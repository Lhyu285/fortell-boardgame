package com.fortell.boardgame.models;

import java.time.Instant;

public record RoomEntity(
        long id,
        GameType gameType,
        String roomId,
        long ownerUserId,
        String passwordHash,
        int seatCount,
        RoomStatus status,
        String configJson,
        String gameStateJson,
        Instant createdAt,
        Instant updatedAt
) {

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }
}
