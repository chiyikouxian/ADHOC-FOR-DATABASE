package com.fanet.service;

import com.fanet.mapper.pg.DroneLatestMapper;
import com.fanet.model.TelemetryRecord;
import com.fanet.websocket.DroneWebSocketHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TelemetryService {

    private final DataSource tdDataSource;
    private final DataSource pgDataSource;
    private final DroneLatestMapper droneLatestMapper;
    private final StringRedisTemplate redisTemplate;
    private final DroneWebSocketHandler wsHandler;

    public TelemetryService(@Qualifier("tdDataSource") DataSource tdDataSource,
                            @Qualifier("pgDataSource") DataSource pgDataSource,
                            DroneLatestMapper droneLatestMapper,
                            StringRedisTemplate redisTemplate,
                            DroneWebSocketHandler wsHandler) {
        this.tdDataSource = tdDataSource;
        this.pgDataSource = pgDataSource;
        this.droneLatestMapper = droneLatestMapper;
        this.redisTemplate = redisTemplate;
        this.wsHandler = wsHandler;
    }

    public void ingest(List<TelemetryRecord> records) {
        if (records == null || records.isEmpty()) return;

        // 1) 批量写 TDengine（单条 SQL，多个 USING-VALUES 子句）
        batchInsertTdengine(records);

        // 2) 批量 UPSERT PG drone_latest
        batchUpsertPg(records);

        // 3) 批量写 Redis + WebSocket 推送
        for (TelemetryRecord r : records) {
            String key = "drone:latest:" + r.getDroneId();
            Instant ts = r.getTs() != null ? r.getTs() : Instant.now();
            Map<String, String> hash = Map.of(
                    "lat", String.valueOf(r.getLat() != null ? r.getLat() : 0),
                    "lon", String.valueOf(r.getLon() != null ? r.getLon() : 0),
                    "alt", String.valueOf(r.getAlt() != null ? r.getAlt() : 0),
                    "battery", String.valueOf(r.getBatteryPct() != null ? r.getBatteryPct() : 0),
                    "rssi", String.valueOf(r.getRssi() != null ? r.getRssi() : 0),
                    "ts", ts.toString());
            redisTemplate.opsForHash().putAll(key, hash);

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

    private void batchInsertTdengine(List<TelemetryRecord> records) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        for (int i = 0; i < records.size(); i++) {
            TelemetryRecord r = records.get(i);
            long ts = r.getTs() != null ? r.getTs().toEpochMilli() : System.currentTimeMillis();
            int mid = r.getModelId() != null ? r.getModelId() : 0;
            if (i > 0) sql.append(' ');
            sql.append("d_telemetry_").append(r.getDroneId())
               .append(" USING telemetry TAGS(").append(r.getDroneId()).append(',').append(mid).append(')')
               .append(" VALUES(").append(ts).append(',')
               .append(r.getLat()).append(',').append(r.getLon()).append(',')
               .append(r.getAlt()).append(',').append(r.getBatteryPct()).append(',')
               .append(r.getRssi()).append(')');
        }
        try (Connection conn = tdDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql.toString());
        } catch (SQLException e) {
            System.err.println("[TDengine] 批量写入失败: " + e.getMessage());
        }
    }

    private void batchUpsertPg(List<TelemetryRecord> records) {
        String sql = "INSERT INTO drone_latest (drone_id, ts, lat, lon, alt, battery_pct, rssi) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT (drone_id) DO UPDATE SET ts=EXCLUDED.ts, lat=EXCLUDED.lat, " +
                     "lon=EXCLUDED.lon, alt=EXCLUDED.alt, battery_pct=EXCLUDED.battery_pct, rssi=EXCLUDED.rssi";
        try (Connection conn = pgDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (TelemetryRecord r : records) {
                Instant ts = r.getTs() != null ? r.getTs() : Instant.now();
                ps.setInt(1, r.getDroneId());
                ps.setTimestamp(2, Timestamp.from(ts));
                ps.setDouble(3, r.getLat() != null ? r.getLat() : 0);
                ps.setDouble(4, r.getLon() != null ? r.getLon() : 0);
                ps.setDouble(5, r.getAlt() != null ? r.getAlt() : 0);
                ps.setDouble(6, r.getBatteryPct() != null ? r.getBatteryPct() : 0);
                ps.setInt(7, r.getRssi() != null ? r.getRssi() : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            System.err.println("[PG] 批量 UPSERT 失败: " + e.getMessage());
        }
    }
}
