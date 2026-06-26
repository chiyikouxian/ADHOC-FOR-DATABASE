package com.fanet.controller;

import com.fanet.model.TelemetryRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bench")
public class BenchController {

    private final DataSource pgDataSource;
    private final DataSource tdDataSource;

    public BenchController(@Qualifier("pgDataSource") DataSource pgDataSource,
                           @Qualifier("tdDataSource") DataSource tdDataSource) {
        this.pgDataSource = pgDataSource;
        this.tdDataSource = tdDataSource;
    }

    @PostMapping("/pg-insert")
    public ResponseEntity<?> pgInsert(@RequestBody List<TelemetryRecord> records) {
        if (records == null || records.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "empty telemetry payload"));
        }

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
            return ResponseEntity.ok(Map.of("target", "pg", "ingested", records.size()));
        } catch (SQLException e) {
            throw new IllegalStateException("PostgreSQL benchmark insert failed: " + e.getMessage(), e);
        }
    }

    @PostMapping("/td-insert")
    public ResponseEntity<?> tdInsert(@RequestBody List<TelemetryRecord> records) {
        if (records == null || records.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "empty telemetry payload"));
        }

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
            return ResponseEntity.ok(Map.of("target", "tdengine", "ingested", records.size()));
        } catch (SQLException e) {
            throw new IllegalStateException("TDengine benchmark insert failed: " + e.getMessage(), e);
        }
    }
}
