package com.fanet.service;

import com.fanet.mapper.pg.DroneLatestMapper;
import com.fanet.mapper.td.TelemetryMapper;
import com.fanet.model.TelemetryRecord;
import com.fanet.websocket.DroneWebSocketHandler;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TelemetryService {

    private final TelemetryMapper telemetryMapper;
    private final DroneLatestMapper droneLatestMapper;
    private final StringRedisTemplate redisTemplate;
    private final DroneWebSocketHandler wsHandler;

    public TelemetryService(TelemetryMapper telemetryMapper,
                            DroneLatestMapper droneLatestMapper,
                            StringRedisTemplate redisTemplate,
                            DroneWebSocketHandler wsHandler) {
        this.telemetryMapper = telemetryMapper;
        this.droneLatestMapper = droneLatestMapper;
        this.redisTemplate = redisTemplate;
        this.wsHandler = wsHandler;
    }

    public void ingest(List<TelemetryRecord> records) {
        if (records == null || records.isEmpty()) return;

        // 1) 逐条写 TDengine（自动建子表）
        for (TelemetryRecord r : records) {
            long ts = r.getTs() != null ? r.getTs().toEpochMilli() : System.currentTimeMillis();
            int modelId = r.getModelId() != null ? r.getModelId() : 0;
            telemetryMapper.insertOne(r.getDroneId(), modelId, ts,
                    r.getLat(), r.getLon(), r.getAlt(), r.getBatteryPct(), r.getRssi());
        }

        // 2) 回写 PG drone_latest + 更新 Redis
        for (TelemetryRecord r : records) {
            Instant ts = r.getTs() != null ? r.getTs() : Instant.now();
            droneLatestMapper.upsert(r.getDroneId(), ts,
                    r.getLat(), r.getLon(), r.getAlt(),
                    r.getBatteryPct(), r.getRssi());

            // 3) Redis: 缓存最新状态 hash
            String key = "drone:latest:" + r.getDroneId();
            Map<String, String> hash = Map.of(
                    "lat", String.valueOf(r.getLat()),
                    "lon", String.valueOf(r.getLon()),
                    "alt", String.valueOf(r.getAlt()),
                    "battery", String.valueOf(r.getBatteryPct()),
                    "rssi", String.valueOf(r.getRssi()),
                    "ts", ts.toString());
            redisTemplate.opsForHash().putAll(key, hash);
        }

        // 4) 推送 WebSocket 广播（通知前端刷新）
        for (TelemetryRecord r : records) {
            wsHandler.broadcast("drone_update", Map.of(
                    "droneId", r.getDroneId(),
                    "lat", r.getLat() != null ? r.getLat() : 0,
                    "lon", r.getLon() != null ? r.getLon() : 0,
                    "alt", r.getAlt() != null ? r.getAlt() : 0,
                    "batteryPct", r.getBatteryPct() != null ? r.getBatteryPct() : 0,
                    "rssi", r.getRssi() != null ? r.getRssi() : 0
            ));
        }
    }
}
