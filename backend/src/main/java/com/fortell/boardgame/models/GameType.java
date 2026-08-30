package com.fortell.boardgame.models;

public enum GameType {
    RPS("rps", "猜拳", 2, 8, 4),
    THINGS_IN_RINGS("thingsInRings", "环中物语", 2, 6, 4),
    CAMEL_UP_CARDS("camel_up_cards", "狂野骆驼：卡牌版", 2, 6, 4),
    GOBANG("gobang", "五子棋", 2, 2, 2),
    BRASS("brass", "伯明翰", 2, 4, 4);

    private final String path;
    private final String displayName;
    private final int minPlayers;
    private final int maxPlayers;
    private final int defaultSeats;

    GameType(String path, String displayName, int minPlayers, int maxPlayers, int defaultSeats) {
        this.path = path;
        this.displayName = displayName;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.defaultSeats = defaultSeats;
    }

    public String path() {
        return path;
    }

    public String displayName() {
        return displayName;
    }

    public int minPlayers() {
        return minPlayers;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public int defaultSeats() {
        return defaultSeats;
    }

    public static GameType fromPath(String value) {
        for (GameType type : values()) {
            if (type.path.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported game: " + value);
    }
}
