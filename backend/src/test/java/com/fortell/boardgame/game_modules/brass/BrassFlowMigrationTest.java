package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.ApiException;
import com.fortell.boardgame.models.UserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static com.fortell.boardgame.game_modules.brass.BrassCompatibilityFixtures.*;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;
import static org.junit.jupiter.api.Assertions.*;

class BrassFlowMigrationTest {
    @Test void railEraStartsWithTheReorderedFirstPlayerNotTheFirstSeat() throws Exception {
        var state = opening();
        state.put("turnOrder", new ArrayList<>(List.of(2L, 1L)));
        BrassEraService.transitionToRailEra(state);
        assertEquals(2L, asLong(state.get("currentPlayerId")));
        assertEquals(0, state.get("turnIndex"));
        assertEquals(2L, asLong(viewMap(state.get("turnStartSnapshot")).get("currentPlayerId")));
    }

    @Test void nonexistentDebtTileIsRejectedWithoutNullDereferenceOrStateChanges() throws Exception {
        var state = opening();
        state.put("incomeDebt", new LinkedHashMap<>(Map.of("playerId", 1L, "amount", 5)));
        var before = reload(state);
        var error = assertThrows(ApiException.class, () -> BrassTurnService.resolveIncomeDebtAction(state,
                new UserSummary(1, "A"), new BrassActionInput.Debt("missing")));
        assertEquals("请选择地图上自己的产业板块", error.getMessage());
        assertJsonEquals(before, state);
    }

    @ParameterizedTest @CsvSource({"2,canal", "3,canal", "4,canal", "2,rail", "3,rail", "4,rail"})
    void roundAdvancesOnlyAfterEveryPlayerAndUsesCorrectActionAllowance(int count, String era) throws Exception {
        var state = flowState(count, era, 1);
        state.put("currentPlayerId", 1L); state.put("turnIndex", 0);
        BrassTurnService.beginTurn(state, "start");
        for (int i = 1; i <= count; i++) {
            assertEquals(1, state.get("round"));
            assertEquals((long) i, asLong(state.get("currentPlayerId")));
            assertEquals(era.equals("canal") ? 1 : 2, viewMap(state.get("turn")).get("actionsAllowed"));
            makeEndable(state);
            BrassTurnService.endTurnAction(state, new UserSummary(i, "P" + i));
        }
        assertEquals(2, state.get("round"));
        assertEquals(1L, asLong(state.get("currentPlayerId")));
        assertEquals(2, viewMap(state.get("turn")).get("actionsAllowed"));
        assertEquals(12 - count, BrassTurnService.roundsPerEra(state));
    }

    @ParameterizedTest @CsvSource({"2,canal", "3,canal", "4,canal", "2,rail", "3,rail", "4,rail"})
    void lastRoundAlwaysReordersAndOnlyRailSkipsIncome(int count, String era) throws Exception {
        var state = flowState(count, era, 12 - count);
        var stats = viewMap(state.get("playerStats"));
        for (int i = 1; i <= count; i++) {
            var player = viewMap(stats.get("" + i));
            player.put("money", 20); player.put("incomeLevel", 0); player.put("spentThisRound", count - i);
            player.put("lastIncome", 777);
        }
        makeEndable(state);
        BrassTurnService.endTurnAction(state, new UserSummary(count, "P" + count));
        assertEquals(java.util.stream.LongStream.rangeClosed(1, count).map(i -> count + 1 - i).boxed().toList(), longList(state.get("turnOrder")));
        for (int i = 1; i <= count; i++) {
            var player = viewMap(viewMap(state.get("playerStats")).get("" + i));
            assertEquals(era.equals("rail") ? 20 : 10, player.get("money"));
            assertEquals(era.equals("rail") ? 777 : -10, player.get("lastIncome"));
            assertEquals(0, player.get("spentThisRound"));
        }
        assertEquals(true, state.get("eraEnding"));
        assertEquals(12 - count, state.get("round"));
        assertNull(state.get("turnStartSnapshot"));
        assertEquals(0, viewMap(state.get("turn")).get("actionsRemaining"));
        assertTrue(BrassTurnService.canMaintainEra(state));
        assertFalse(state.containsKey("pendingFinishedRound"));
    }

    @ParameterizedTest @ValueSource(ints = {2, 3, 4})
    void railPenultimateRoundStillCollectsIncomeAndBeginsTheFinalRound(int count) throws Exception {
        var state = flowState(count, "rail", 11 - count);
        for (var player : viewMap(state.get("playerStats")).values()) {
            viewMap(player).put("money", 20); viewMap(player).put("incomeLevel", 0);
        }
        makeEndable(state);
        BrassTurnService.endTurnAction(state, new UserSummary(count, "P" + count));
        assertEquals(12 - count, state.get("round"));
        assertFalse(Boolean.TRUE.equals(state.get("eraEnding")));
        assertEquals(10, viewMap(viewMap(state.get("playerStats")).get("1")).get("money"));
        assertEquals(2, viewMap(state.get("turn")).get("actionsAllowed"));
    }

    @Test void spendingTiesKeepPreviousOrderAndRawPlayerIdShape() throws Exception {
        var state = flowState(3, "canal", 2);
        state.put("turnOrder", new ArrayList<>(List.of("2", 1L, 3L)));
        viewMap(viewMap(state.get("playerStats")).get("1")).put("spentThisRound", 5);
        viewMap(viewMap(state.get("playerStats")).get("2")).put("spentThisRound", "5");
        BrassTurnService.reorderTurnOrderBySpending(state);
        assertEquals(List.of(3L, "2", 1L), state.get("turnOrder"));
        var positions = BrassTypedTurns.spendingOrder(List.of(2L, 1L), Map.of());
        assertEquals(List.of(0, 1), positions);
        assertThrows(UnsupportedOperationException.class, () -> positions.clear());
    }

    @ParameterizedTest @CsvSource({"0,0,0,10", "9,0,0,1", "10,0,0,0", "11,0,1,0", "20,10,20,0"})
    void incomePaymentAndExactZeroBoundary(int money, int level, int expectedMoney, int debt) {
        var result = BrassTypedIncome.settle(money, level);
        assertEquals(expectedMoney, result.money()); assertEquals(debt, result.debt());
        assertEquals(BrassRuleData.incomeForLevel(level), result.amount());
    }

    @Test void incomeKeepsUnknownStatsAndCreatesDebtsInOriginalStatsOrder() throws Exception {
        var state = flowState(2, "canal", 2);
        state.put("turnOrder", List.of(2L, 1L));
        var stats = viewMap(state.get("playerStats"));
        for (String id : List.of("1", "2")) {
            viewMap(stats.get(id)).put("money", "0"); viewMap(stats.get(id)).put("incomeLevel", "0");
            viewMap(stats.get(id)).put("extension", new LinkedHashMap<>(Collections.singletonMap("nullable", null)));
        }
        BrassTurnService.collectIncome(state);
        assertEquals(List.of(1L, 2L), listOf(state.get("incomeDebtQueue")).stream().map(d -> asLong(d.get("playerId"))).toList());
        assertEquals(10, listOf(state.get("incomeDebtQueue")).getFirst().get("amount"));
        assertEquals("0", viewMap(viewMap(state.get("playerStats")).get("1")).get("incomeLevel"));
        assertTrue(viewMap(viewMap(viewMap(state.get("playerStats")).get("1")).get("extension")).containsKey("nullable"));
    }

    @ParameterizedTest @CsvSource({"11,8,0,5,0,3", "10,3,0,5,2,0", "1,5,0,0,0,5", "-4,5,0,0,0,5", "10,8,3,5,0,0"})
    void liquidationHalvesCostAndKeepsOverpayment(int cost, int debt, int money, int proceeds, int cash, int remaining) {
        var result = BrassTypedDebt.liquidate(new com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.Debt(1, debt), 1,
                new BrassFlowModel.DebtAsset(1, cost), money);
        assertEquals(proceeds, result.proceeds()); assertEquals(cash, result.money()); assertEquals(remaining, result.remainingDebt());
    }

    @Test void partialDebtPreservesDebtMetadataThenConvertsUnpayableRemainderToVp() throws Exception {
        var state = debtState(2);
        state.put("incomeDebt", new LinkedHashMap<>(Map.of("playerId", 1L, "amount", 8, "extension", List.of("debt"))));
        var tiles = new ArrayList<>(List.of(asset("first", 1, 6), asset("second", 1, 2)));
        viewMap(state.get("board")).put("industries", tiles);
        BrassTurnService.resolveIncomeDebtAction(state, new UserSummary(1, "P1"), new BrassActionInput.Debt("first"));
        assertEquals(5, viewMap(state.get("incomeDebt")).get("amount"));
        assertEquals(List.of("debt"), viewMap(state.get("incomeDebt")).get("extension"));
        assertEquals(2, state.get("round")); assertNull(state.get("turnStartSnapshot"));
        BrassTurnService.resolveIncomeDebtAction(state, new UserSummary(1, "P1"), new BrassActionInput.Debt("second"));
        assertEquals(-4, viewMap(viewMap(state.get("playerStats")).get("1")).get("victoryPoints"));
        assertEquals(3, state.get("round")); assertTrue(viewMap(state.get("incomeDebt")).isEmpty());
        assertEquals(2, viewMap(state.get("turn")).get("actionsRemaining"));
    }

    @ParameterizedTest @ValueSource(ints = {2, 3, 4})
    void finalCanalIncomeDebtsMustAllFinishBeforeEraMaintenance(int count) throws Exception {
        var state = flowState(count, "canal", 12 - count);
        var tiles = new ArrayList<Map<String, Object>>();
        for (int i = 1; i <= count; i++) {
            var player = viewMap(viewMap(state.get("playerStats")).get("" + i));
            player.put("money", 0); player.put("incomeLevel", 0); player.put("spentThisRound", count - i);
            tiles.add(asset("debt-" + i, i, 20));
        }
        viewMap(state.get("board")).put("industries", tiles);
        makeEndable(state); BrassTurnService.endTurnAction(state, new UserSummary(count, "P" + count));
        for (int i = 1; i <= count; i++) {
            assertFalse(BrassTurnService.canMaintainEra(state));
            assertEquals((long) i, asLong(state.get("currentPlayerId")));
            assertEquals(12 - count, state.get("round"));
            assertEquals(0, viewMap(state.get("turn")).get("actionsAllowed"));
            assertNull(state.get("turnStartSnapshot"));
            BrassTurnService.resolveIncomeDebtAction(state, new UserSummary(i, "P" + i), new BrassActionInput.Debt("debt-" + i));
        }
        assertTrue(BrassTurnService.canMaintainEra(state));
        assertTrue(viewMap(state.get("incomeDebt")).isEmpty());
        assertTrue(listOf(state.get("incomeDebtQueue")).isEmpty());
        assertEquals((long) count, longList(state.get("turnOrder")).getFirst());
    }

    @Test void absentAssetsInQueuedDebtsDeductVpAndResumeSortedFirstPlayer() throws Exception {
        var state = debtState(2);
        state.put("turnOrder", List.of(2L, 1L));
        state.put("incomeDebt", Map.of("playerId", 1L, "amount", 3));
        state.put("incomeDebtQueue", List.of(Map.of("playerId", 2L, "amount", 5)));
        BrassTurnService.continueIncomeDebtResolution(state);
        assertEquals(-3, viewMap(viewMap(state.get("playerStats")).get("1")).get("victoryPoints"));
        assertEquals(-5, viewMap(viewMap(state.get("playerStats")).get("2")).get("victoryPoints"));
        assertEquals(3, state.get("round")); assertEquals(2L, asLong(state.get("currentPlayerId")));
        assertTrue(viewMap(state.get("incomeDebt")).isEmpty());
    }

    @Test void foreignDebtTileAndWrongActorLeaveStateUnchanged() throws Exception {
        var state = debtState(2);
        state.put("incomeDebt", Map.of("playerId", 1L, "amount", 5));
        viewMap(state.get("board")).put("industries", List.of(asset("other", 2, 20)));
        var before = reload(state);
        assertThrows(ApiException.class, () -> BrassTurnService.resolveIncomeDebtAction(state, new UserSummary(1, "P1"), new BrassActionInput.Debt("other")));
        assertThrows(ApiException.class, () -> BrassTurnService.resolveIncomeDebtAction(state, new UserSummary(2, "P2"), new BrassActionInput.Debt("other")));
        assertJsonEquals(before, state);
    }

    @ParameterizedTest @ValueSource(ints = {2, 3, 4})
    void railTransitionDealsCorrectlyAndPreservesRetainedIndustryAndAccountMetadata(int count) throws Exception {
        var state = flowState(count, "canal", 12 - count);
        var remaining = asset("higher-level", 1, 20); remaining.put("era", "canal"); remaining.put("level", 2);
        remaining.put("extension", List.of("retained")); remaining.put("coal", "2");
        var removed = asset("level-one", 1, 10); removed.put("era", "canal"); removed.put("level", 1);
        viewMap(state.get("board")).put("industries", List.of(removed, remaining));
        viewMap(state.get("board")).put("links", List.of(Map.of("from", "A", "to", "B", "type", "canal")));
        var order = java.util.stream.LongStream.rangeClosed(1, count).map(i -> count + 1 - i).boxed().toList();
        state.put("turnOrder", order);
        var merchant = new LinkedHashMap<String, Object>(Map.of("id", "old-merchant", "city", "Oxford",
                "providesBeer", true, "marketOpen", true, "beer", 0, "used", true, "extension", List.of("merchant")));
        viewMap(state.get("market")).put("beerMerchants", List.of(merchant));
        var money = viewMap(viewMap(state.get("playerStats")).get("1")).get("money");
        BrassEraService.transitionToRailEra(state);
        assertEquals("rail", state.get("era")); assertEquals(1, state.get("round"));
        assertEquals(order, longList(state.get("turnOrder")));
        assertEquals((long) count, asLong(state.get("currentPlayerId")));
        assertEquals(2, viewMap(state.get("turn")).get("actionsAllowed"));
        assertEquals(1, listOf(viewMap(state.get("board")).get("industries")).size());
        assertJsonEquals(remaining, listOf(viewMap(state.get("board")).get("industries")).getFirst());
        assertTrue(listOf(viewMap(state.get("board")).get("links")).isEmpty());
        assertEquals(money, viewMap(viewMap(state.get("playerStats")).get("1")).get("money"));
        assertEquals(1, listOf(viewMap(state.get("market")).get("beerMerchants")).getFirst().get("beer"));
        assertEquals(List.of("merchant"), listOf(viewMap(state.get("market")).get("beerMerchants")).getFirst().get("extension"));
        for (int i = 1; i <= count; i++) assertEquals(8, listOf(viewMap(state.get("hands")).get("" + i)).size());
        assertEquals(BrassCardService.buildEraDeck(count).size() - 8 * count, listOf(state.get("deck")).size());
        assertTrue(listOf(state.get("discardPile")).isEmpty());
        assertFalse(Boolean.TRUE.equals(state.get("eraEnding")));
    }

    @ParameterizedTest @CsvSource({"true,true,false,1", "true,true,true,0", "true,false,false,0", "false,true,false,0", "false,false,true,0"})
    void merchantBeerRestorationHonorsAllFlags(boolean provides, boolean open, boolean blank, int expected) {
        assertEquals(expected, BrassTypedEra.merchantBeer(provides, open, blank));
    }

    @Test void canalRemovalRetainsOtherErasUnknownTypesAndHigherLevels() {
        for (String type : BrassRuleData.CANAL_ERA_REMOVED_LEVEL_ONE_INDUSTRIES) {
            var tile = asset("tile", 1, 0); tile.put("era", "canal"); tile.put("industryType", type);
            assertTrue(BrassEraService.removedAfterCanalEra(tile)); // Missing level preserves the legacy default of one.
            tile.put("level", "1"); assertTrue(BrassEraService.removedAfterCanalEra(tile));
            tile.put("level", 2); assertFalse(BrassEraService.removedAfterCanalEra(tile));
            tile.put("level", 1); tile.put("era", "rail"); assertFalse(BrassEraService.removedAfterCanalEra(tile));
        }
        var future = asset("future", 1, 0); future.put("era", "canal"); future.put("industryType", "future-type");
        assertFalse(BrassEraService.removedAfterCanalEra(future));
    }

    static Map<String, Object> flowState(int count, String era, int round) throws Exception {
        var state = opening();
        var players = new ArrayList<Map<String, Object>>(); var stats = new LinkedHashMap<String, Object>();
        var hands = new LinkedHashMap<String, Object>(); var order = new ArrayList<Long>();
        for (int i = 1; i <= count; i++) {
            players.add(new LinkedHashMap<>(Map.of("userId", (long) i, "username", "P" + i, "seatIndex", i - 1, "color", "red")));
            stats.put("" + i, new LinkedHashMap<>(Map.of("money", 20, "incomeLevel", 10, "income", 0, "victoryPoints", 0, "spentThisRound", 0)));
            hands.put("" + i, new ArrayList<>()); order.add((long) i);
        }
        state.put("players", players); state.put("playerStats", stats); state.put("hands", hands);
        state.put("deck", new ArrayList<>()); state.put("turnOrder", order); state.put("turnIndex", count - 1); state.put("currentPlayerId", (long) count);
        state.put("era", era); state.put("round", round); state.put("eraEnding", false);
        state.put("incomeDebt", new LinkedHashMap<>()); state.put("incomeDebtQueue", new ArrayList<>());
        state.put("board", new LinkedHashMap<>(Map.of("industries", new ArrayList<>(), "links", new ArrayList<>())));
        state.put("turn", new LinkedHashMap<>(Map.of("actionsAllowed", 2, "actionsRemaining", 0, "actionsTaken", 2, "awaitingEndTurn", true, "startedAtRound", round)));
        return state;
    }
    static Map<String, Object> debtState(int round) throws Exception {
        var state = flowState(2, "canal", round);
        state.put("pendingFinishedRound", round); state.put("currentPlayerId", 1L);
        viewMap(viewMap(state.get("playerStats")).get("1")).put("money", 0);
        state.put("turnStartSnapshot", null); return state;
    }
    static Map<String, Object> asset(String id, long owner, int cost) {
        return new LinkedHashMap<>(Map.of("id", id, "ownerId", owner, "ownerName", "P" + owner, "city", "Birmingham", "industryType", "coal_mine", "cost", cost));
    }
    static void makeEndable(Map<String, Object> state) {
        var turn = mapOf(state.get("turn")); turn.put("actionsRemaining", 0); turn.put("actionsTaken", 2); turn.put("awaitingEndTurn", true); state.put("turn", turn);
    }
}
