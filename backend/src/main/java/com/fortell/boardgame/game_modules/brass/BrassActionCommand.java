package com.fortell.boardgame.game_modules.brass;

import java.util.Map;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;

/** Typed operation identity with a detached, backwards-compatible wire payload. */
record BrassActionCommand(BrassActionType type, Map<String, Object> payload, BrassActionInput input) {
    BrassActionCommand {
        payload = immutableData(mapOf(deepCopy(payload == null ? Map.of() : payload)));
        input = BrassActionInput.decode(type, payload);
    }
    static BrassActionCommand decode(String type, Map<String, Object> payload) {
        return new BrassActionCommand(BrassActionType.parse(type), payload, null);
    }
}
