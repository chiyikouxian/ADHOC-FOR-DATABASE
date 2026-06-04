package com.fanet.service;

import com.fanet.mapper.pg.DroneLatestMapper;
import com.fanet.websocket.DroneWebSocketHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

@Service
public class AnomalyDetector {

    private final DataSource pgDataSource;
    private final DroneWebSocketHandler wsHandler;

    public AnomalyDetector(DataSource pgDataSource, DroneWebSocketHandler wsHandler) {
        this.pgDataSource = pgDataSource;
        this.wsHandler = wsHandler;
    }

    @Scheduled(fixedDelay = 10000)
    public void check() {
        List<Map<String, Object>> alerts = new ArrayList<>();
        try (Connection conn = pgDataSource.getConnection()) {
            // 1) 电池骤降告警
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO alerts (drone_id, alert_type, severity, detail, created_at) " +
                "SELECT dl.drone_id, 'battery_drop', " +
                "  CASE WHEN dl.battery_pct < 10 THEN 'critical' WHEN dl.battery_pct < 20 THEN 'warning' END, " +
                "  '电量降至 ' || ROUND(dl.battery_pct::numeric, 1) || '%，低于安全阈值', NOW() " +
                "FROM drone_latest dl JOIN drones d ON d.drone_id = dl.drone_id " +
                "WHERE dl.battery_pct < 20 AND d.status != 'offline' " +
                "ON CONFLICT (drone_id, alert_type) WHERE resolved = FALSE DO NOTHING " +
                "RETURNING alert_id, drone_id, alert_type, severity, detail"
            )) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> a = new HashMap<>();
                        a.put("alert_id", rs.getLong("alert_id"));
                        a.put("drone_id", rs.getInt("drone_id"));
                        a.put("type", rs.getString("alert_type"));
                        a.put("severity", rs.getString("severity"));
                        a.put("detail", rs.getString("detail"));
                        alerts.add(a);
                    }
                }
            }

            // 2) 信号劣化告警
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO alerts (drone_id, alert_type, severity, detail, created_at) " +
                "SELECT dl.drone_id, 'rssi_drop', " +
                "  CASE WHEN dl.rssi < -95 THEN 'critical' WHEN dl.rssi < -85 THEN 'warning' END, " +
                "  '信号劣化，RSSI = ' || dl.rssi || ' dBm', NOW() " +
                "FROM drone_latest dl JOIN drones d ON d.drone_id = dl.drone_id " +
                "WHERE dl.rssi < -85 AND d.status != 'offline' " +
                "ON CONFLICT (drone_id, alert_type) WHERE resolved = FALSE DO NOTHING " +
                "RETURNING alert_id, drone_id, alert_type, severity, detail"
            )) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> a = new HashMap<>();
                        a.put("alert_id", rs.getLong("alert_id"));
                        a.put("drone_id", rs.getInt("drone_id"));
                        a.put("type", rs.getString("alert_type"));
                        a.put("severity", rs.getString("severity"));
                        a.put("detail", rs.getString("detail"));
                        alerts.add(a);
                    }
                }
            }

            // 3) 自动清除已恢复的告警（电量回升到 25% 以上或信号恢复到 -80 以上）
            try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE alerts SET resolved = TRUE " +
                "WHERE resolved = FALSE AND alert_type = 'battery_drop' " +
                "AND drone_id IN (SELECT drone_id FROM drone_latest WHERE battery_pct >= 25)"
            )) { ps.executeUpdate(); }
            try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE alerts SET resolved = TRUE " +
                "WHERE resolved = FALSE AND alert_type = 'rssi_drop' " +
                "AND drone_id IN (SELECT drone_id FROM drone_latest WHERE rssi >= -80)"
            )) { ps.executeUpdate(); }

        } catch (SQLException ignored) { /* 静默，下次重试 */ }

        // 新告警推送 WebSocket
        for (Map<String, Object> alert : alerts) {
            wsHandler.broadcast("new_alert", alert);
        }
    }
}
