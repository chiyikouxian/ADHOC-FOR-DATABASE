package com.fanet.controller;

import com.fanet.service.TopologySyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * 拓扑分析控制器
 * GET /api/topology — 当前拓扑数据（节点 + 边）
 * GET /api/topology/links — 链路质量排行
 */
@RestController
@RequestMapping("/api/topology")
public class TopologyController {

    private final DataSource pgDataSource;
    private final TopologySyncService topologySyncService;

    public TopologyController(@org.springframework.beans.factory.annotation.Qualifier("pgDataSource") DataSource pgDataSource,
                              TopologySyncService topologySyncService) {
        this.pgDataSource = pgDataSource;
        this.topologySyncService = topologySyncService;
    }

    /**
     * 获取当前网络拓扑（节点 + 活跃边）
     * 从 PG network_links_snapshot 读最新快照 + drones 表获取节点信息
     */
    @GetMapping
    public ResponseEntity<?> getTopology() {
        try (Connection conn = pgDataSource.getConnection()) {
            // 所有无人机节点（含地面站）
            Map<String, Object> result = new LinkedHashMap<>();

            // 节点列表
            List<Map<String, Object>> nodes = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT d.drone_id, d.serial_no, d.status, dl.battery_pct, dl.lat, dl.lon, dl.alt "
                    + "FROM drones d LEFT JOIN drone_latest dl ON dl.drone_id = d.drone_id "
                    + "WHERE d.drone_id = 0 OR d.status <> 'offline' "
                    + "ORDER BY d.drone_id")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> node = new LinkedHashMap<>();
                        node.put("droneId", rs.getInt("drone_id"));
                        node.put("name", rs.getInt("drone_id") == 0 ? "地面站" : rs.getString("serial_no"));
                        node.put("status", rs.getString("status"));
                        node.put("batteryPct", rs.getDouble("battery_pct"));
                        node.put("lat", rs.getDouble("lat"));
                        node.put("lon", rs.getDouble("lon"));
                        node.put("alt", rs.getDouble("alt"));
                        node.put("isGround", rs.getInt("drone_id") == 0);
                        nodes.add(node);
                    }
                }
            }

            // 边列表（活跃链路）
            List<Map<String, Object>> edges = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT src_drone_id, dst_drone_id, link_quality, is_active "
                    + "FROM network_links_snapshot WHERE is_active = TRUE ORDER BY link_quality DESC")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> edge = new LinkedHashMap<>();
                        edge.put("source", rs.getInt("src_drone_id"));
                        edge.put("target", rs.getInt("dst_drone_id"));
                        edge.put("quality", rs.getInt("link_quality"));
                        edge.put("active", rs.getBoolean("is_active"));
                        edges.add(edge);
                    }
                }
            }

            result.put("nodes", nodes);
            result.put("edges", edges);
            return ResponseEntity.ok(result);
        } catch (SQLException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "拓扑查询失败: " + e.getMessage()));
        }
    }

    /**
     * 获取链路质量排行榜（从 Redis Sorted Set 或 PG 快照表）
     */
    @GetMapping("/links")
    public ResponseEntity<?> getLinkRanking() {
        try (Connection conn = pgDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT src_drone_id, dst_drone_id, link_quality, is_active, snapshot_at "
                     + "FROM network_links_snapshot ORDER BY link_quality DESC")) {
            List<Map<String, Object>> links = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> link = new LinkedHashMap<>();
                    link.put("srcDroneId", rs.getInt("src_drone_id"));
                    link.put("dstDroneId", rs.getInt("dst_drone_id"));
                    link.put("linkQuality", rs.getInt("link_quality"));
                    link.put("isActive", rs.getBoolean("is_active"));
                    link.put("snapshotAt", rs.getTimestamp("snapshot_at").toString());
                    links.add(link);
                }
            }
            return ResponseEntity.ok(links);
        } catch (SQLException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "链路查询失败: " + e.getMessage()));
        }
    }
}
