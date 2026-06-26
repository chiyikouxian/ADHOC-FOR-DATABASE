package com.fanet.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@RestController
@RequestMapping("/api/alerts")
public class AlertsController {

    private final DataSource pgDataSource;

    public AlertsController(@Qualifier("pgDataSource") DataSource pgDataSource) {
        this.pgDataSource = pgDataSource;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        List<Map<String, Object>> alerts = new ArrayList<>();
        try (Connection conn = pgDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT alert_id, drone_id, alert_type, severity, detail, created_at, resolved " +
                "FROM alerts ORDER BY resolved ASC, created_at DESC LIMIT 50");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("alert_id", rs.getLong("alert_id"));
                a.put("drone_id", rs.getInt("drone_id"));
                a.put("alert_type", rs.getString("alert_type"));
                a.put("severity", rs.getString("severity"));
                a.put("detail", rs.getString("detail"));
                a.put("created_at", rs.getTimestamp("created_at").toInstant().toString());
                a.put("resolved", rs.getBoolean("resolved"));
                alerts.add(a);
            }
        } catch (SQLException e) {
            return ResponseEntity.status(500).body(List.of(Map.of("error", e.getMessage())));
        }
        return ResponseEntity.ok(alerts);
    }

    @PostMapping("/{alertId}/resolve")
    public ResponseEntity<?> resolve(@PathVariable Long alertId) {
        String sql = "UPDATE alerts SET resolved = TRUE WHERE alert_id = ? RETURNING alert_id, resolved";
        try (Connection conn = pgDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, alertId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return ResponseEntity.status(404).body(Map.of("error", "告警不存在"));
                }
                return ResponseEntity.ok(Map.of(
                        "alert_id", rs.getLong("alert_id"),
                        "resolved", rs.getBoolean("resolved")
                ));
            }
        } catch (SQLException e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
