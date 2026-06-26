package com.fanet.service;

import com.fanet.mapper.td.TelemetryMapper;
import com.fanet.websocket.DroneWebSocketHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TopologySyncService {
    private static final DateTimeFormatter TD_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DataSource pgDataSource;
    private final TelemetryMapper telemetryMapper;
    private final StringRedisTemplate redisTemplate;
    private final DroneWebSocketHandler wsHandler;

    public TopologySyncService(@Qualifier("pgDataSource") DataSource pgDataSource,
                               TelemetryMapper telemetryMapper,
                               StringRedisTemplate redisTemplate,
                               DroneWebSocketHandler wsHandler) {
        this.pgDataSource = pgDataSource;
        this.telemetryMapper = telemetryMapper;
        this.redisTemplate = redisTemplate;
        this.wsHandler = wsHandler;
    }

    @Scheduled(fixedDelay = 30000)
    public void syncLinkSnapshot() {
        List<Map<String, Object>> links = telemetryMapper.latestLinkSnapshot(tdTimestampSeconds(60));
        if (links == null) {
            links = List.of();
        }

        Map<String, Map<String, Object>> existingLinks = loadExistingSnapshot();
        Set<String> incomingKeys = new HashSet<>();
        Timestamp now = Timestamp.from(Instant.now());
        boolean topologyChanged = false;

        String upsertSql = "INSERT INTO network_links_snapshot (src_drone_id, dst_drone_id, link_quality, is_active, snapshot_at) "
                + "VALUES (?, ?, ?, ?, ?) "
                + "ON CONFLICT (src_drone_id, dst_drone_id) "
                + "DO UPDATE SET link_quality = EXCLUDED.link_quality, is_active = EXCLUDED.is_active, "
                + "snapshot_at = EXCLUDED.snapshot_at";

        try (Connection conn = pgDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            for (Map<String, Object> link : links) {
                int src = getInt(link, "src_drone_id");
                int dst = getInt(link, "dst_drone_id");
                int quality = getInt(link, "link_quality");
                boolean active = getBoolean(link, "is_active");
                String linkKey = linkKey(src, dst);
                incomingKeys.add(linkKey);

                Map<String, Object> existing = existingLinks.get(linkKey);
                if (existing == null
                        || getInt(existing, "link_quality") != quality
                        || getBoolean(existing, "is_active") != active) {
                    topologyChanged = true;
                }

                ps.setInt(1, src);
                ps.setInt(2, dst);
                ps.setInt(3, quality);
                ps.setBoolean(4, active);
                ps.setTimestamp(5, now);
                ps.addBatch();

                if (active) {
                    redisTemplate.opsForZSet().add("link:quality:ranking", linkKey, quality);
                    redisTemplate.opsForHash().put("link:active", linkKey, String.valueOf(quality));
                } else {
                    redisTemplate.opsForZSet().remove("link:quality:ranking", linkKey);
                    redisTemplate.opsForHash().delete("link:active", linkKey);
                }
            }
            ps.executeBatch();
        } catch (SQLException e) {
            System.err.println("[TopologySync] PG UPSERT 失败: " + e.getMessage());
        }

        topologyChanged |= deactivateMissingLinks(existingLinks, incomingKeys, now);

        if (topologyChanged) {
            wsHandler.broadcast("topology_update", buildTopologyData(loadActiveLinks()));
        }
    }

    private Map<String, Object> buildTopologyData(List<Map<String, Object>> links) {
        Set<Integer> nodeIds = new LinkedHashSet<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        for (Map<String, Object> link : links) {
            int src = getInt(link, "src_drone_id");
            int dst = getInt(link, "dst_drone_id");
            int quality = getInt(link, "link_quality");
            boolean active = getBoolean(link, "is_active");

            nodeIds.add(src);
            nodeIds.add(dst);

            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("source", src);
            edge.put("target", dst);
            edge.put("quality", quality);
            edge.put("active", active);
            edges.add(edge);
        }

        nodeIds.add(0);

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Integer id : nodeIds) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("droneId", id);
            node.put("name", id == 0 ? "地面站" : "无人机" + id);
            node.put("isGround", id == 0);
            nodes.add(node);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    private Map<String, Map<String, Object>> loadExistingSnapshot() {
        Map<String, Map<String, Object>> existing = new HashMap<>();
        String sql = "SELECT src_drone_id, dst_drone_id, link_quality, is_active FROM network_links_snapshot";
        try (Connection conn = pgDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("link_quality", rs.getInt("link_quality"));
                row.put("is_active", rs.getBoolean("is_active"));
                existing.put(linkKey(rs.getInt("src_drone_id"), rs.getInt("dst_drone_id")), row);
            }
        } catch (SQLException e) {
            System.err.println("[TopologySync] 读取链路快照失败: " + e.getMessage());
        }
        return existing;
    }

    private boolean deactivateMissingLinks(Map<String, Map<String, Object>> existingLinks,
                                           Set<String> incomingKeys,
                                           Timestamp now) {
        List<String> staleKeys = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : existingLinks.entrySet()) {
            if (!incomingKeys.contains(entry.getKey()) && getBoolean(entry.getValue(), "is_active")) {
                staleKeys.add(entry.getKey());
            }
        }

        if (staleKeys.isEmpty()) {
            return false;
        }

        String sql = "UPDATE network_links_snapshot SET is_active = FALSE, snapshot_at = ? "
                + "WHERE src_drone_id = ? AND dst_drone_id = ?";
        try (Connection conn = pgDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String staleKey : staleKeys) {
                String[] parts = staleKey.split("->");
                ps.setTimestamp(1, now);
                ps.setInt(2, Integer.parseInt(parts[0]));
                ps.setInt(3, Integer.parseInt(parts[1]));
                ps.addBatch();
                redisTemplate.opsForHash().delete("link:active", staleKey);
                redisTemplate.opsForZSet().remove("link:quality:ranking", staleKey);
            }
            ps.executeBatch();
            return true;
        } catch (SQLException e) {
            System.err.println("[TopologySync] 失活链路更新失败: " + e.getMessage());
            return false;
        }
    }

    private List<Map<String, Object>> loadActiveLinks() {
        List<Map<String, Object>> links = new ArrayList<>();
        String sql = "SELECT src_drone_id, dst_drone_id, link_quality, is_active "
                + "FROM network_links_snapshot WHERE is_active = TRUE ORDER BY link_quality DESC";
        try (Connection conn = pgDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("src_drone_id", rs.getInt("src_drone_id"));
                row.put("dst_drone_id", rs.getInt("dst_drone_id"));
                row.put("link_quality", rs.getInt("link_quality"));
                row.put("is_active", rs.getBoolean("is_active"));
                links.add(row);
            }
        } catch (SQLException e) {
            System.err.println("[TopologySync] 加载活跃链路失败: " + e.getMessage());
        }
        return links;
    }

    private String linkKey(int src, int dst) {
        return src + "->" + dst;
    }

    private String tdTimestampSeconds(int secondsAgo) {
        return LocalDateTime.now().minusSeconds(secondsAgo).format(TD_FMT);
    }

    private int getInt(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v instanceof Boolean) {
            return ((Boolean) v) ? 1 : 0;
        }
        return 0;
    }

    private boolean getBoolean(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue() != 0;
        }
        return false;
    }
}
