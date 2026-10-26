package com.fortell.boardgame.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortell.boardgame.models.RoomDtos;
import com.fortell.boardgame.models.UserSummary;
import com.fortell.boardgame.utils.SessionKeys;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Exercises the real routing hub with mocked sessions; no browser/network load claim. */
class RoomWebSocketHundredRoomTest {
    @Test
    void oneHundredRoomsRouteSnapshotsPerRoomAndViewerIncludingOneUserInMultipleRooms() throws Exception {
        var mapper = new ObjectMapper();
        var hub = new RoomWebSocketHub(mapper);
        var sessions = new ArrayList<WebSocketSession>();
        var messages = new ArrayList<List<String>>();
        for (int room = 0; room < 100; room++) {
            for (int seat = 0; seat < 2; seat++) {
                long userId = seat == 0 ? 1 : room + 2;
                var session = mock(WebSocketSession.class);
                var inbox = new CopyOnWriteArrayList<String>();
                when(session.isOpen()).thenReturn(true);
                when(session.getAttributes()).thenReturn(Map.of(SessionKeys.USER_ID, userId, SessionKeys.USERNAME, "user-" + userId));
                doAnswer(call -> { inbox.add(((TextMessage) call.getArgument(0)).getPayload()); return null; })
                        .when(session).sendMessage(any(TextMessage.class));
                hub.addSession(RoomWebSocketHub.roomKey("brass", "table-" + room), session);
                sessions.add(session);
                messages.add(inbox);
            }
        }
        try (var workers = Executors.newFixedThreadPool(16)) {
            var tasks = new ArrayList<Future<?>>();
            for (int room = 0; room < 100; room++) {
                String roomId = "table-" + room;
                tasks.add(workers.submit(() -> hub.broadcastRoom("brass", roomId, viewer -> view(roomId, viewer))));
            }
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
        }
        for (int i = 0; i < messages.size(); i++) {
            assertEquals(1, messages.get(i).size());
            var body = mapper.readTree(messages.get(i).getFirst());
            assertEquals("room.snapshot", body.path("type").asText());
            assertEquals("table-" + i / 2, body.path("payload").path("roomId").asText());
            assertEquals(i % 2 == 0 ? 1 : i / 2 + 2, body.path("payload").path("gameState").path("privateViewerId").asLong());
        }
        hub.broadcastDismissed("rps", "table-0", "wrong game");
        assertEquals(1, messages.getFirst().size(), "game key is part of room identity");
        hub.removeSession("brass:table-0", sessions.getFirst());
        hub.broadcastDismissed("brass", "table-0", "closed");
        assertEquals(1, messages.getFirst().size(), "leaving stops delivery only to that subscription");
        assertEquals(2, messages.get(1).size());
        for (int i = 2; i < messages.size(); i++) assertEquals(1, messages.get(i).size());
    }

    private RoomDtos.RoomView view(String roomId, UserSummary viewer) {
        return new RoomDtos.RoomView("brass", roomId, "IN_PROGRESS", new UserSummary(1, "owner"), viewer, 2,
                null, List.of(), Map.of(), Map.of("privateViewerId", viewer.id()), List.of(), List.of(), null);
    }
}
