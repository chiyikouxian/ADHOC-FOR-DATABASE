package com.fanet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

@Service
public class Nl2SqlService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DataSource pgDataSource;
    private final DataSource tdDataSource;

    private final String apiBase;
    private final String apiKey;
    private final String model;

    // 白名单：只允许 SELECT 这些表
    private static final Set<String> PG_TABLES = Set.of(
            "users", "drone_models", "drones", "missions", "mission_assignments",
            "waypoints", "alerts", "audit_log", "drone_latest", "network_links_snapshot",
            "v_drone_latest"
    );
    private static final Set<String> TD_TABLES = Set.of("telemetry", "network_links");

    // 双库 schema 上下文（喂给 LLM）
    private static final String SCHEMA_CONTEXT = "你是一个SQL专家。无人机自组网管理平台有两个数据库：\n" +
            "【PostgreSQL】表: users(username,role), drones(drone_id,serial_no,status), missions(mission_id,title,status), mission_assignments(mission_id,drone_id,status), waypoints(assign_id,seq,lat,lon,alt), alerts(drone_id,alert_type,severity,detail,resolved), drone_latest(drone_id,battery_pct,rssi,lat,lon,alt)\n" +
            "【TDengine】超级表: telemetry(ts,lat,lon,alt,battery_pct,rssi) TAGS(drone_id,model_id)\n" +
            "规则: 关系/排名/统计→PostgreSQL, 时序/趋势/聚合→TDengine(用INTERVAL)。\n" +
            "只返回JSON: {\"target_db\":\"pg\",\"sql\":\"SELECT ...\"}";

    public Nl2SqlService(@Qualifier("pgDataSource") DataSource pgDataSource,
                         @Qualifier("tdDataSource") DataSource tdDataSource) {
        this.pgDataSource = pgDataSource;
        this.tdDataSource = tdDataSource;
        this.apiBase = cleanEnv("LLM_API_BASE", "https://api.deepseek.com");
        this.apiKey = cleanEnv("LLM_API_KEY", "");
        this.model = cleanEnv("LLM_MODEL", "deepseek-v4-flash");
    }

    /** 读环境变量并清理：去注释、trim、strip 尾部 # 注释 */
    private static String cleanEnv(String name, String defaultValue) {
        String val = System.getenv().getOrDefault(name, defaultValue);
        if (val == null) return null;
        val = val.strip();
        int commentIdx = val.indexOf('#');
        if (commentIdx > 0 && val.charAt(commentIdx - 1) == ' ') {
            val = val.substring(0, commentIdx).strip();
        }
        return val;
    }

    /**
     * 自由格式 LLM 调用（不生成 SQL，返回纯文本）
     * 用于续航分析、复盘报告等非 SQL 场景
     */
    public String askRaw(String prompt) {
        if (apiKey == null || apiKey.isBlank()) return null;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(
                    Map.of("role", "user", "content", prompt)
            ));
            body.put("temperature", 0.3);
            body.put("max_tokens", 300);
            String json = objectMapper.writeValueAsString(body);

            URI uri = URI.create(apiBase + "/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String resp = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (code >= 400) return null;

            Map respMap = objectMapper.readValue(resp, Map.class);
            List<Map> choices = (List<Map>) respMap.get("choices");
            Map msg = (Map) ((Map) choices.get(0)).get("message");
            return (String) msg.get("content");
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> ask(String question) {
        if (apiKey == null || apiKey.isBlank()) {
            return Map.of("error", "API Key 未配置，请设置环境变量 LLM_API_KEY");
        }

        // 1) 调 DeepSeek 生成 SQL
        String llmResponse = callLlm(question);
        if (llmResponse == null) return Map.of("error", "LLM 调用失败：无响应");
        if (llmResponse.startsWith("ERROR:")) return Map.of("error", llmResponse);

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
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", SCHEMA_CONTEXT),
                    Map.of("role", "user", "content", question)
            ));
            body.put("temperature", 0.1);
            body.put("max_tokens", 500);
            String json = objectMapper.writeValueAsString(body);

            URI uri = URI.create(apiBase + "/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String resp = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (code >= 400) return "ERROR: HTTP " + code + " - " + resp.substring(0, Math.min(resp.length(), 200));

            Map respMap = objectMapper.readValue(resp, Map.class);
            List<Map> choices = (List<Map>) respMap.get("choices");
            Map msg = (Map) ((Map) choices.get(0)).get("message");
            return (String) msg.get("content");
        } catch (Exception e) {
            return "ERROR: " + e.getClass().getSimpleName() + " - " + e.getMessage();
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
