package com.fortell.boardgame.websocket;

import com.fortell.boardgame.models.RoomDtos;
import com.fortell.boardgame.models.UserSummary;

import java.util.function.Function;

public interface RealtimeGateway {
    void broadcastRoom(String gameType, String roomId, Function<UserSummary, RoomDtos.RoomView> roomViewFactory);

    void broadcastDismissed(String gameType, String roomId, String message);
}
