package com.fanet.service;

import com.fanet.mapper.pg.DroneLatestMapper;
import com.fanet.mapper.td.TelemetryMapper;
import com.fanet.model.TelemetryRecord;
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

    public TelemetryService(TelemetryMapper telemetryMapper,
                            DroneLatestMapper droneLatestMapper,
                            StringRedisTemplate redisTemplate) {
        this.telemetryMapper = telemetryMapper;
        this.droneLatestMapper = droneLatestMapper;
        this.redisTemplate = redisTemplate;
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
    }
}
