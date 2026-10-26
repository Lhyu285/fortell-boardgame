package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.UserSummary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.fortell.boardgame.game_modules.brass.BrassCardService.buildEraDeck;
import static com.fortell.boardgame.game_modules.brass.BrassCardService.initialScoutPool;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.asLong;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.listOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.mapOf;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.appendNotice;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.describeOperation;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;
import static com.fortell.boardgame.game_modules.brass.BrassScoringService.scoreCurrentEra;
import static com.fortell.boardgame.game_modules.brass.BrassScoringService.winnersOf;
import static com.fortell.boardgame.game_modules.brass.BrassScoringService.winnersText;
import static com.fortell.boardgame.game_modules.brass.BrassSetupData.initialMarket;
import static com.fortell.boardgame.game_modules.brass.BrassTurnService.beginTurn;
import static com.fortell.boardgame.game_modules.brass.BrassTurnService.canMaintainEra;
import static com.fortell.boardgame.game_modules.brass.BrassTurnService.resetRoundSpending;

/** Era Service rules; all working state is passed explicitly. */
final class BrassEraService {
    private BrassEraService() {}

    static Map<String, Object> maintainEraAction(Map<String, Object> state, UserSummary actor) {
        var step = BrassTypedEra.maintenance(canMaintainEra(state), "canal".equals(state.get("era")));
        scoreCurrentEra(state);
        if (step == BrassFlowModel.MaintenanceStep.START_RAIL) {
            transitionToRailEra(state);
            describeOperation(state, actor, "maintain_era", "\u8fd0\u6cb3\u65f6\u4ee3\u7ed3\u675f\uff0c\u8fdb\u5165\u94c1\u8def\u65f6\u4ee3");
            return state;
        }

        finishGame(state);
        describeOperation(state, actor, "maintain_era", "\u94c1\u8def\u65f6\u4ee3\u7ed3\u675f\uff0c\u6e38\u620f\u7ed3\u675f");
        return state;
    }

    static void transitionToRailEra(Map<String, Object> state) {
        Map<String, Object> board = mapOf(state.get("board"));
        List<Map<String, Object>> remainingIndustries = listOf(board.get("industries")).stream()
                .filter(tile -> !removedAfterCanalEra(tile))
                .toList();
        board.put("industries", new ArrayList<>(remainingIndustries));
        board.put("links", new ArrayList<>());
        board.put("availableRoutes", routes());
        board.put("buildableCities", new ArrayList<>(citySlotOptions().keySet()));
        board.put("citySlots", citySlotOptions());
        board.put("map", mapSummary());
        board.put("eraScored", false);
        state.put("board", board);

        List<Map<String, Object>> players = listOf(state.get("players"));
        List<Map<String, Object>> deck = buildEraDeck(players.size());
        Collections.shuffle(deck);
        Map<String, Object> hands = new LinkedHashMap<>();
        for (Map<String, Object> player : players) {
            List<Map<String, Object>> hand = new ArrayList<>();
            for (int index = 0; index < STARTING_HAND_SIZE && !deck.isEmpty(); index++) {
                hand.add(deck.remove(0));
            }
            hands.put(String.valueOf(player.get("userId")), hand);
        }

        resetRoundSpending(state);
        var start = BrassTypedEra.railStart(BrassFlowAdapter.order(state),
                players.stream().map(player -> asLong(player.get("userId"))).toList());
        state.put("era", "rail");
        state.put("round", start.round());
        state.put("turnIndex", start.turnIndex());
        state.put("currentPlayerId", start.firstPlayerId());
        state.put("hands", hands);
        state.put("deck", deck);
        state.put("scoutPool", initialScoutPool());
        state.put("discardPile", new ArrayList<>());
        Map<String, Object> market = initialMarket(players.size());
        List<Map<String, Object>> merchants = listOf(mapOf(state.get("market")).get("beerMerchants"));
        if (!merchants.isEmpty()) {
            for (Map<String, Object> merchant : merchants) {
                int beer = BrassTypedEra.merchantBeer(Boolean.TRUE.equals(merchant.get("providesBeer")),
                        Boolean.TRUE.equals(merchant.get("marketOpen")), Boolean.TRUE.equals(merchant.get("blank")));
                merchant.put("beer", beer);
                merchant.put("used", beer == 0);
            }
            market.put("beerMerchants", merchants);
        }
        state.put("market", market);
        state.put("canMaintainEra", false);
        state.put("eraEnding", false);
        beginTurn(state, "\u94c1\u8def\u65f6\u4ee3\u5f00\u59cb\uff0c\u8f6e\u5230 " + playerName(players, start.firstPlayerId()) + " \u884c\u52a8");
    }

    static boolean removedAfterCanalEra(Map<String, Object> tile) {
        return BrassTypedEra.removed(BrassFlowAdapter.industry(tile));
    }

    static void finishGame(Map<String, Object> state) {
        state.put("phase", "finished");
        state.put("canMaintainEra", false);
        state.put("availableActions", new LinkedHashMap<>());
        state.put("turn", new LinkedHashMap<>());
        state.put("turnStartSnapshot", null);
        state.put("winners", winnersOf(state));
        appendNotice(state, "\u6e38\u620f\u7ed3\u675f\uff0c\u80dc\u8005\uff1a" + winnersText(listOf(state.get("winners"))));
    }
}
