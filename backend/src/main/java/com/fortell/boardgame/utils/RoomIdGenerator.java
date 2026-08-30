package com.fortell.boardgame.utils;

import java.security.SecureRandom;

public final class RoomIdGenerator {
    private static final String ALPHABET = "0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private RoomIdGenerator() {
    }

    public static String generate(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }
}
