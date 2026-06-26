package com.fanet.service;

import com.fanet.mapper.pg.DroneLatestMapper;
import com.fanet.mapper.td.TelemetryMapper;
import com.fanet.model.TelemetryRecord;
import com.fanet.websocket.DroneWebSocketHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TelemetryService {

    private static final DateTimeFormatter TD_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> ALLOWED_WINDOWS = Set.of("10s", "30s", "1m", "5m", "15m", "30m", "1h");

    private final DataSource tdDataSource;
    private final DataSource pgDataSource;
    private final DroneLatestMapper droneLatestMapper;
    private final TelemetryMapper telemetryMapper;
    private final StringRedisTemplate redisTemplate;
    private final DroneWebSocketHandler wsHandler;

    public TelemetryService(@Qualifier("tdDataSource") DataSource tdDataSource,
                            @Qualifier("pgDataSource") DataSource pgDataSource,
                            DroneLatestMapper droneLatestMapper,
                            TelemetryMapper telemetryMapper,
                            StringRedisTemplate redisTemplate,
                            DroneWebSocketHandler wsHandler) {
        this.tdDataSource = tdDataSource;
        this.pgDataSource = pgDataSource;
        this.droneLatestMapper = droneLatestMapper;
        this.telemetryMapper = telemetryMapper;
        this.redisTemplate = redisTemplate;
        this.wsHandler = wsHandler;
    }

    public void ingest(List<TelemetryRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        batchInsertTdengine(records);
        batchUpsertPg(records);

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

    public List<Map<String, Object>> getDroneTelemetrySeries(Integer droneId, String window, int minutes) {
        validateDroneId(droneId);
        validateWindow(window);
        validatePositiveRange(minutes, "minutes");
        return telemetryMapper.aggregateByDrone(droneId, tdTimestamp(minutes), window);
    }

    public List<Map<String, Object>> getAllDronesAggregated(String window, int minutes) {
        validateWindow(window);
        validatePositiveRange(minutes, "minutes");
        return telemetryMapper.aggregateAllDrones(tdTimestamp(minutes), window);
    }

    public List<Map<String, Object>> getBatterySeries(Integer droneId, int minutes) {
        validateDroneId(droneId);
        validatePositiveRange(minutes, "minutes");
        return telemetryMapper.batterySeries(droneId, tdTimestamp(minutes));
    }

    public List<Map<String, Object>> getRssiSeries(Integer droneId, int minutes) {
        validateDroneId(droneId);
        validatePositiveRange(minutes, "minutes");
        return telemetryMapper.rssiSeries(droneId, tdTimestamp(minutes));
    }

    public List<Map<String, Object>> getLatestLinkSnapshot(int secondsBack) {
        validatePositiveRange(secondsBack, "seconds");
        return telemetryMapper.latestLinkSnapshot(tdTimestampSeconds(secondsBack));
    }

    private void validateDroneId(Integer droneId) {
        if (droneId == null || droneId <= 0) {
            throw new IllegalArgumentException("有效的 droneId 必填");
        }
    }

    private void validateWindow(String window) {
        if (window == null || !ALLOWED_WINDOWS.contains(window)) {
            throw new IllegalArgumentException("window 仅支持: " + String.join(", ", new LinkedHashSet<>(ALLOWED_WINDOWS)));
        }
    }

    private void validatePositiveRange(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " 必须大于 0");
        }
    }

    private String tdTimestamp(int minutesAgo) {
        return LocalDateTime.now().minusMinutes(minutesAgo).format(TD_FMT);
    }

    private String tdTimestampSeconds(int secondsAgo) {
        return LocalDateTime.now().minusSeconds(secondsAgo).format(TD_FMT);
    }

    private void batchInsertTdengine(List<TelemetryRecord> records) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        for (int i = 0; i < records.size(); i++) {
            TelemetryRecord r = records.get(i);
            long ts = r.getTs() != null ? r.getTs().toEpochMilli() : System.currentTimeMillis();
            int mid = r.getModelId() != null ? r.getModelId() : 0;
            if (i > 0) {
                sql.append(' ');
            }
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
            throw new IllegalStateException("TDengine 批量写入失败: " + e.getMessage(), e);
        }
    }

    private void batchUpsertPg(List<TelemetryRecord> records) {
        String sql = "INSERT INTO drone_latest (drone_id, ts, lat, lon, alt, battery_pct, rssi) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (drone_id) DO UPDATE SET ts=EXCLUDED.ts, lat=EXCLUDED.lat, "
                + "lon=EXCLUDED.lon, alt=EXCLUDED.alt, battery_pct=EXCLUDED.battery_pct, rssi=EXCLUDED.rssi";
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
            throw new IllegalStateException("PostgreSQL 批量 UPSERT 失败: " + e.getMessage(), e);
        }
    }
}
