package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.UserSummary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;
import static com.fortell.boardgame.game_modules.brass.BrassProjectionModel.*;

/** Outbound boundary: preserve public counts without exposing hands or restore data. */
final class BrassViewerProjection {
    private BrassViewerProjection() {}

    static Map<String, Object> forViewer(Map<String, Object> state, UserSummary viewer) {
        Map<String, Object> visible = new LinkedHashMap<>(state);
        for (var field : PrivateField.values()) visible.remove(field.key);
        var rawHands = viewMap(state.get("hands"));
        var policy = BrassTypedProjection.viewer(viewer == null ? null : viewer.id(), asLong(state.get("currentPlayerId")),
                rawHands.entrySet().stream().map(entry -> new HandCount(entry.getKey(), cardCount(entry.getValue()))).toList(),
                cardCount(state.get("deck")));
        Map<String, Object> hands = new LinkedHashMap<>();
        for (var hand : policy.hands()) hands.put(hand.playerId(), hand.visible() ? rawHands.get(hand.playerId()) : concealedCards(hand.count()));
        visible.put("hands", hands);
        visible.put("deck", concealedCards(policy.deckCount()));
        if (!policy.fullActions()) {
            Map<String, Object> actions = new LinkedHashMap<>();
            Map<String, Object> available = viewMap(state.get("availableActions"));
            for (var field : PublicActionField.values()) {
                if (available.containsKey(field.key)) actions.put(field.key, available.get(field.key));
            }
            actions.put("actions", List.of());
            visible.put("availableActions", actions);
        }
        return mapOf(deepCopy(visible));
    }

    private static int cardCount(Object cards) { return cards instanceof List<?> list ? list.size() : 0; }
    private static List<Map<String, Object>> concealedCards(int count) {
        return IntStream.range(0, count).mapToObj(index -> Map.<String, Object>of("hidden", true)).toList();
    }
}
