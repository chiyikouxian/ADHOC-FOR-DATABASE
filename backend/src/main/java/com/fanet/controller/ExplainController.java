package com.fanet.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@RestController
@RequestMapping("/api/explain")
public class ExplainController {

    private final DataSource pgDataSource;

    public ExplainController(@Qualifier("pgDataSource") DataSource pgDataSource) {
        this.pgDataSource = pgDataSource;
    }

    @GetMapping("/schedule")
    public List<Map<String, Object>> explainSchedule() {
        return runExplain(
            "EXPLAIN ANALYZE " +
            "SELECT d.drone_id, d.status, dl.battery_pct AS battery " +
            "FROM drones d JOIN drone_latest dl ON dl.drone_id = d.drone_id " +
            "WHERE d.drone_id = 1 AND d.status = 'idle' FOR UPDATE OF d"
        );
    }

    @GetMapping("/recursive")
    public List<Map<String, Object>> explainRecursive() {
        return runExplain(
            "EXPLAIN ANALYZE " +
            "WITH RECURSIVE route AS ( " +
            "  SELECT src_drone_id, dst_drone_id, 1 AS hops " +
            "  FROM network_links_snapshot WHERE src_drone_id = 4 AND is_active = TRUE " +
            "  UNION ALL SELECT r.src_drone_id, nl.dst_drone_id, r.hops + 1 " +
            "  FROM network_links_snapshot nl JOIN route r ON nl.src_drone_id = r.dst_drone_id " +
            "  WHERE nl.is_active = TRUE AND r.hops < 10) " +
            "SELECT * FROM route WHERE dst_drone_id = 0 ORDER BY hops"
        );
    }

    @GetMapping("/ranking")
    public List<Map<String, Object>> explainRanking() {
        return runExplain(
            "EXPLAIN ANALYZE " +
            "SELECT d.drone_id, d.serial_no, COUNT(ma.assign_id) AS completed, " +
            "  RANK() OVER (ORDER BY COUNT(ma.assign_id) DESC) AS rank " +
            "FROM drones d " +
            "LEFT JOIN mission_assignments ma ON ma.drone_id = d.drone_id AND ma.status = 'done' " +
            "WHERE d.drone_id != 0 " +
            "GROUP BY d.drone_id, d.serial_no ORDER BY rank"
        );
    }

    @GetMapping("/drones")
    public List<Map<String, Object>> explainDrones() {
        return runExplain(
            "EXPLAIN ANALYZE SELECT * FROM v_drone_latest"
        );
    }

    private List<Map<String, Object>> runExplain(String sql) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = pgDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
        } catch (SQLException e) {
            return List.of(Map.of("error", e.getMessage()));
        }
        return rows;
    }
}
