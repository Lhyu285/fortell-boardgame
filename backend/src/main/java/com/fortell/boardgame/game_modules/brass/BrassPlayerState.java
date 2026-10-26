package com.fortell.boardgame.game_modules.brass;

import java.util.Map;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;

record BrassPlayerState(long playerId, int money, int incomeLevel, int victoryPoints, int spentThisRound) {
    static BrassPlayerState read(Map<String, Object> state, long playerId) {
        Map<String, Object> stats = viewMap(viewMap(state.get("playerStats")).get(String.valueOf(playerId)));
        return fromStats(playerId, stats);
    }
    static BrassPlayerState fromStats(long playerId, Map<String, Object> stats) {
        return new BrassPlayerState(playerId, parseInteger(stats.get("money"), 0), parseInteger(stats.get("incomeLevel"), 0),
                parseInteger(stats.get("victoryPoints"), 0), parseInteger(stats.get("spentThisRound"), 0));
    }
}
