package com.fanet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Service
public class Nl2SqlService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DataSource pgDataSource;
    private final DataSource tdDataSource;

    @Value("${llm.api-base:https://api.deepseek.com}")
    private String apiBase;

    @Value("${llm.api-key:}")
    private String apiKey;

    @Value("${llm.model:deepseek-chat}")
    private String model;

    // 白名单：只允许 SELECT 这些表
    private static final Set<String> PG_TABLES = Set.of(
            "users", "drone_models", "drones", "missions", "mission_assignments",
            "waypoints", "alerts", "audit_log", "drone_latest", "network_links_snapshot",
            "v_drone_latest"
    );
    private static final Set<String> TD_TABLES = Set.of("telemetry", "network_links");

    // 双库 schema 上下文（喂给 LLM）
    private static final String SCHEMA_CONTEXT = """
            你是一个 SQL 专家。数据库是无人机自组网管理平台，有两个数据库：

            【PostgreSQL（关系核心）】表：
            - users(user_id, username, role)
            - drone_models(model_id, model_name, max_flight_minutes, max_speed)
            - drones(drone_id, model_id, serial_no, status)  -- status: idle/assigned/flying/offline/maintenance; drone_id=0 是地面站
            - missions(mission_id, creator_id, title, status, planned_start, created_at) -- status: draft/scheduled/running/completed/aborted
            - mission_assignments(assign_id, mission_id, drone_id, status, assigned_at, completed_at) -- status: assigned/executing/done/failed/released
            - waypoints(wp_id, assign_id, seq, lat, lon, alt)
            - alerts(alert_id, drone_id, alert_type, severity, detail, created_at, resolved) -- severity: info/warning/critical
            - drone_latest(drone_id, ts, lat, lon, alt, battery_pct, rssi)
            - v_drone_latest -- 视图：每架机最新状态（含型号信息）

            【TDengine（遥测时序）】超级表：
            - telemetry (ts, lat, lon, alt, battery_pct, rssi) TAGS (drone_id, model_id)
            - network_links (ts, link_quality, is_active) TAGS (src_drone_id, dst_drone_id)

            规则：
            1. 关系/排名/统计/CRUD 类问题 → 查 PostgreSQL
            2. 时序/趋势/按时间聚合/历史曲线类问题 → 查 TDengine
            3. TDengine 用 INTERVAL 做时间窗聚合，LAST() 取最新值
            4. PostgreSQL 用标准 SQL

            返回 JSON 格式（不要 markdown 代码块）：
            {"target_db":"pg","sql":"SELECT ..."}
            或
            {"target_db":"tdengine","sql":"SELECT ..."}
            只返回 JSON，不要任何解释文字。""";

    public Nl2SqlService(@Qualifier("pgDataSource") DataSource pgDataSource,
                         @Qualifier("tdDataSource") DataSource tdDataSource) {
        this.pgDataSource = pgDataSource;
        this.tdDataSource = tdDataSource;
    }

    public Map<String, Object> ask(String question) {
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your-deepseek-api-key")) {
            return Map.of("error", "API Key 未配置，请在 .env 或 application.yml 中设置 llm.api-key");
        }

        // 1) 调 DeepSeek 生成 SQL
        String llmResponse = callLlm(question);
        if (llmResponse == null) return Map.of("error", "LLM 调用失败");

        // 2) 解析 JSON
        Map<String, String> parsed = parseLlmResponse(llmResponse);
        if (parsed == null) return Map.of("error", "LLM 返回格式异常", "raw", llmResponse);

        String targetDb = parsed.get("target_db");
        String sql = parsed.get("sql");
        if (targetDb == null || sql == null) return Map.of("error", "SQL 解析失败", "raw", llmResponse);

        // 3) 白名单校验
        if (!validateSql(sql, targetDb)) {
            return Map.of("error", "SQL 校验不通过（仅允许 SELECT 白名单表）", "sql", sql);
        }

        // 4) 执行
        DataSource ds = "tdengine".equals(targetDb) ? tdDataSource : pgDataSource;
        List<Map<String, Object>> rows = executeSql(ds, sql);
        return Map.of("question", question, "target_db", targetDb, "sql", sql, "rows", rows != null ? rows : List.of());
    }

    private String callLlm(String question) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", SCHEMA_CONTEXT),
                            Map.of("role", "user", "content", question)
                    ),
                    "temperature", 0.1,
                    "max_tokens", 500
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = restTemplate.postForEntity(apiBase + "/v1/chat/completions", req, Map.class);
            Map body2 = resp.getBody();
            List<Map> choices = (List<Map>) body2.get("choices");
            Map msg = (Map) ((Map) choices.get(0)).get("message");
            return (String) msg.get("content");
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, String> parseLlmResponse(String text) {
        try {
            // 清理可能的 markdown 代码块包裹
            String json = text.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```\\w*\\n?", "").replaceAll("```", "").trim();
            }
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean validateSql(String sql, String targetDb) {
        String upper = sql.toUpperCase().trim();
        if (!upper.startsWith("SELECT")) return false;
        if (upper.contains(";")) return false;                    // 禁止多语句
        if (upper.contains("DROP") || upper.contains("DELETE")
                || upper.contains("INSERT") || upper.contains("UPDATE")
                || upper.contains("ALTER") || upper.contains("CREATE")) return false;

        // 表名白名单
        Set<String> allowed = "tdengine".equals(targetDb) ? TD_TABLES : PG_TABLES;
        String lower = sql.toLowerCase();
        for (String table : allowed) {
            if (lower.contains(table)) return true;
        }
        return false; // SQL 中没找到任何白名单表名
    }

    private List<Map<String, Object>> executeSql(DataSource ds, String sql) {
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        } catch (SQLException e) {
            return List.of(Map.of("sql_error", e.getMessage()));
        }
    }
}
