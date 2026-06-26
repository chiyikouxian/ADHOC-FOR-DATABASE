package com.fanet.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fanet.model.TelemetryRecord;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * MQTT 遥测 + 链路数据接入服务。
 * 订阅 fanet/telemetry 和 fanet/network_links 两个主题。
 */
@Service
public class MqttTelemetryService {

    private final TelemetryService telemetryService;
    private final DataSource tdDataSource;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MqttClient client;

    private static final String BROKER = "tcp://" +
            System.getenv().getOrDefault("MQTT_HOST", "127.0.0.1") + ":" +
            System.getenv().getOrDefault("MQTT_PORT", "1883");
    private static final String TOPIC_TELEMETRY = "fanet/telemetry";
    private static final String TOPIC_NETWORK_LINKS = "fanet/network_links";

    public MqttTelemetryService(TelemetryService telemetryService,
                                @Qualifier("tdDataSource") DataSource tdDataSource) {
        this.telemetryService = telemetryService;
        this.tdDataSource = tdDataSource;
    }

    @PostConstruct
    public void start() {
        try {
            client = new MqttClient(BROKER, "fanet-backend-" + System.currentTimeMillis());
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setAutomaticReconnect(true);
            opts.setCleanSession(true);
            client.connect(opts);

            // 订阅遥测主题
            client.subscribe(TOPIC_TELEMETRY, (topic, msg) -> {
                String json = new String(msg.getPayload());
                try {
                    List<TelemetryRecord> records = objectMapper.readValue(json,
                            new TypeReference<List<TelemetryRecord>>() {});
                    telemetryService.ingest(records);
                } catch (Exception e) {
                    System.err.println("[MQTT] 遥测解析失败: " + e.getMessage());
                }
            });

            // 订阅链路主题
            client.subscribe(TOPIC_NETWORK_LINKS, (topic, msg) -> {
                String json = new String(msg.getPayload());
                try {
                    List<Map<String, Object>> links = objectMapper.readValue(json,
                            new TypeReference<List<Map<String, Object>>>() {});
                    ingestNetworkLinks(links);
                } catch (Exception e) {
                    System.err.println("[MQTT] 链路解析失败: " + e.getMessage());
                }
            });

            System.out.println("[MQTT] 已连接 " + BROKER + "，订阅: " + TOPIC_TELEMETRY + ", " + TOPIC_NETWORK_LINKS);
        } catch (MqttException e) {
            System.err.println("[MQTT] 连接失败: " + e.getMessage() + "（无 MQTT Broker 时忽略）");
        }
    }

    /**
     * 写入 TDengine network_links 超表
     */
    private void ingestNetworkLinks(List<Map<String, Object>> links) {
        if (links == null || links.isEmpty()) return;

        StringBuilder sql = new StringBuilder("INSERT INTO ");
        for (int i = 0; i < links.size(); i++) {
            Map<String, Object> link = links.get(i);
            int src = toInt(link.get("srcDroneId"));
            int dst = toInt(link.get("dstDroneId"));
            int quality = toInt(link.get("linkQuality"));
            boolean active = link.get("isActive") instanceof Boolean b ? b : false;

            if (i > 0) sql.append(' ');
            sql.append("l_link_").append(src).append('_').append(dst)
               .append(" USING network_links TAGS(").append(src).append(',').append(dst).append(')')
               .append(" VALUES(NOW,").append(quality).append(',').append(active).append(')');
        }

        try (Connection conn = tdDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql.toString());
        } catch (SQLException e) {
            System.err.println("[TDengine] 链路写入失败: " + e.getMessage());
        }
    }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) return Integer.parseInt(s);
        return 0;
    }

    @PreDestroy
    public void stop() {
        try { if (client != null) client.disconnect(); } catch (Exception ignored) {}
    }
}
