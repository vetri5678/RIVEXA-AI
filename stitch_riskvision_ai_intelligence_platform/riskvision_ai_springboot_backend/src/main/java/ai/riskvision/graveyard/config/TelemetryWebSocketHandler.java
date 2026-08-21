package ai.riskvision.graveyard.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class TelemetryWebSocketHandler extends TextWebSocketHandler {

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("[WebSocket] Connection established: sessionId={}, remoteAddress={}", 
                session.getId(), session.getRemoteAddress());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("[WebSocket] Message received from sessionId={}: {}", session.getId(), payload);
        
        if (payload.contains("PING")) {
            try {
                session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
            } catch (IOException e) {
                log.error("[WebSocket] Failed to send PONG response to sessionId={}: {}", session.getId(), e.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        log.info("[WebSocket] Connection closed: sessionId={}, status={}", session.getId(), status);
    }

    public void broadcast(String message) {
        log.debug("[WebSocket] Broadcasting message to {} sessions", sessions.size());
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    log.error("[WebSocket] Broadcast failed for sessionId={}: {}", session.getId(), e.getMessage());
                }
            } else {
                sessions.remove(session);
            }
        }
    }
}
