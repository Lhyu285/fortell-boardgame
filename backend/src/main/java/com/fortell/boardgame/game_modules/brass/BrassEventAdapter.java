package com.fortell.boardgame.game_modules.brass;

import java.util.*;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;

/** Version-1 event wire contract. Deltas remain lossless JSON, not normalized domain snapshots. */
final class BrassEventAdapter {
    private static final List<String> RULE_FIELDS = List.of("era", "round", "phase", "currentPlayerId", "turnIndex",
            "turnOrder", "turn", "playerStats", "playerBoards", "developments", "hands", "deck", "discardPile",
            "scoutPool", "hiddenDiscardCount", "cardHints", "board", "market", "eraEnding", "incomeDebt", "incomeDebtQueue",
            "pendingFinishedRound", "eraScores", "winners");
    private BrassEventAdapter() {}
    static Map<String, Object> encode(BrassEventModel record, Map<String, Object> before,
                                      Map<String, Object> after, Map<String, Object> input) {
        var changes = new LinkedHashMap<String, Object>();
        for (String field : RULE_FIELDS) if (!Objects.equals(before.get(field), after.get(field))) {
            var change = new LinkedHashMap<String, Object>(); change.put("before", deepCopy(before.get(field)));
            change.put("after", deepCopy(after.get(field))); changes.put(field, change);
        }
        var event = new LinkedHashMap<String, Object>();
        event.put("schemaVersion", BrassEventModel.SCHEMA_VERSION); event.put("type", record.type().key);
        event.put("actorId", record.actorId()); event.put("actorName", record.actorName()); event.put("summary", record.summary());
        event.put("beforeVersion", record.before().version()); event.put("afterVersion", record.afterVersion());
        event.put("era", record.era()); event.put("round", record.before().round()); event.put("turnKey", record.before().turnKey());
        event.put("beforePhase", record.before().phase().name()); event.put("afterPhase", record.after().phase().name());
        event.put("kind", record.kind()); event.put("actionIndex", record.actionIndex()); event.put("businessActionId", record.businessActionId());
        event.put("actionCompleted", record.actionCompleted()); event.put("input", deepCopy(input)); event.put("changes", changes);
        if (record.type() == BrassActionType.RESTART_TURN) event.put("restoredFromVersion", viewMap(before.get("turnStartSnapshot")).get("version"));
        return event;
    }
    static void appendPublicRecord(Map<String, Object> state, BrassEventModel record) {
        var log = listOf(state.get("actionLog")); var item = new LinkedHashMap<String, Object>();
        item.put("actorId", record.actorId()); item.put("actorName", record.actorName()); item.put("type", record.type().key);
        item.put("summary", record.summary()); item.put("era", record.era()); item.put("round", record.before().round());
        item.put("actionsTaken", record.before().turn().actionsTaken());
        // Public UI history deliberately excludes input, changes and restore snapshots.
        log.add(0, item); state.put("actionLog", log.stream().limit(30).toList());
    }
}
