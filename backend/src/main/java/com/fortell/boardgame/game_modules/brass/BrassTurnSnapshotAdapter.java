package com.fortell.boardgame.game_modules.brass;

import java.util.Map;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;

/** A snapshot is the complete recovery authority, including unknown/private legacy fields.
 * Opaque JSON copy is intentional; strict model decoding would change historical save acceptance.
 */
final class BrassTurnSnapshotAdapter {
    private BrassTurnSnapshotAdapter() {}
    static Map<String, Object> capture(Map<String, Object> state) {
        var snapshot = mapOf(deepCopy(state)); snapshot.remove("turnStartSnapshot"); return snapshot;
    }
    static void restore(Map<String, Object> state, Map<String, Object> snapshot) {
        var restored = mapOf(deepCopy(snapshot));
        state.clear(); state.putAll(restored); state.put("turnStartSnapshot", deepCopy(snapshot));
    }
}
