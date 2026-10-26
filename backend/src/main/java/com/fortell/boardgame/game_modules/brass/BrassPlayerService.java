package com.fortell.boardgame.game_modules.brass;

import java.util.List;
import java.util.Map;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.listOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.mapOf;

/** Compatibility facade for typed player assets and map-industry ownership. */
final class BrassPlayerService {
    private BrassPlayerService() {}

    static List<Map<String, Object>> ownedMapIndustries(Map<String, Object> state, long playerId) {
        List<Map<String, Object>> tiles = listOf(mapOf(state.get("board")).get("industries"));
        return BrassTypedIndustries.ownedIndices(BrassBoardAdapter.industries(tiles), playerId).stream().map(tiles::get).toList();
    }

    static void payMoney(Map<String, Object> state, long playerId, int amount) {
        var next = BrassTypedPlayers.pay(BrassPersonalAdapter.stats(state, playerId, 0), amount);
        BrassPersonalAdapter.writeStats(state, playerId, next, java.util.Set.of(BrassTypedPlayers.Field.MONEY, BrassTypedPlayers.Field.SPENT));
    }

    static void addIncome(Map<String, Object> state, long playerId, int incomeReward) {
        var next = BrassTypedPlayers.income(BrassPersonalAdapter.stats(state, playerId, 0), incomeReward);
        BrassPersonalAdapter.writeStats(state, playerId, next, java.util.Set.of(BrassTypedPlayers.Field.INCOME_LEVEL, BrassTypedPlayers.Field.INCOME));
    }

    static void gainMoney(Map<String, Object> state, long playerId, int amount) {
        BrassPersonalAdapter.writeStats(state, playerId, BrassTypedPlayers.gain(BrassPersonalAdapter.stats(state, playerId, 0), amount),
                java.util.Set.of(BrassTypedPlayers.Field.MONEY));
    }

    static int playerMoney(Map<String, Object> state, long playerId) {
        return BrassPersonalAdapter.stats(state, playerId, 0).money();
    }

    static void addVictoryPoints(Map<String, Object> state, long playerId, int amount) {
        BrassPersonalAdapter.writeStats(state, playerId, BrassTypedPlayers.victoryPoints(BrassPersonalAdapter.stats(state, playerId, 0), amount),
                java.util.Set.of(BrassTypedPlayers.Field.VP));
    }
}
