package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.GameTransitionResult;
import com.fortell.boardgame.models.UserSummary;
import java.util.Map;
import java.util.Objects;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;

/** Builds this operation's audit event, independently of the bounded UI history. */
final class BrassActionHistory {
    private BrassActionHistory() {}
    static GameTransitionResult record(Map<String, Object> before, Map<String, Object> after, UserSummary actor, BrassActionCommand command) {
        if (BrassStateDocument.isSimulation(after)) throw new IllegalArgumentException("Cannot audit a candidate simulation");
        BrassGameState source = BrassFlowAdapter.gameState(before);
        BrassActionType type = command.type();
        long startVersion = BrassTypedEvents.startVersion(source, type);
        if (type == BrassActionType.SELL || type == BrassActionType.END_SELL) {
            Map<String, Object> turn = mapOf(after.get("turn"));
            if (type == BrassActionType.SELL) turn.put("sellActionStartVersion", startVersion);
            else turn.remove("sellActionStartVersion");
            after.put("turn", turn);
        }
        after.put("version", source.version());
        BrassGameState target = BrassFlowAdapter.gameState(after);
        if (BrassTypedEvents.startsTurn(source, target)) {
            Map<String, Object> snapshot = mapOf(after.get("turnStartSnapshot"));
            snapshot.put("version", source.version() + 1);
            after.put("turnStartSnapshot", snapshot);
        }
        String summary = after instanceof BrassStateDocument document
                ? Objects.requireNonNullElse(document.operationSummary(), type.label) : type.label;
        var record = new BrassEventModel(type, actor.id(), actor.username(), summary, source, target, startVersion);
        BrassEventAdapter.appendPublicRecord(after, record);
        return new GameTransitionResult(after, BrassEventAdapter.encode(record, before, after, command.payload()), record.message());
    }
}
