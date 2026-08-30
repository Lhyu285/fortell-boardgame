package com.fortell.boardgame.utils;

import java.util.regex.Pattern;

public final class RoomRules {
    private static final Pattern ROOM_PATTERN = Pattern.compile("^[0-9]{3,8}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[A-Za-z0-9]{4,8}$");

    private RoomRules() {
    }

    public static boolean isValidRoomId(String roomId) {
        return roomId != null && ROOM_PATTERN.matcher(roomId).matches();
    }

    public static boolean isValidRoomPassword(String password) {
        return password == null || password.isBlank() || PASSWORD_PATTERN.matcher(password).matches();
    }
}
