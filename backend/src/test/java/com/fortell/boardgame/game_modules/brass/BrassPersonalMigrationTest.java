package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.Card;
import com.fortell.boardgame.models.ApiException;
import com.fortell.boardgame.models.UserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static com.fortell.boardgame.game_modules.brass.BrassCompatibilityFixtures.*;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;
import static org.junit.jupiter.api.Assertions.*;

class BrassPersonalMigrationTest {
    @Test void typedInitializationKeepsTheOldSparseWireShapeAndHintGrouping() {
        var players = BrassSetupData.playersOf(SEATS);
        assertEquals(Set.of("seatIndex", "userId", "username", "color"), players.getFirst().keySet());
        assertEquals(1L, players.getFirst().get("userId"));
        Map<String, Object> stats = viewMap(BrassSetupData.initialPlayerStats(players).get("1"));
        assertEquals(Set.of("money", "incomeLevel", "income", "victoryPoints", "spentThisRound", "estimatedEraEndScore"), stats.keySet());
        Map<String, Object> first = normal("first"); first.put("displayName", "first display");
        Map<String, Object> second = normal("second"); second.put("displayName", "second display");
        List<Map<String, Object>> hints = listOf(BrassCardService.initialCardHints(List.of(first, second, wild("unused", "wild_industry"))).get("cards"));
        assertEquals(List.of(Map.of("key", "location:Birmingham", "name", "first display", "total", 2, "remaining", 2)), hints);
        Map<String, Object> created = BrassCardService.card("location", "Birmingham", "伯明翰");
        assertEquals(Set.of("id", "type", "key", "name"), created.keySet());
        assertTrue(created.get("id").toString().startsWith("location_Birmingham_"));
        Map<String, Object> wild = BrassCardService.wildCard("wild_location", "万能地点牌");
        assertEquals(Set.of("id", "type", "key", "name", "wild"), wild.keySet());
        assertEquals(true, wild.get("wild"));
    }
    @ParameterizedTest @ValueSource(strings = {"skip", "loan", "scout"})
    void wrongActorAndFinishedActionsCannotChangePersonalState(String action) throws Exception {
        Map<String, Object> state = state();
        Map<String, Object> payload = action.equals("scout") ? Map.of("cardIds", List.of("a", "b", "c")) : Map.of("cardId", "a");
        String before = JSON.writeValueAsString(state);
        assertThrows(ApiException.class, () -> MODULE.onTransition(ROOM, SEATS, MODULE.defaultConfig(), state,
                new UserSummary(2, "B"), action, payload, List.of()));
        assertEquals(before, JSON.writeValueAsString(state));
        viewMap(state.get("turn")).put("awaitingEndTurn", true);
        String after = JSON.writeValueAsString(state);
        assertThrows(ApiException.class, () -> act(state, Map.of("type", action, "payload", payload)));
        assertEquals(after, JSON.writeValueAsString(state));
    }

    @ParameterizedTest @ValueSource(strings = {"", "not-owned"})
    void skipRejectsMissingOrUnownedCardsWithoutMutation(String id) throws Exception {
        Map<String, Object> state = state(); String before = JSON.writeValueAsString(state);
        assertThrows(ApiException.class, () -> act(state, Map.of("type", "skip", "payload", Map.of("cardId", id))));
        assertEquals(before, JSON.writeValueAsString(state));
    }

    @Test void skipRetainsCardExtensionsUpdatesOnlyItsHintAndConsumesOneAction() throws Exception {
        Map<String, Object> state = state();
        Map<String, Object> card = mapOf(deepCopy(BrassCardService.findCard(BrassCardService.currentHand(state, 1), "a")));
        String before = JSON.writeValueAsString(state);
        var result = act(state, Map.of("type", "skip", "payload", Map.of("cardId", "a")));
        assertEquals(before, JSON.writeValueAsString(state));
        assertEquals(card, listOf(result.state().get("discardPile")).getLast());
        assertEquals(1, viewMap(result.state().get("turn")).get("actionsTaken"));
        assertEquals(1, viewMap(result.state().get("turn")).get("actionsRemaining"));
        assertEquals(1, listOf(viewMap(result.state().get("cardHints")).get("cards")).getFirst().get("remaining"));
        assertEquals("skip", result.event().get("type"));
        assertEquals(state.get("turnStartSnapshot"), result.state().get("turnStartSnapshot"));
    }

    @Test void wildSkipReturnsTheSameCardAndDoesNotChangeDiscardOrHints() throws Exception {
        Map<String, Object> state = state();
        Map<String, Object> wild = BrassCardService.findCard(BrassCardService.currentHand(state, 1), "wild-held");
        var result = act(state, Map.of("type", "skip", "payload", Map.of("cardId", "wild-held")));
        assertEquals(wild, listOf(result.state().get("scoutPool")).getLast());
        assertEquals(state.get("discardPile"), result.state().get("discardPile"));
        assertEquals(state.get("cardHints"), result.state().get("cardHints"));
    }

    @Test void scoutingPreservesSelectionOrderCardMetadataOtherHandsAndExistingHintBehavior() throws Exception {
        Map<String, Object> state = state();
        viewMap(state.get("hands")).put("2", null);
        List<Map<String, Object>> pool = listOf(state.get("scoutPool"));
        var result = act(state, Map.of("type", "scout", "payload", Map.of("cardIds", List.of("c", "a", "b"))));
        assertEquals(List.of("c", "a", "b"), listOf(result.state().get("discardPile")).stream().map(card -> card.get("id")).toList());
        List<Map<String, Object>> hand = BrassCardService.currentHand(result.state(), 1);
        assertEquals(List.of("wild-held", "pool-industry", "pool-location"), hand.stream().map(card -> card.get("id")).toList());
        assertEquals(pool.getFirst(), hand.get(1)); assertEquals(pool.get(1), hand.get(2));
        assertNull(viewMap(result.state().get("hands")).get("2"));
        assertEquals(state.get("cardHints"), result.state().get("cardHints"));
        assertEquals(1, viewMap(result.state().get("turn")).get("actionsTaken"));
        assertEquals("scout", result.event().get("type"));
        assertJsonEquals(result.state(), reload(result.state()));
    }

    @ParameterizedTest @ValueSource(strings = {"duplicate", "missing", "too-few", "wild", "pool-empty", "pool-type-mismatch"})
    void failedScoutingNeverLeavesPartialCardsOrPoolChanges(String kind) throws Exception {
        Map<String, Object> state = state();
        List<String> ids = switch (kind) {
            case "duplicate" -> List.of("a", "a", "b");
            case "missing" -> List.of("a", "b", "absent");
            case "too-few" -> List.of("a", "b");
            case "wild" -> List.of("a", "b", "wild-held");
            default -> List.of("a", "b", "c");
        };
        if (kind.equals("pool-empty")) state.put("scoutPool", List.of());
        if (kind.equals("pool-type-mismatch")) {
            // Existing availability checks key, while actually taking a card checks type.
            ((Map<String, Object>) ((List<?>) state.get("scoutPool")).get(1)).put("type", "not-location");
        }
        String before = JSON.writeValueAsString(state);
        assertThrows(ApiException.class, () -> BrassScoutAction.scoutAction(state, new UserSummary(1, "A"),
                BrassActionCommand.decode("scout", Map.of("cardIds", ids))));
        assertEquals(before, JSON.writeValueAsString(state));
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 999})
    void blockedLoanDoesNotDiscardOrChangeAssets(int level) throws Exception {
        Map<String, Object> state = state();
        viewMap(viewMap(state.get("playerStats")).get("1")).put("incomeLevel", level);
        String before = JSON.writeValueAsString(state);
        assertThrows(ApiException.class, () -> BrassLoanAction.loanAction(state, new UserSummary(1, "A"),
                BrassActionCommand.decode("loan", Map.of("cardId", "a"))));
        assertEquals(before, JSON.writeValueAsString(state));
    }

    @Test void loanKeepsTheEstablishedMissingIncomeLevelDefaultAndUnknownFields() throws Exception {
        Map<String, Object> state = state();
        Map<String, Object> stats = viewMap(viewMap(state.get("playerStats")).get("1"));
        stats.put("incomeLevel", null); stats.put("money", "17"); stats.put("futureStats", Arrays.asList(1, null));
        var result = act(state, Map.of("type", "loan", "payload", Map.of("cardId", "a")));
        Map<String, Object> after = viewMap(viewMap(result.state().get("playerStats")).get("1"));
        int expectedLevel = BrassRuleData.INCOME_LEVEL_AFTER_LOAN.get(BrassRuleData.STARTING_INCOME_LEVEL);
        assertEquals(47, after.get("money")); assertEquals(expectedLevel, after.get("incomeLevel"));
        assertEquals(BrassRuleData.incomeForLevel(expectedLevel), after.get("income"));
        assertEquals(stats.get("futureStats"), after.get("futureStats"));
        assertEquals(stats.get("spentThisRound"), after.get("spentThisRound"));
        assertEquals("loan", result.event().get("type"));
    }

    @Test void loanWithWrongCardFailsWithoutChangingAnything() throws Exception {
        Map<String, Object> state = state(); String before = JSON.writeValueAsString(state);
        assertThrows(ApiException.class, () -> BrassLoanAction.loanAction(state, new UserSummary(1, "A"),
                BrassActionCommand.decode("loan", Map.of("cardId", "not-owned"))));
        assertEquals(before, JSON.writeValueAsString(state));
    }

    @Test void drawsAreOrderedBoundedAndRetainMetadataForEverySource() throws Exception {
        Map<String, Object> state = state();
        Map<String, Object> first = normal("deck-first"), second = normal("deck-second");
        state.put("deck", new ArrayList<>(List.of(first, second)));
        Object pool = deepCopy(state.get("scoutPool")); Object hints = deepCopy(state.get("cardHints"));
        BrassCardService.drawCards(state, 1, 10);
        List<Map<String, Object>> hand = BrassCardService.currentHand(state, 1);
        assertEquals(first, hand.get(hand.size() - 2)); assertEquals(second, hand.getLast());
        assertTrue(listOf(state.get("deck")).isEmpty());
        assertEquals(pool, state.get("scoutPool")); assertEquals(hints, state.get("cardHints"));
        String before = JSON.writeValueAsString(state);
        BrassCardService.drawHandToLimit(state, 1, 3);
        assertEquals(before, JSON.writeValueAsString(state));
    }

    @Test void cardWritingIsAtomicAndModelsCannotMutateInputOrOtherRooms() throws Exception {
        Map<String, Object> state = state(), other = reload(state);
        var session = BrassPersonalAdapter.cards(state, 1);
        assertThrows(UnsupportedOperationException.class, () -> session.value().hand(1).clear());
        var original = session.value();
        Card untracked = new Card("untracked", "location", "Birmingham", null, null, null, null, List.of(), false);
        var invalid = new BrassTypedCards.State(original.hands(), original.deck(), List.of(), List.of(untracked), original.hints());
        String before = JSON.writeValueAsString(state);
        assertThrows(IllegalStateException.class, () -> session.apply(new BrassTypedCards.Change(invalid,
                EnumSet.of(BrassTypedCards.Field.POOL, BrassTypedCards.Field.DISCARD), null)));
        assertEquals(before, JSON.writeValueAsString(state));
        var valid = BrassTypedCards.discard(original, 1, "a");
        assertEquals(before, JSON.writeValueAsString(state));
        session.apply(valid);
        assertEquals(before, JSON.writeValueAsString(other));
    }

    @Test void assetUpdatesKeepSparseShapeCapIncomeAndAllowNegativeScores() {
        Map<String, Object> stats = new LinkedHashMap<>(); stats.put("money", null); stats.put("future", "kept");
        Map<String, Object> state = new LinkedHashMap<>(Map.of("playerStats", new LinkedHashMap<>(Map.of("1", stats))));
        BrassPlayerService.gainMoney(state, 1, 0);
        assertEquals(Map.of("money", 0, "future", "kept"), viewMap(viewMap(state.get("playerStats")).get("1")));
        BrassPlayerService.addVictoryPoints(state, 1, -2);
        assertEquals(-2, viewMap(viewMap(state.get("playerStats")).get("1")).get("victoryPoints"));
        viewMap(viewMap(state.get("playerStats")).get("1")).put("incomeLevel", 98);
        BrassPlayerService.addIncome(state, 1, 50);
        assertEquals(99, viewMap(viewMap(state.get("playerStats")).get("1")).get("incomeLevel"));
        BrassPlayerService.addIncome(state, 1, -10);
        assertEquals(99, viewMap(viewMap(state.get("playerStats")).get("1")).get("incomeLevel"));
        assertThrows(ApiException.class, () -> BrassPlayerService.payMoney(state, 1, 1));
        assertFalse(viewMap(viewMap(state.get("playerStats")).get("1")).containsKey("spentThisRound"));
    }

    @Test void supplyNeverSkipsABlockedFirstTileAndPreservesOwnersWithIdenticalIds() throws Exception {
        Map<String, Object> state = state();
        Map<String, Object> blocked = new LinkedHashMap<>(Map.of("id", "shared-id", "industryType", "pottery", "level", 1,
                "period", 2, "canDevelop", false, "future", "owner-one"));
        Map<String, Object> second = new LinkedHashMap<>(Map.of("id", "second", "industryType", "pottery", "level", 2, "period", 0));
        viewMap(viewMap(state.get("playerBoards")).get("1")).put("pottery", new ArrayList<>(List.of(blocked, second)));
        viewMap(viewMap(state.get("playerBoards")).get("2")).put("pottery", List.of(new LinkedHashMap<>(Map.of("id", "shared-id", "future", "owner-two"))));
        String before = JSON.writeValueAsString(state);
        assertThrows(ApiException.class, () -> BrassIndustryService.nextAvailableBoardTile(state, 1, "pottery"));
        assertTrue(BrassIndustryService.lowestDevelopableBoardTile(state, 1, "pottery").isEmpty());
        assertThrows(ApiException.class, () -> BrassIndustryService.lowestDevelopableBoardTileOrThrow(state, 1, "pottery"));
        assertThrows(ApiException.class, () -> BrassIndustryService.removeBoardTile(state, 1, "pottery", "absent"));
        assertEquals(before, JSON.writeValueAsString(state));
        BrassIndustryService.removeBoardTile(state, 1, "pottery", "shared-id");
        BrassIndustryService.incrementDevelopment(state, 1, "pottery");
        assertEquals(second, BrassIndustryService.lowestDevelopableBoardTile(state, 1, "pottery"));
        assertEquals("owner-two", BrassIndustryService.playerBoardTiles(state, 2, "pottery").getFirst().get("future"));
        assertEquals(1, viewMap(viewMap(state.get("developments")).get("1")).get("pottery"));
    }

    @Test void missingSupplyPeriodAndCanDevelopRetainLegacyDefaults() throws Exception {
        Map<String, Object> state = state();
        Map<String, Object> tile = new LinkedHashMap<>(Map.of("id", "legacy", "industryType", "coal_mine", "future", List.of(1, 2)));
        tile.put("canDevelop", null); tile.put("period", null);
        viewMap(viewMap(state.get("playerBoards")).get("1")).put("coal_mine", List.of(tile));
        assertEquals(tile, BrassIndustryService.nextAvailableBoardTile(state, 1, "coal_mine"));
        assertEquals(tile, BrassIndustryService.lowestDevelopableBoardTile(state, 1, "coal_mine"));
        state.put("era", "rail");
        assertEquals(tile, BrassIndustryService.nextAvailableBoardTile(state, 1, "coal_mine"));
        assertTrue(BrassIndustryService.lowestDevelopableBoardTile(state, 1, "not-present").isEmpty());
    }

    @Test void supplyPeriodChecksDoNotDependOnDisplayEraOrPriceFields() {
        for (int period : List.of(0, 1, 2)) {
            Map<String, Object> tile = Map.of("period", String.valueOf(period), "era", "future-display-era", "cost", "not-a-price");
            assertEquals(period != 2, BrassIndustryService.boardTileBuildableInEra(tile, "canal"));
            assertEquals(period != 1, BrassIndustryService.boardTileBuildableInEra(tile, "rail"));
        }
    }

    @Test void typedCardViewsSupportSparseLegacyModelValues() {
        Card sparse = new Card(null, null, null, null, null, null, null, List.of(), false);
        assertFalse(BrassTypedCards.wild(sparse)); assertEquals("", BrassTypedCards.hintKey(sparse));
        assertSame(sparse, BrassTypedCards.find(List.of(sparse), ""));
    }

    static Map<String, Object> state() throws Exception {
        Map<String, Object> state = opening();
        state.put("round", 2); state.put("currentPlayerId", 1L); state.put("turnIndex", 0); state.put("turnOrder", List.of(1L, 2L));
        state.put("turn", new LinkedHashMap<>(Map.of("actionsAllowed", 2, "actionsRemaining", 2, "actionsTaken", 0, "awaitingEndTurn", false, "startedAtRound", 2)));
        viewMap(state.get("hands")).put("1", new ArrayList<>(List.of(normal("a"), normal("b"), normal("c"), wild("wild-held", "wild_location"))));
        state.put("scoutPool", new ArrayList<>(List.of(wild("pool-industry", "wild_industry"), wild("pool-location", "wild_location"))));
        state.put("discardPile", new ArrayList<>());
        state.put("cardHints", new LinkedHashMap<>(Map.of("cards", new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("key", "location:Birmingham", "name", "伯明翰", "total", 3, "remaining", 2)))))));
        state.put("futureRoot", Arrays.asList("keep", null));
        BrassStateProjection.updateMaintenanceFlag(state);
        state.put("turnStartSnapshot", BrassTurnService.snapshotOf(state));
        return state;
    }
    static Map<String, Object> normal(String id) {
        Map<String, Object> card = new LinkedHashMap<>(Map.of("id", id, "type", "location", "key", "Birmingham", "name", "伯明翰"));
        card.put("futureCard", Arrays.asList(id, null)); return card;
    }
    static Map<String, Object> wild(String id, String type) {
        Map<String, Object> card = new LinkedHashMap<>(Map.of("id", id, "type", type, "key", type, "wild", true, "name", type));
        card.put("futureCard", Arrays.asList(id, null)); return card;
    }
}
