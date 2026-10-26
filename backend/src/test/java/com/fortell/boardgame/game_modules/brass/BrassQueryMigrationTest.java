package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.UserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.*;
import java.util.stream.Stream;
import static com.fortell.boardgame.game_modules.brass.BrassCompatibilityFixtures.*;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;
import static org.junit.jupiter.api.Assertions.*;

class BrassQueryMigrationTest {
    static Stream<Scenario> saves() throws Exception { return scenarios(); }
    @ParameterizedTest @MethodSource("saves")
    void allLegacyPhasesHaveStableTypedAndWireQueriesWithoutMutatingSaves(Scenario scenario) throws Exception {
        var state = scenario.state(); var before = reload(state);
        var typed = BrassActionQueryService.query(state);
        var wire = BrassActionQueryService.availableActions(state);
        assertEquals(typed.actions().stream().map(action -> action.key).toList(), wire.get("actions"));
        assertJsonEquals(wire, BrassActionQueryService.availableActions(reload(state)));
        assertThrows(UnsupportedOperationException.class, () -> typed.actions().clear());
        assertThrows(UnsupportedOperationException.class, () -> typed.builds().clear());
        assertJsonEquals(before, state);
    }

    @Test void inactiveSparseDocumentsDoNotDecodeUnrelatedPrivateOrInvalidComponents() {
        var state = new LinkedHashMap<String, Object>();
        state.put("phase", "finished"); state.put("currentPlayerId", "opaque-legacy-id");
        state.put("board", Map.of("industries", List.of(Map.of("ownerId", "not-a-number"))));
        state.put("players", List.of(Map.of("userId", "unrelated-legacy-id")));
        var query = BrassActionQueryService.availableActions(state);
        assertEquals(Map.of("currentPlayerId", "opaque-legacy-id", "actions", List.of()), query);
        state.remove("currentPlayerId");
        assertTrue(BrassActionQueryService.availableActions(state).containsKey("currentPlayerId"));
        assertNull(BrassActionQueryService.availableActions(state).get("currentPlayerId"));
    }

    @Test void snapshotInputAndNestedCollectionsAreDetachedFromTheWireDocument() throws Exception {
        var state = opening(); var input = BrassQueryAdapter.input(state);
        var probe = new BrassQueryModel.Probe() {
            public boolean build(BrassQueryModel.Build candidate) { return true; }
            public boolean sell(int tile, int merchant) { return true; }
            public boolean network(List<BrassTypedNetwork.RouteSpec> routes) { return true; }
        };
        var before = BrassTypedQueries.query(input, probe);
        viewMap(state.get("hands")).clear(); viewMap(state.get("playerBoards")).clear(); viewMap(state.get("board")).clear();
        assertEquals(before, BrassTypedQueries.query(input, probe));
        assertThrows(UnsupportedOperationException.class, () -> input.slots().get("Birmingham").getFirst().clear());
        assertThrows(UnsupportedOperationException.class, () -> input.resources().board().industries().clear());
    }

    @Test void queryingAndMutatingAnEncodedResultCannotModifyRealResourcesOrCachedActions() throws Exception {
        var state = saleState(); var before = reload(state);
        var query = BrassActionQueryService.availableActions(state);
        var merchant = listOf(listOf(query.get("sellTiles")).getFirst().get("merchantSources")).getFirst();
        ((List<?>) merchant.get("acceptedIndustryTypes")).clear();
        var input = BrassQueryAdapter.input(state); var probe = new BrassSimulationProbe(state, 1);
        assertFalse(probe.build(new BrassQueryModel.Build("not-a-city", "cotton_mill", 0, false, false, List.of("card-a"))));
        assertFalse(probe.network(List.of(new BrassTypedNetwork.RouteSpec("nowhere", "elsewhere"))));
        assertFalse(BrassTypedQueries.query(input, probe).sales().isEmpty());
        assertJsonEquals(before, state);
    }

    @Test void displayedResourceFreeBuildCanExecuteWithTheUnchangedPublicContract() throws Exception {
        var state = opening(); var before = reload(state);
        var option = listOf(BrassActionQueryService.availableActions(state).get("buildOptions")).stream()
                .filter(row -> parseInteger(row.get("coalCost"), 0) == 0 && parseInteger(row.get("ironCost"), 0) == 0).findFirst().orElseThrow();
        var payload = new LinkedHashMap<>(option); payload.put("cardId", stringList(option.get("cardIds")).getFirst());
        var result = act(state, Map.of("type", "build", "payload", payload));
        assertEquals("build", result.event().get("type"));
        assertEquals(1, listOf(viewMap(result.state().get("board")).get("industries")).size());
        assertJsonEquals(before, state);
    }

    @Test void legacySupplyScalarRepresentationsAreNotNormalizedInQueryEncoding() throws Exception {
        var state = baseState();
        var supply = listOf(viewMap(viewMap(state.get("playerBoards")).get("1")).get("cotton_mill"));
        var tile = supply.getFirst(); tile.put("level", "1"); tile.put("cost", "12"); tile.remove("coalCost"); tile.put("ironCost", null);
        viewMap(viewMap(state.get("playerBoards")).get("1")).put("cotton_mill", supply);
        var option = listOf(BrassActionQueryService.availableActions(state).get("buildOptions")).stream()
                .filter(row -> "cotton_mill".equals(row.get("industryType"))).findFirst().orElseThrow();
        assertEquals("1", option.get("level")); assertEquals("12", option.get("cost"));
        assertTrue(option.containsKey("coalCost")); assertNull(option.get("coalCost")); assertNull(option.get("ironCost"));
    }

    @Test void mixedOwnOtherAndMerchantBeerRemainsSellableAndExecutable() throws Exception {
        var state = saleState(); var before = reload(state);
        var query = BrassActionQueryService.availableActions(state);
        assertEquals(List.of("sale"), listOf(query.get("sellTiles")).stream().map(tile -> tile.get("id")).toList());
        var result = act(state, Map.of("type", "sell", "payload", Map.of("cardId", "card-a", "tileId", "sale", "merchantId", "merchant",
                "resourceSelections", List.of(selection("beer", "industry", "own-beer", 0), selection("beer", "industry", "other-beer", 0),
                        selection("beer", "merchant", "merchant", 0)))));
        assertEquals(0, listOf(viewMap(result.state().get("market")).get("beerMerchants")).getFirst().get("beer"));
        assertJsonEquals(before, state);
    }

    @ParameterizedTest @CsvSource({"1,1,1,true", "2,0,1,true", "1,0,1,false", "0,2,1,false", "1,1,0,false"})
    void doubleRailQueriesRespectStageReachabilityAndSharedStocks(int firstCoal, int secondCoal, int beer, boolean expected) throws Exception {
        var state = baseState(); state.put("era", "rail");
        viewMap(state.get("board")).put("industries", List.of(tile("anchor", 1, "Birmingham", "cotton_mill", 0),
                tile("first", 2, "Dudley", "coal_mine", firstCoal), tile("second", 2, "Wolverhampton", "coal_mine", secondCoal),
                tile("beer", 2, "Wolverhampton", "brewery", beer)));
        var before = reload(state);
        var pairs = BrassActionQueryService.query(state).pairs();
        var requested = pairs.stream().filter(pair -> same(pair.first().route(), "Birmingham", "Dudley")
                && same(pair.second().route(), "Dudley", "Wolverhampton")).findFirst();
        assertEquals(expected, requested.isPresent());
        if (expected) {
            var pair = requested.orElseThrow();
            assertEquals(1, pair.first().stage().links().size()); assertEquals(2, pair.second().stage().links().size());
            assertEquals(1L, pair.first().stage().links().getFirst().ownerId());
            var selections = List.of(selection("coal", "industry", "first", 0),
                    selection("coal", "industry", firstCoal > 1 ? "first" : "second", 1), selection("beer", "industry", "beer", 2));
            var result = act(state, Map.of("type", "network", "payload", Map.of("cardId", "card-a", "routes", List.of(
                    Map.of("from", "Birmingham", "to", "Dudley"), Map.of("from", "Dudley", "to", "Wolverhampton")), "resourceSelections", selections)));
            assertEquals(2, listOf(viewMap(result.state().get("board")).get("links")).size());
        }
        assertJsonEquals(before, state);
    }

    @Test void noCardsOrLinkPiecesDoesNotInventAnExecutableNetworkAction() throws Exception {
        var state = baseState(); state.put("hands", Map.of("1", List.of()));
        assertFalse(BrassActionQueryService.query(state).actions().contains(BrassActionType.NETWORK));
        state = baseState(); var links = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 14; i++) links.add(Map.of("from", "X" + i, "to", "Y" + i, "ownerId", 1L, "type", "canal"));
        viewMap(state.get("board")).put("links", links);
        var result = BrassActionQueryService.query(state);
        assertEquals(0, result.context().remainingLinks()); assertTrue(result.routes().isEmpty()); assertTrue(result.pairs().isEmpty());
    }

    static Map<String, Object> baseState() throws Exception {
        var state = opening(); state.put("round", 2); state.put("currentPlayerId", 1L);
        state.put("turn", new LinkedHashMap<>(Map.of("actionsAllowed", 2, "actionsRemaining", 2, "actionsTaken", 0, "awaitingEndTurn", false)));
        state.put("hands", new LinkedHashMap<>(Map.of("1", new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("id", "card-a", "type", "location", "key", "Birmingham")))), "2", List.of())));
        viewMap(viewMap(state.get("playerStats")).get("1")).put("money", 100);
        state.put("board", new LinkedHashMap<>(Map.of("industries", List.of(), "links", List.of())));
        state.put("market", new LinkedHashMap<>(Map.of("coal", List.of(), "iron", List.of(), "beerMerchants", List.of())));
        return state;
    }
    static Map<String, Object> saleState() throws Exception {
        var state = baseState(); var sale = tile("sale", 1, "Birmingham", "cotton_mill", 0); sale.put("flipType", "sell"); sale.put("saleBeerCost", 3);
        viewMap(state.get("board")).put("industries", List.of(sale, tile("own-beer", 1, "Leek", "brewery", 1), tile("other-beer", 2, "Oxford", "brewery", 1)));
        viewMap(state.get("board")).put("links", List.of(Map.of("from", "Birmingham", "to", "Oxford", "ownerId", 2L, "type", "canal")));
        viewMap(state.get("market")).put("beerMerchants", new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("id", "merchant", "city", "Oxford", "beer", 1,
                "acceptedIndustryTypes", new ArrayList<>(List.of("cotton_mill")), "reward", "")))));
        return state;
    }
    static Map<String, Object> tile(String id, long actor, String city, String type, int amount) {
        var result = new LinkedHashMap<String, Object>(Map.of("id", id, "ownerId", actor, "city", city, "industryType", type, "level", 1, "flipped", false));
        result.put("coal", "coal_mine".equals(type) ? amount : 0); result.put("beer", "brewery".equals(type) ? amount : 0); return result;
    }
    static Map<String, Object> selection(String type, String kind, String id, int index) {
        return Map.of("type", type, "sourceType", kind, "sourceId", id, "requirementIndex", index, "amount", 1);
    }
    private static boolean same(BrassTypedNetwork.RouteSpec route, String from, String to) { return BrassTypedConnectivity.sameRoute(route.from(), route.to(), from, to); }
}
