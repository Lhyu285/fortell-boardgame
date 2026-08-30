package com.fortell.boardgame.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortell.boardgame.models.UserSummary;
import com.fortell.boardgame.services.RoomService;
import com.fortell.boardgame.utils.SessionKeys;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Component
public class BoardGameWebSocketHandler extends TextWebSocketHandler {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final RoomService roomService;
    private final RoomWebSocketHub roomWebSocketHub;

    public BoardGameWebSocketHandler(ObjectMapper objectMapper, RoomService roomService, RoomWebSocketHub roomWebSocketHub) {
        this.objectMapper = objectMapper;
        this.roomService = roomService;
        this.roomWebSocketHub = roomWebSocketHub;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Object userId = session.getAttributes().get(SessionKeys.USER_ID);
        Object username = session.getAttributes().get(SessionKeys.USERNAME);
        if (!(userId instanceof Long) || !(username instanceof String)) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        roomWebSocketHub.addSession(RoomWebSocketHub.roomKey(pathPart(session, 3), pathPart(session, 4)), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> envelope = objectMapper.readValue(message.getPayload(), MAP_TYPE);
        String type = String.valueOf(envelope.get("type"));
        if ("room.ping".equals(type)) {
            session.sendMessage(new TextMessage("{\"type\":\"room.pong\"}"));
            return;
        }
        if (!"game.action".equals(type)) {
            return;
        }

        Map<String, Object> payload = (Map<String, Object>) envelope.getOrDefault("payload", Map.of());
        roomService.handleGameAction(
                pathPart(session, 3),
                pathPart(session, 4),
                String.valueOf(payload.get("type")),
                (Map<String, Object>) payload.getOrDefault("data", Map.of()),
                asLong(payload.get("stateVersion")),
                payload.get("clientActionId") == null ? null : String.valueOf(payload.get("clientActionId")),
                new UserSummary(
                        (Long) session.getAttributes().get(SessionKeys.USER_ID),
                        (String) session.getAttributes().get(SessionKeys.USERNAME)
                )
        );
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        roomWebSocketHub.removeSession(RoomWebSocketHub.roomKey(pathPart(session, 3), pathPart(session, 4)), session);
    }

    private String pathPart(WebSocketSession session, int index) {
        String[] pieces = session.getUri() == null ? new String[0] : session.getUri().getPath().split("/");
        return pieces.length > index ? pieces[index] : "";
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ignored) {
        }
    }
}
