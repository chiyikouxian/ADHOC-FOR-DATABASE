package com.fanet.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class DroneWebSocketHandler extends TextWebSocketHandler {

    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void broadcast(String type, Object payload) {
        try {
            Map<String, Object> msg = Map.of("type", type, "payload", payload);
            String json = objectMapper.writeValueAsString(msg);
            TextMessage tm = new TextMessage(json);
            for (WebSocketSession s : sessions) {
                if (s.isOpen()) {
                    try { s.sendMessage(tm); } catch (IOException ignored) { sessions.remove(s); }
                }
            }
        } catch (Exception ignored) { /* malformed JSON won't happen */ }
    }

    public int activeSessionCount() {
        return sessions.size();
    }
}
