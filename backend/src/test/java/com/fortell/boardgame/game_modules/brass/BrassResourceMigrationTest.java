package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static com.fortell.boardgame.game_modules.brass.BrassResourceModel.*;
import static com.fortell.boardgame.game_modules.brass.BrassCompatibilityFixtures.*;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;
import static org.junit.jupiter.api.Assertions.*;

class BrassResourceMigrationTest {
    @ParameterizedTest @ValueSource(strings = {"coal", "iron"})
    void freeSourcesAreMandatoryAndCanBeSplitAcrossOwnersBeforeMarket(String type) throws Exception {
        var state = state();
        var need = new ResourceRequirement(type, 3, List.of("Birmingham"), null, "");
        var choices = List.of(select(type, "industry", type + "-own", 0, 1),
                select(type, "industry", type + "-other", 0, 1), select(type, "market", "market:" + type, 0, 1));
        var before = reload(state);
        var plan = validate(state, List.of(need), choices);
        assertEquals(3, plan.size());
        assertThrows(ApiException.class, () -> validate(state, List.of(need), List.of(select(type, "market", "market:" + type, 0, 3))));
        assertJsonEquals(before, state);
        var result = mapOf(deepCopy(state));
        BrassResourceSelectionService.consumeExplicitResources(result, 1, List.of(need), payload(choices), "");
        assertEquals(0, stock(result, type + "-own", type));
        assertEquals(0, stock(result, type + "-other", type));
        assertEquals(1, objectList(viewMap(result.get("market")).get(type)).size());
    }

    @ParameterizedTest @ValueSource(strings = {"coal", "iron"})
    void anyFreeOwnerCanBeChosenWhenDemandIsSmallerThanSupply(String type) throws Exception {
        var state = state();
        assertEquals(type + "-other", validate(state, List.of(new ResourceRequirement(type, 1, List.of("Birmingham"), null, "")),
                List.of(select(type, "industry", type + "-other", 0, 1))).getFirst().source().id());
    }

    @Test void unreachableCoalDoesNotBlockMarketButRemoteIronDoes() throws Exception {
        var state = state();
        var board = viewMap(state.get("board"));
        var tiles = listOf(board.get("industries"));
        for (var tile : tiles) if (Objects.toString(tile.get("industryType")).equals("coal_mine")) tile.put("city", "Isolated");
        board.put("industries", tiles);
        assertEquals(SourceType.MARKET, validate(state, List.of(new ResourceRequirement("coal", 1, List.of("Birmingham"), null, "")),
                List.of(select("coal", "market", "market:coal", 0, 1))).getFirst().source().kind());
        assertThrows(ApiException.class, () -> validate(state, List.of(new ResourceRequirement("iron", 1, List.of(), null, "")),
                List.of(select("iron", "market", "market:iron", 0, 1))));
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "null", "object", "null-item", "zero", "negative", "bad-type", "bad-kind", "empty-id", "bad-index", "wrong-resource", "missing-source", "too-many", "duplicate"})
    void invalidSelectionsAreRejectedBeforeAnyConsumption(String scenario) throws Exception {
        var state = state(); var before = reload(state);
        var need = List.of(new ResourceRequirement("iron", 1, List.of(), null, ""));
        var item = new LinkedHashMap<>(wire(select("iron", "industry", "iron-own", 0, 1)));
        var payload = new LinkedHashMap<String, Object>();
        payload.put("resourceSelections", List.of(item));
        switch (scenario) {
            case "missing" -> payload.clear();
            case "null" -> payload.put("resourceSelections", null);
            case "object" -> payload.put("resourceSelections", Map.of());
            case "null-item" -> payload.put("resourceSelections", Arrays.asList((Object) null));
            case "zero" -> item.put("amount", 0);
            case "negative" -> item.put("amount", -1);
            case "bad-type" -> item.put("type", "money");
            case "bad-kind" -> item.put("sourceType", "bank");
            case "empty-id" -> item.put("sourceId", " ");
            case "bad-index" -> item.put("requirementIndex", -1);
            case "wrong-resource" -> item.put("type", "beer");
            case "missing-source" -> item.put("sourceId", "not-there");
            case "too-many" -> item.put("amount", 2);
            case "duplicate" -> payload.put("resourceSelections", List.of(item, item));
        }
        assertThrows(ApiException.class, () -> BrassResourceSelectionService.consumeExplicitResources(state, 1, need, payload, ""));
        assertJsonEquals(before, state);
    }

    @Test void amountAggregationDoesNotWrapAtIntegerBoundary() throws Exception {
        var state = state();
        var needs = List.of(new ResourceRequirement("iron", 1, List.of(), null, ""));
        var error = assertThrows(ApiException.class, () -> validate(state, needs,
                List.of(select("iron", "industry", "iron-own", 0, Integer.MAX_VALUE),
                        select("iron", "industry", "iron-other", 0, Integer.MAX_VALUE), select("iron", "industry", "iron-own", 0, 3))));
        assertTrue(error.getMessage().contains("4294967297"));
    }

    @Test void duplicateSelectionsWithinStockAreAllowedButCannotOverdrawAcrossNeeds() throws Exception {
        var state = state();
        var tiles = listOf(viewMap(state.get("board")).get("industries"));
        find(tiles, "iron-own").put("iron", 2);
        viewMap(state.get("board")).put("industries", tiles);
        var split = List.of(select("iron", "industry", "iron-own", 0, 1), select("iron", "industry", "iron-own", 0, 1));
        assertEquals(2, validate(state, List.of(new ResourceRequirement("iron", 2, List.of(), null, "")), split).size());
        assertThrows(ApiException.class, () -> validate(state, List.of(new ResourceRequirement("iron", 2, List.of(), null, ""),
                        new ResourceRequirement("iron", 1, List.of(), null, "")),
                List.of(select("iron", "industry", "iron-own", 0, 2), select("iron", "industry", "iron-own", 1, 1))));
    }

    @Test void ownOtherAndMerchantBeerCanBeCombinedWithoutPriority() throws Exception {
        var state = state(); var before = reload(state);
        var need = List.of(new ResourceRequirement("beer", 3, List.of(), "Oxford", "cotton_mill"));
        var choices = mixedBeer();
        assertEquals(3, validate(state, need, choices).size());
        var notices = BrassResourceSelectionService.consumeExplicitResources(state, 1, need, payload(choices), "");
        assertEquals(3, notices.size());
        assertEquals(0, stock(state, "beer-own", "beer"));
        assertEquals(0, stock(state, "beer-other", "beer"));
        assertEquals(0, find(listOf(viewMap(state.get("market")).get("beerMerchants")), "merchant-a").get("beer"));
        assertEquals(true, find(listOf(viewMap(state.get("market")).get("beerMerchants")), "merchant-a").get("used"));
        // Resource consumption alone must not grant the sale's merchant bonus.
        assertEquals(10, viewMap(viewMap(state.get("playerStats")).get("1")).get("incomeLevel"));
        assertJsonEquals(viewMap(before.get("board")).get("extension"), viewMap(state.get("board")).get("extension"));
    }

    @ParameterizedTest @ValueSource(strings = {"other-disconnected", "merchant-wrong-type", "merchant-blank", "merchant-empty", "market-beer", "merchant-in-network-action"})
    void inaccessibleOrWrongBeerSourcesAreRejected(String scenario) throws Exception {
        var state = state(); var need = new ResourceRequirement("beer", 1, List.of(), "Oxford", "cotton_mill");
        var choice = select("beer", "merchant", "merchant-a", 0, 1);
        var merchant = find(listOf(viewMap(state.get("market")).get("beerMerchants")), "merchant-a");
        switch (scenario) {
            case "other-disconnected" -> {
                viewMap(state.get("board")).put("links", List.of());
                choice = select("beer", "industry", "beer-other", 0, 1);
            }
            case "merchant-wrong-type" -> merchant.put("acceptedIndustryTypes", List.of("pottery"));
            case "merchant-blank" -> merchant.put("blank", true);
            case "merchant-empty" -> merchant.put("beer", 0);
            case "market-beer" -> choice = select("beer", "market", "market:beer", 0, 1);
            case "merchant-in-network-action" -> need = new ResourceRequirement("beer", 1, List.of("Birmingham"), null, "");
        }
        viewMap(state.get("market")).put("beerMerchants", List.of(merchant));
        var finalNeed = need; var finalChoice = choice; var before = reload(state);
        assertThrows(ApiException.class, () -> validate(state, List.of(finalNeed), List.of(finalChoice)));
        assertJsonEquals(before, state);
    }

    @Test void merchantMustMatchChosenSaleMarketEvenIfBothAcceptAndAreConnected() throws Exception {
        var choices = List.of(select("beer", "merchant", "another-market", 0, 1));
        assertThrows(ApiException.class, () -> BrassTypedSale.validateMerchants(choices, "merchant-a"));
        assertDoesNotThrow(() -> BrassTypedSale.validateMerchants(mixedBeer(), "merchant-a"));
    }

    @Test void sourceBoardStagesAreImmutableAndMarketCoalCannotUseFutureConnectivity() throws Exception {
        var state = state();
        var firstRaw = mapOf(deepCopy(state.get("board"))); firstRaw.put("links", List.of(link("Birmingham", "Dudley")));
        var secondRaw = mapOf(deepCopy(firstRaw)); secondRaw.put("links", List.of(link("Birmingham", "Dudley"), link("Dudley", "Oxford")));
        var first = BrassResourceAdapter.board(firstRaw); var second = BrassResourceAdapter.board(secondRaw);
        var need = new ResourceRequirement(ResourceType.COAL, 3, List.of("Birmingham"), null, "", first);
        var choices = List.of(select("coal", "industry", "coal-own", 0, 1), select("coal", "industry", "coal-other", 0, 1),
                select("coal", "market", "market:coal", 0, 1));
        assertThrows(ApiException.class, () -> validate(state, List.of(need), choices));
        assertEquals(3, validate(state, List.of(new ResourceRequirement(ResourceType.COAL, 3, List.of("Birmingham"), null, "", second)), choices).size());
        firstRaw.put("links", secondRaw.get("links"));
        assertFalse(BrassTypedConnectivity.connected(first.network(), "Birmingham", "Oxford"));
        assertThrows(UnsupportedOperationException.class, () -> first.industries().clear());
        assertThrows(UnsupportedOperationException.class, () -> need.destinations().clear());
    }

    @Test void doubleStageAllocationsReserveFreeCoalBeforeSecondMarketPurchase() throws Exception {
        var state = state();
        var board = viewMap(state.get("board"));
        var tiles = listOf(board.get("industries")); find(tiles, "coal-other").put("coal", 0); board.put("industries", tiles);
        var firstRaw = mapOf(deepCopy(board)); firstRaw.put("links", List.of(link("Birmingham", "Dudley")));
        var stages = List.of(BrassResourceAdapter.board(firstRaw), BrassResourceAdapter.board(board));
        var needs = BrassTypedNetwork.requirements("rail", List.of(new BrassTypedNetwork.RouteSpec("Birmingham", "Dudley"),
                new BrassTypedNetwork.RouteSpec("Dudley", "Oxford")), stages);
        var choices = List.of(select("coal", "industry", "coal-own", 0, 1), select("coal", "market", "market:coal", 1, 1),
                select("beer", "industry", "beer-own", 2, 1));
        assertEquals(3, validate(state, needs, choices).size());
        var duplicated = List.of(choices.get(0), select("coal", "industry", "coal-own", 1, 1), choices.get(2));
        assertThrows(ApiException.class, () -> validate(state, needs, duplicated));
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3})
    void twoRailRequirementsCanShareOneMineOnlyWithinItsStock(int available) throws Exception {
        var state = state();
        var board = viewMap(state.get("board"));
        var tiles = listOf(board.get("industries"));
        find(tiles, "coal-own").put("coal", available);
        find(tiles, "coal-other").put("coal", 0);
        board.put("industries", tiles);
        var firstRaw = mapOf(deepCopy(board)); firstRaw.put("links", List.of(link("Birmingham", "Dudley")));
        var stages = List.of(BrassResourceAdapter.board(firstRaw), BrassResourceAdapter.board(board));
        var needs = BrassTypedNetwork.requirements("rail", List.of(new BrassTypedNetwork.RouteSpec("Birmingham", "Dudley"),
                new BrassTypedNetwork.RouteSpec("Dudley", "Oxford")), stages);
        var choices = List.of(select("coal", "industry", "coal-own", 0, 1),
                select("coal", "industry", "coal-own", 1, 1), select("beer", "industry", "beer-own", 2, 1));
        var before = reload(state);
        if (available == 1) {
            assertThrows(ApiException.class, () -> BrassResourceSelectionService.consumeExplicitResources(state, 1, needs, payload(choices), ""));
            assertJsonEquals(before, state);
        } else {
            assertEquals(3, validate(state, needs, choices).size());
            assertJsonEquals(before, state);
            BrassResourceSelectionService.consumeExplicitResources(state, 1, needs, payload(choices), "");
            assertEquals(available - 2, stock(state, "coal-own", "coal"));
            assertEquals(0, stock(state, "beer-own", "beer"));
            assertEquals(viewMap(before.get("market")), viewMap(state.get("market")));
        }
    }

    @Test void emptyMarketPricesAndUnconsumedRawValuesArePreserved() throws Exception {
        var state = state();
        viewMap(state.get("market")).put("iron", new ArrayList<>(List.of("2", 3, "4")));
        BrassResourceService.consumeMarketResource(state, 1, "iron", 1);
        assertEquals(List.of(3, "4"), viewMap(state.get("market")).get("iron"));
        viewMap(state.get("market")).put("coal", List.of());
        BrassResourceService.consumeMarketResource(state, 1, "coal", 2);
        assertEquals(82, viewMap(viewMap(state.get("playerStats")).get("1")).get("money"));
        var quote = BrassTypedResources.purchase(List.of(1, 2), 4, 8);
        assertEquals(List.of(1, 2, 8, 8), quote.prices()); assertEquals(19, quote.totalCost());
    }

    @Test void restockingMatchesPriceTrackAtFullEmptyAndPartialBoundaries() {
        var track = BrassRuleData.FULL_IRON_MARKET;
        for (int size = 0; size <= track.size(); size++) for (int stock = 0; stock <= 5; stock++) {
            var result = BrassTypedResources.restock(stock, size, track.size(), track);
            assertEquals(Math.min(stock, track.size() - size), result.prices().size());
            assertEquals(stock - result.prices().size(), result.remaining());
            for (int i = 0; i < result.prices().size(); i++)
                assertEquals(track.get(Math.max(0, track.size() - size - i - 1)), result.prices().get(i));
        }
    }

    @Test void versionedCommandsRetainAliasesAndDefaultRequirementIndex() {
        var canonical = BrassResourceAdapter.selections(Map.of("resourceSelections", List.of(Map.of("type", "iron", "sourceType", "market", "sourceId", "market:iron", "amount", 1))));
        var alias = BrassResourceAdapter.selections(Map.of("resourceSelections", List.of(Map.of("resourceType", "iron", "sourceType", "market", "sourceId", "market:iron", "amount", "1"))));
        assertEquals(canonical, alias);
        assertEquals(0, alias.getFirst().requirementIndex());
        assertThrows(UnsupportedOperationException.class, () -> alias.clear());
    }

    @Test void twoConsecutiveDevelopActionsUseExplicitIronAndSurviveReload() throws Exception {
        var state = actionState();
        var tiles = listOf(viewMap(state.get("board")).get("industries"));
        find(tiles, "iron-own").put("iron", 4); find(tiles, "iron-other").put("iron", 0);
        viewMap(state.get("board")).put("industries", tiles);
        var before = reload(state);
        var first = act(state, command("develop", Map.of("cardId", "card-a", "industryTypes", List.of("cotton_mill", "cotton_mill"),
                "resourceSelections", payload(List.of(select("iron", "industry", "iron-own", 0, 2))).get("resourceSelections"))));
        var second = act(reload(first.state()), command("develop", Map.of("cardId", "card-b", "industryTypes", List.of("cotton_mill", "cotton_mill"),
                "resourceSelections", payload(List.of(select("iron", "industry", "iron-own", 0, 2))).get("resourceSelections"))));
        assertEquals(4, viewMap(viewMap(second.state().get("developments")).get("1")).get("cotton_mill"));
        assertEquals(0, stock(second.state(), "iron-own", "iron"));
        assertEquals(true, viewMap(second.state().get("turn")).get("awaitingEndTurn"));
        assertJsonEquals(before, state);
    }

    @Test void buildUsesCoalAndRemoteIronWithoutChangingInterfaceOrSparseSourceMetadata() throws Exception {
        var state = actionState(); var before = reload(state);
        var boards = viewMap(viewMap(state.get("playerBoards")).get("1"));
        var stack = listOf(boards.get("manufacturer"));
        stack.getFirst().put("ironCost", 1); boards.put("manufacturer", stack);
        var result = act(state, command("build", Map.of("cardId", "card-a", "city", "Birmingham", "industryType", "manufacturer",
                "resourceSelections", payload(List.of(select("coal", "industry", "coal-other", 0, 1), select("iron", "industry", "iron-other", 1, 1))).get("resourceSelections")))).state();
        assertEquals(0, stock(result, "coal-other", "coal"));
        assertEquals(0, stock(result, "iron-other", "iron"));
        assertEquals(92, viewMap(viewMap(result.get("playerStats")).get("1")).get("money"));
        assertEquals(List.of("kept"), find(listOf(viewMap(result.get("board")).get("industries")), "iron-other").get("extension"));
        assertJsonEquals(before.get("market"), result.get("market"));
        assertJsonEquals(result, reload(result));
    }

    @Test void saleCombinesThreeBeerKindsAndCanResumeUntilEndSell() throws Exception {
        var state = saleState();
        var before = reload(state);
        var sold = act(state, command("sell", Map.of("cardId", "card-a", "tileId", "sale", "merchantId", "merchant-a",
                "resourceSelections", payload(mixedBeer()).get("resourceSelections"))));
        assertEquals(0, stock(sold.state(), "beer-own", "beer")); assertEquals(0, stock(sold.state(), "beer-other", "beer"));
        assertEquals(true, find(listOf(viewMap(sold.state().get("board")).get("industries")), "sale").get("flipped"));
        assertEquals(true, viewMap(sold.state().get("turn")).get("sellInProgress"));
        assertEquals(0, viewMap(sold.state().get("turn")).get("actionsTaken"));
        var ended = act(reload(sold.state()), command("end_sell", Map.of())).state();
        assertEquals(1, viewMap(ended.get("turn")).get("actionsTaken"));
        assertFalse(viewMap(ended.get("turn")).containsKey("sellInProgress"));
        assertJsonEquals(before, state);
    }

    @ParameterizedTest @ValueSource(strings = {"build", "develop", "sell", "network"})
    void realActionsRejectMissingResourceChoicesAtomically(String type) throws Exception {
        var state = type.equals("sell") ? saleState() : actionState();
        Map<String, Object> payload = switch (type) {
            case "build" -> Map.of("cardId", "card-a", "city", "Birmingham", "industryType", "manufacturer");
            case "develop" -> Map.of("cardId", "card-a", "industryTypes", List.of("cotton_mill"));
            case "sell" -> Map.of("cardId", "card-a", "tileId", "sale", "merchantId", "merchant-a");
            default -> Map.of("cardId", "card-a", "from", "Birmingham", "to", "Dudley");
        };
        if (type.equals("network")) state.put("era", "rail");
        var before = reload(state);
        var error = assertThrows(ApiException.class, () -> act(state, command(type, payload)));
        assertTrue(error.getMessage().contains("resourceSelections"));
        assertJsonEquals(before, state);
    }

    @Test void zeroResourceBuildMayOmitSelectionsAndRetainsCompatibleTileFields() throws Exception {
        var state = actionState(); var before = reload(state);
        var result = act(state, command("build", Map.of("cardId", "card-a", "city", "Birmingham", "industryType", "cotton_mill"))).state();
        assertEquals(88, viewMap(viewMap(result.get("playerStats")).get("1")).get("money"));
        var built = listOf(viewMap(result.get("board")).get("industries")).stream().filter(tile -> "cotton_mill".equals(tile.get("industryType"))).findFirst().orElseThrow();
        assertTrue(built.containsKey("coalCost")); assertTrue(built.containsKey("sourceTileId"));
        assertJsonEquals(before.get("market"), result.get("market"));
        assertJsonEquals(before, state);
    }

    @Test void zeroBeerSaleMayOmitSelectionsWithoutConsumingMerchantBeer() throws Exception {
        var state = saleState();
        var tiles = listOf(viewMap(state.get("board")).get("industries"));
        find(tiles, "sale").put("saleBeerCost", 0); viewMap(state.get("board")).put("industries", tiles);
        var before = reload(state);
        var result = act(state, command("sell", Map.of("cardId", "card-a", "tileId", "sale", "merchantId", "merchant-a"))).state();
        assertJsonEquals(before.get("market"), result.get("market"));
        assertEquals(10, viewMap(viewMap(result.get("playerStats")).get("1")).get("incomeLevel"));
        assertEquals(true, find(listOf(viewMap(result.get("board")).get("industries")), "sale").get("flipped"));
        assertJsonEquals(before, state);
    }

    @Test void failedSecondDevelopmentRollsBackResourcesCardAndFirstTileRemoval() throws Exception {
        var state = actionState();
        var boards = viewMap(viewMap(state.get("playerBoards")).get("1"));
        var pottery = listOf(boards.get("pottery"));
        pottery.getFirst().put("canDevelop", false); boards.put("pottery", pottery);
        var before = reload(state);
        assertThrows(ApiException.class, () -> act(state, command("develop", Map.of("cardId", "card-a",
                "industryTypes", List.of("cotton_mill", "pottery"),
                "resourceSelections", payload(List.of(select("iron", "industry", "iron-own", 0, 1),
                        select("iron", "industry", "iron-other", 0, 1))).get("resourceSelections")))));
        assertJsonEquals(before, state);
    }

    @Test void insufficientMoneyPartwayThroughMarketConsumptionRollsBackBuildAndTrack() throws Exception {
        var state = actionState();
        viewMap(viewMap(state.get("playerStats")).get("1")).put("money", 9);
        var boards = viewMap(viewMap(state.get("playerBoards")).get("1"));
        var manufacture = listOf(boards.get("manufacturer"));
        manufacture.getFirst().put("coalCost", 0); manufacture.getFirst().put("ironCost", 2); boards.put("manufacturer", manufacture);
        var tiles = listOf(viewMap(state.get("board")).get("industries"));
        find(tiles, "iron-own").put("iron", 0); find(tiles, "iron-other").put("iron", 0);
        viewMap(state.get("board")).put("industries", tiles);
        var before = reload(state);
        assertThrows(ApiException.class, () -> act(state, command("build", Map.of("cardId", "card-a", "city", "Birmingham", "industryType", "manufacturer",
                "resourceSelections", payload(List.of(select("iron", "market", "market:iron", 0, 2))).get("resourceSelections")))));
        assertJsonEquals(before, state);
    }

    @Test void failingMerchantRewardRollsBackPriorBeerConsumptionAndSaleSession() throws Exception {
        var state = saleState();
        var merchants = listOf(viewMap(state.get("market")).get("beerMerchants"));
        merchants.getFirst().put("reward", "Develop"); viewMap(state.get("market")).put("beerMerchants", merchants);
        var before = reload(state);
        assertThrows(ApiException.class, () -> act(state, command("sell", Map.of("cardId", "card-a", "tileId", "sale",
                "merchantId", "merchant-a", "freeDevelopIndustryType", "not-an-industry",
                "resourceSelections", payload(mixedBeer()).get("resourceSelections")))));
        assertJsonEquals(before, state);
    }

    @Test void failedSaleSubmissionKeepsEarlierSaleAndBusinessActionIdentity() throws Exception {
        var state = saleState();
        var first = act(state, command("sell", Map.of("cardId", "card-a", "tileId", "sale", "merchantId", "merchant-a",
                "resourceSelections", payload(mixedBeer()).get("resourceSelections"))));
        var before = reload(first.state());
        assertThrows(ApiException.class, () -> act(first.state(), command("sell",
                Map.of("tileId", "sale", "merchantId", "merchant-a", "resourceSelections", List.of()))));
        assertJsonEquals(before, first.state());
        var ended = act(reload(first.state()), command("end_sell", Map.of()));
        assertNotNull(first.event().get("businessActionId"));
        assertEquals(first.event().get("businessActionId"), ended.event().get("businessActionId"));
    }

    private static Map<String, Object> state() {
        var state = new LinkedHashMap<String, Object>();
        state.put("board", new LinkedHashMap<>(Map.of("industries", new ArrayList<>(List.of(
                tile("coal-own", 1, "Birmingham", "coal_mine", 1), tile("coal-other", 2, "Dudley", "coal_mine", 1),
                tile("iron-own", 1, "Remote", "iron_works", 1), tile("iron-other", 2, "Further", "iron_works", 1),
                tile("beer-own", 1, "Leek", "brewery", 1), tile("beer-other", 2, "Dudley", "brewery", 1))),
                "links", new ArrayList<>(List.of(link("Birmingham", "Dudley"), link("Dudley", "Oxford"))),
                "extension", List.of("board-kept"))));
        var merchant = new LinkedHashMap<String, Object>(Map.of("id", "merchant-a", "city", "Oxford", "beer", 1,
                "acceptedIndustryTypes", List.of("cotton_mill"), "reward", "2 Income"));
        state.put("market", new LinkedHashMap<>(Map.of("coal", new ArrayList<>(List.of(1, 2)), "iron", new ArrayList<>(List.of(1, 2)), "beerMerchants", new ArrayList<>(List.of(merchant)))));
        state.put("playerStats", new LinkedHashMap<>(Map.of("1", new LinkedHashMap<>(Map.of("money", 100, "incomeLevel", 10, "income", 0, "spentThisRound", 0)),
                "2", new LinkedHashMap<>(Map.of("money", 100, "incomeLevel", 10, "income", 0, "spentThisRound", 0)))));
        return state;
    }
    private static Map<String, Object> actionState() throws Exception {
        var state = opening(); var resources = state();
        state.put("board", resources.get("board")); state.put("market", resources.get("market")); state.put("playerStats", resources.get("playerStats"));
        state.put("round", 2);
        state.put("turn", new LinkedHashMap<>(Map.of("actionsAllowed", 2, "actionsRemaining", 2, "actionsTaken", 0, "awaitingEndTurn", false, "startedAtRound", 2)));
        state.put("hands", new LinkedHashMap<>(Map.of("1", List.of(Map.of("id", "card-a", "type", "location", "key", "Birmingham"),
                Map.of("id", "card-b", "type", "location", "key", "Oxford")), "2", List.of())));
        // Keep the lowest personal tiles legal in canal, and leave the build city free of own industries.
        var tiles = listOf(viewMap(state.get("board")).get("industries"));
        find(tiles, "coal-own").put("city", "Dudley");
        viewMap(state.get("board")).put("industries", tiles);
        state.put("era", "canal");
        return state;
    }
    private static Map<String, Object> saleState() throws Exception {
        var state = actionState(); var tiles = listOf(viewMap(state.get("board")).get("industries"));
        var sale = tile("sale", 1, "Birmingham", "cotton_mill", 0); sale.put("flipType", "sell"); sale.put("saleBeerCost", 3); tiles.add(sale);
        viewMap(state.get("board")).put("industries", tiles); return state;
    }
    private static Map<String, Object> tile(String id, long owner, String city, String type, int amount) {
        var tile = new LinkedHashMap<String, Object>(Map.of("id", id, "ownerId", owner, "ownerName", owner == 1 ? "A" : "B",
                "city", city, "industryType", type, "flipped", false, "level", 1, "incomeReward", 0, "extension", List.of("kept")));
        tile.put("coal", type.equals("coal_mine") ? amount : 0); tile.put("iron", type.equals("iron_works") ? amount : 0);
        tile.put("beer", type.equals("brewery") ? amount : 0);
        return tile;
    }
    private static Map<String, Object> link(String from, String to) { return new LinkedHashMap<>(Map.of("from", from, "to", to, "ownerId", 2L, "type", "rail")); }
    private static Map<String, Object> find(List<Map<String, Object>> values, String id) { return values.stream().filter(v -> id.equals(v.get("id"))).findFirst().orElseThrow(); }
    private static int stock(Map<String, Object> state, String id, String type) { return parseInteger(find(listOf(viewMap(state.get("board")).get("industries")), id).get(type), 0); }
    private static ResourceSelection select(String type, String kind, String id, int index, int amount) { return new ResourceSelection(ResourceType.from(type), SourceType.from(kind), id, index, amount); }
    private static Map<String, Object> wire(ResourceSelection s) { return Map.of("type", s.type().key, "sourceType", s.sourceType().key, "sourceId", s.sourceId(), "requirementIndex", s.requirementIndex(), "amount", s.amount()); }
    private static Map<String, Object> payload(List<ResourceSelection> selections) { return Map.of("resourceSelections", selections.stream().map(BrassResourceMigrationTest::wire).toList()); }
    private static Map<String, Object> command(String type, Map<String, Object> payload) { return Map.of("type", type, "payload", payload); }
    private static List<ResourceSelection> mixedBeer() { return List.of(select("beer", "industry", "beer-own", 0, 1), select("beer", "industry", "beer-other", 0, 1), select("beer", "merchant", "merchant-a", 0, 1)); }
    private static List<ResourceAllocation> validate(Map<String, Object> state, List<ResourceRequirement> needs, List<ResourceSelection> selections) {
        return BrassTypedResources.validate(BrassResourceAdapter.context(state), 1, needs, selections, List.of("Oxford"));
    }
}
