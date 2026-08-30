package com.fortell.boardgame.models;

public record RoomSeat(int seatIndex, Long userId, String username) {

    public boolean occupied() {
        return userId != null;
    }

    public boolean bot() {
        return userId != null && userId < 0;
    }
}
