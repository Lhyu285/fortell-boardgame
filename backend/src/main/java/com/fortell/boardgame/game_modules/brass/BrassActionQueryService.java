package com.fortell.boardgame.game_modules.brass;

import java.util.Map;

/** Query entry point; JSON is encoded only for external callers, not action validation. */
final class BrassActionQueryService {
    private BrassActionQueryService() {}
    static BrassQueryModel.Result query(Map<String, Object> state) {
        var input = BrassQueryAdapter.input(state);
        return BrassTypedQueries.query(input, new BrassSimulationProbe(state, input.context().actor()));
    }
    static Map<String, Object> availableActions(Map<String, Object> state) {
        return BrassJsonCodec.mapOf(BrassJsonCodec.deepCopy(BrassQueryAdapter.encode(state, query(state))));
    }
}
