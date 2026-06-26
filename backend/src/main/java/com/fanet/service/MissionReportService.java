package com.fanet.service;

import com.fanet.mapper.td.TelemetryMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MissionReportService {

    private static final DateTimeFormatter TD_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DataSource pgDataSource;
    private final TelemetryMapper telemetryMapper;
    private final Nl2SqlService nl2SqlService;

    public MissionReportService(@Qualifier("pgDataSource") DataSource pgDataSource,
                                TelemetryMapper telemetryMapper,
                                Nl2SqlService nl2SqlService) {
        this.pgDataSource = pgDataSource;
        this.telemetryMapper = telemetryMapper;
        this.nl2SqlService = nl2SqlService;
    }

    public Map<String, Object> generateReport(long missionId, boolean useAI) {
        Map<String, Object> report = new LinkedHashMap<>();

        Map<String, Object> mission = queryMission(missionId);
        if (mission == null) {
            report.put("error", "任务 #" + missionId + " 不存在");
            return report;
        }
        report.put("mission", mission);

        List<Map<String, Object>> assignments = queryAssignments(missionId);
        report.put("assignments", assignments);

        TimeWindow timeWindow = resolveMissionWindow(mission, assignments);
        report.put("timeWindow", Map.of(
                "start", timeWindow.start().toString(),
                "end", timeWindow.end().toString()
        ));

        List<Map<String, Object>> alerts = queryMissionAlerts(missionId, timeWindow);
        report.put("alerts", alerts);

        List<Map<String, Object>> droneSummaries = new ArrayList<>();
        for (Map<String, Object> assign : assignments) {
            int droneId = ((Number) assign.get("drone_id")).intValue();
            Map<String, Object> summary = buildDroneSummary(droneId, timeWindow);
            summary.put("droneId", droneId);
            summary.put("serialNo", assign.get("serial_no"));
            droneSummaries.add(summary);
        }
        report.put("droneSummaries", droneSummaries);

        int totalDrones = assignments.size();
        long completedCount = assignments.stream().filter(a -> "done".equals(a.get("status"))).count();
        long alertCount = alerts.size();
        long criticalAlerts = alerts.stream().filter(a -> "critical".equals(a.get("severity"))).count();

        report.put("stats", Map.of(
                "totalDrones", totalDrones,
                "completed", completedCount,
                "totalAlerts", alertCount,
                "criticalAlerts", criticalAlerts
        ));

        if (useAI && nl2SqlService != null) {
            String aiPrompt = buildReportPrompt(
                    mission, assignments, alerts, droneSummaries,
                    totalDrones, completedCount, alertCount, criticalAlerts
            );
            try {
                String aiSummary = nl2SqlService.askRaw(aiPrompt);
                if (aiSummary != null && !aiSummary.isBlank()) {
                    report.put("aiSummary", aiSummary.trim());
                }
            } catch (Exception e) {
                report.put("aiError", "AI 总结生成失败: " + e.getMessage());
            }
        }

        report.put("generatedAt", Instant.now().toString());
        return report;
    }

    private Map<String, Object> queryMission(long missionId) {
        String sql = "SELECT m.mission_id, m.title, m.status, m.planned_start, m.created_at, "
                + "u.username AS creator_name "
                + "FROM missions m JOIN users u ON u.user_id = m.creator_id "
                + "WHERE m.mission_id = ?";
        try (Connection conn = pgDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, missionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("missionId", rs.getLong("mission_id"));
                    row.put("title", rs.getString("title"));
                    row.put("status", rs.getString("status"));
                    row.put("plannedStart", rs.getTimestamp("planned_start"));
                    row.put("createdAt", rs.getTimestamp("created_at"));
                    row.put("creatorName", rs.getString("creator_name"));
                    return row;
                }
            }
        } catch (SQLException e) {
            System.err.println("[Report] 任务查询失败: " + e.getMessage());
        }
        return null;
    }

    private List<Map<String, Object>> queryAssignments(long missionId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT ma.drone_id, d.serial_no, ma.status, ma.assigned_at, ma.completed_at "
                + "FROM mission_assignments ma JOIN drones d ON d.drone_id = ma.drone_id "
                + "WHERE ma.mission_id = ? ORDER BY ma.assigned_at";
        try (Connection conn = pgDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, missionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("drone_id", rs.getInt("drone_id"));
                    row.put("serial_no", rs.getString("serial_no"));
                    row.put("status", rs.getString("status"));
                    row.put("assigned_at", rs.getTimestamp("assigned_at"));
                    row.put("completed_at", rs.getTimestamp("completed_at"));
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("[Report] 任务分配查询失败: " + e.getMessage());
        }
        return list;
    }

    private List<Map<String, Object>> queryMissionAlerts(long missionId, TimeWindow timeWindow) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT a.alert_id, a.drone_id, a.alert_type, a.severity, a.detail, a.created_at, a.resolved "
                + "FROM alerts a "
                + "WHERE a.drone_id IN (SELECT drone_id FROM mission_assignments WHERE mission_id = ?) "
                + "AND a.created_at BETWEEN ? AND ? "
                + "ORDER BY a.created_at DESC LIMIT 50";
        try (Connection conn = pgDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, missionId);
            ps.setTimestamp(2, Timestamp.from(timeWindow.start()));
            ps.setTimestamp(3, Timestamp.from(timeWindow.end()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("alertId", rs.getLong("alert_id"));
                    row.put("droneId", rs.getInt("drone_id"));
                    row.put("alertType", rs.getString("alert_type"));
                    row.put("severity", rs.getString("severity"));
                    row.put("detail", rs.getString("detail"));
                    row.put("createdAt", rs.getTimestamp("created_at"));
                    row.put("resolved", rs.getBoolean("resolved"));
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("[Report] 告警查询失败: " + e.getMessage());
        }
        return list;
    }

    private Map<String, Object> buildDroneSummary(int droneId, TimeWindow timeWindow) {
        Map<String, Object> summary = new LinkedHashMap<>();

        List<Map<String, Object>> batterySeries = filterSeriesByWindow(
                telemetryMapper.batterySeries(droneId, tdTimestamp(timeWindow.start())),
                timeWindow
        );
        if (!batterySeries.isEmpty()) {
            double sum = 0;
            double min = 100;
            double max = 0;
            for (Map<String, Object> pt : batterySeries) {
                double value = toDouble(pt.get("battery_pct"));
                sum += value;
                if (value < min) {
                    min = value;
                }
                if (value > max) {
                    max = value;
                }
            }
            int count = batterySeries.size();
            summary.put("avgBattery", Math.round(sum / count * 10.0) / 10.0);
            summary.put("minBattery", Math.round(min * 10.0) / 10.0);
            summary.put("maxBattery", Math.round(max * 10.0) / 10.0);
            summary.put("dataPoints", count);
        } else {
            summary.put("avgBattery", "N/A");
            summary.put("dataPoints", 0);
        }

        List<Map<String, Object>> rssiSeries = filterSeriesByWindow(
                telemetryMapper.rssiSeries(droneId, tdTimestamp(timeWindow.start())),
                timeWindow
        );
        if (!rssiSeries.isEmpty()) {
            double sum = 0;
            double best = Double.NEGATIVE_INFINITY;
            double worst = Double.POSITIVE_INFINITY;
            for (Map<String, Object> pt : rssiSeries) {
                double value = toDouble(pt.get("rssi"));
                sum += value;
                if (value > best) {
                    best = value;
                }
                if (value < worst) {
                    worst = value;
                }
            }
            int count = rssiSeries.size();
            summary.put("avgRssi", Math.round(sum / count * 10.0) / 10.0);
            summary.put("bestRssi", Math.round(best * 10.0) / 10.0);
            summary.put("worstRssi", Math.round(worst * 10.0) / 10.0);
        } else {
            summary.put("avgRssi", "N/A");
        }

        return summary;
    }

    private TimeWindow resolveMissionWindow(Map<String, Object> mission, List<Map<String, Object>> assignments) {
        Instant start = timestampToInstant(mission.get("plannedStart"));
        if (start == null) {
            start = timestampToInstant(mission.get("createdAt"));
        }
        Instant end = null;

        for (Map<String, Object> assignment : assignments) {
            Instant assignedAt = timestampToInstant(assignment.get("assigned_at"));
            Instant completedAt = timestampToInstant(assignment.get("completed_at"));
            if (assignedAt != null && (start == null || assignedAt.isBefore(start))) {
                start = assignedAt;
            }
            if (completedAt != null && (end == null || completedAt.isAfter(end))) {
                end = completedAt;
            }
        }

        if (start == null) {
            start = Instant.now().minus(Duration.ofHours(2));
        }
        if (end == null || end.isBefore(start)) {
            end = Instant.now();
        }
        return new TimeWindow(start, end);
    }

    private List<Map<String, Object>> filterSeriesByWindow(List<Map<String, Object>> series, TimeWindow timeWindow) {
        if (series == null || series.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> point : series) {
            Instant ts = timestampToInstant(point.get("ts"));
            if (ts == null) {
                continue;
            }
            if (!ts.isBefore(timeWindow.start()) && !ts.isAfter(timeWindow.end())) {
                filtered.add(point);
            }
        }
        return filtered;
    }

    private String buildReportPrompt(Map<String, Object> mission,
                                     List<Map<String, Object>> assignments,
                                     List<Map<String, Object>> alerts,
                                     List<Map<String, Object>> summaries,
                                     int totalDrones,
                                     long completed,
                                     long totalAlerts,
                                     long critical) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是无人机任务分析专家。请基于以下数据生成一段简洁的飞行复盘总结（200字以内）。\n\n");
        sb.append("任务: ").append(mission.get("title"))
                .append("（状态: ").append(mission.get("status")).append("）\n");
        sb.append("参与无人机: ").append(totalDrones).append(" 架，完成: ").append(completed).append(" 架\n");
        sb.append("告警: ").append(totalAlerts).append(" 条，其中严重: ").append(critical).append(" 条\n\n");

        for (Map<String, Object> summary : summaries) {
            sb.append("无人机 ").append(summary.get("serialNo"))
                    .append(": 均电量 ").append(summary.get("avgBattery")).append("%")
                    .append(", 均信号 ").append(summary.get("avgRssi")).append(" dBm")
                    .append(", 数据点 ").append(summary.get("dataPoints")).append("\n");
        }

        if (!alerts.isEmpty()) {
            sb.append("\n关键告警:\n");
            for (Map<String, Object> alert : alerts) {
                if ("critical".equals(alert.get("severity")) || !Boolean.TRUE.equals(alert.get("resolved"))) {
                    sb.append("- [").append(alert.get("severity")).append("] Drone-")
                            .append(alert.get("droneId")).append(": ").append(alert.get("detail")).append("\n");
                }
            }
        }

        return sb.toString();
    }

    private String tdTimestamp(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(TD_FMT);
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0;
    }

    private Instant timestampToInstant(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        return null;
    }

    private record TimeWindow(Instant start, Instant end) {
    }
}
