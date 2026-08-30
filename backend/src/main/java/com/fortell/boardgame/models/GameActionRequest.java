package com.fortell.boardgame.models;

import java.util.Map;

public record GameActionRequest(
        String type,
        Map<String, Object> payload,
        Long stateVersion,
        String clientActionId
) {
}
