package com.fortell.boardgame.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortell.boardgame.models.RoomDtos;
import com.fortell.boardgame.models.UserSummary;
import com.fortell.boardgame.utils.SessionKeys;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Component
public class RoomWebSocketHub implements RealtimeGateway {
    private final ObjectMapper objectMapper;
    private final Map<String, Set<WebSocketSession>> sessionsByRoom = new ConcurrentHashMap<>();

    public RoomWebSocketHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void addSession(String roomKey, WebSocketSession session) {
        sessionsByRoom.computeIfAbsent(roomKey, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void removeSession(String roomKey, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByRoom.get(roomKey);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByRoom.remove(roomKey);
        }
    }

    @Override
    public void broadcastRoom(String gameType, String roomId, Function<UserSummary, RoomDtos.RoomView> roomViewFactory) {
        Set<WebSocketSession> sessions = sessionsByRoom.get(roomKey(gameType, roomId));
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            UserSummary user = userOf(session);
            if (user == null) {
                continue;
            }
            sendToSession(session, Map.of(
                    "type", "room.snapshot",
                    "payload", roomViewFactory.apply(user)
            ));
        }
    }

    @Override
    public void broadcastDismissed(String gameType, String roomId, String message) {
        send(roomKey(gameType, roomId), Map.of(
                "type", "room.dismissed",
                "payload", Map.of("message", message)
        ));
    }

    public static String roomKey(String gameType, String roomId) {
        return gameType + ":" + roomId;
    }

    private void send(String roomKey, Map<String, Object> body) {
        Set<WebSocketSession> sessions = sessionsByRoom.get(roomKey);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception exception) {
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            sendRaw(session, message);
        }
    }

    private void sendToSession(WebSocketSession session, Map<String, Object> body) {
        try {
            sendRaw(session, new TextMessage(objectMapper.writeValueAsString(body)));
        } catch (Exception ignored) {
        }
    }

    private void sendRaw(WebSocketSession session, TextMessage message) {
        try {
            session.sendMessage(message);
        } catch (IOException ignored) {
        }
    }

    private UserSummary userOf(WebSocketSession session) {
        Object userId = session.getAttributes().get(SessionKeys.USER_ID);
        Object username = session.getAttributes().get(SessionKeys.USERNAME);
        if (!(userId instanceof Long id) || !(username instanceof String name)) {
            return null;
        }
        return new UserSummary(id, name);
    }
}
