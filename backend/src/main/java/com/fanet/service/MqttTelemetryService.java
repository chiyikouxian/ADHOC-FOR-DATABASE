package com.fanet.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fanet.model.TelemetryRecord;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MqttTelemetryService {

    private final TelemetryService telemetryService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MqttClient client;

    private static final String BROKER = "tcp://" +
            System.getenv().getOrDefault("MQTT_HOST", "127.0.0.1") + ":" +
            System.getenv().getOrDefault("MQTT_PORT", "1883");
    private static final String TOPIC = "fanet/telemetry";

    public MqttTelemetryService(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostConstruct
    public void start() {
        try {
            client = new MqttClient(BROKER, "fanet-backend-" + System.currentTimeMillis());
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setAutomaticReconnect(true);
            opts.setCleanSession(true);
            client.connect(opts);

            client.subscribe(TOPIC, (topic, msg) -> {
                String json = new String(msg.getPayload());
                try {
                    List<TelemetryRecord> records = objectMapper.readValue(json,
                            new TypeReference<List<TelemetryRecord>>() {});
                    telemetryService.ingest(records);
                } catch (Exception e) {
                    // 单条解析失败不影响后续
                }
            });

            System.out.println("[MQTT] 已连接 " + BROKER + "，订阅主题: " + TOPIC);
        } catch (MqttException e) {
            System.err.println("[MQTT] 连接失败: " + e.getMessage() + "（无 MQTT Broker 时忽略）");
        }
    }

    @PreDestroy
    public void stop() {
        try { if (client != null) client.disconnect(); } catch (Exception ignored) {}
    }
}
