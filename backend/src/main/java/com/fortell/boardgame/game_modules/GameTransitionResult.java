package com.fortell.boardgame.game_modules;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** The state and audit record produced by this exact operation. */
public record GameTransitionResult(Map<String, Object> state, Map<String, Object> event, String message) {
    public GameTransitionResult {
        Objects.requireNonNull(state, "state");
        event = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(event, "event")));
        Objects.requireNonNull(message, "message");
    }
}
