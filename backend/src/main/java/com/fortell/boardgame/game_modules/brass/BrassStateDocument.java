package com.fortell.boardgame.game_modules.brass;

import java.util.LinkedHashMap;
import java.util.Map;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;

/** JSON working copy. Execution mode is out-of-band and cannot be supplied by a client. */
final class BrassStateDocument extends LinkedHashMap<String, Object> {
    private final BrassExecutionMode mode;
    private String operationSummary;

    private BrassStateDocument(Map<String, Object> state, BrassExecutionMode mode) {
        super(BrassJsonCodec.viewMap(deepCopy(state)));
        this.mode = mode;
        remove("_candidateSimulation");
    }

    static BrassStateDocument executionCopy(Map<String, Object> state) {
        return new BrassStateDocument(state, BrassExecutionMode.EXECUTE);
    }

    static BrassStateDocument simulationCopy(Map<String, Object> state) {
        Map<String, Object> rules = new LinkedHashMap<>(state);
        // Candidate handlers need rule state, not the previous full snapshot or UI history.
        for (String field : java.util.List.of("turnStartSnapshot", "availableActions", "actionLog", "notices", "log")) rules.remove(field);
        return new BrassStateDocument(rules, BrassExecutionMode.SIMULATE);
    }

    static boolean isSimulation(Map<String, Object> state) {
        return state instanceof BrassStateDocument document && document.mode == BrassExecutionMode.SIMULATE;
    }

    void describeOperation(String summary) {
        if (mode == BrassExecutionMode.EXECUTE) operationSummary = summary;
    }

    String operationSummary() { return operationSummary; }
}
