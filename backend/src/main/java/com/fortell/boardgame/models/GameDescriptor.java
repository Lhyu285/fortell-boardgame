package com.fortell.boardgame.models;

public record GameDescriptor(
        String key,
        String name,
        String englishName,
        String path,
        String rulesPath,
        int minPlayers,
        int maxPlayers,
        String recommendedPlayers,
        String summary
) {
}
