package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassBoardView.*;
import com.fortell.boardgame.models.ApiException;
import com.fortell.boardgame.models.UserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static com.fortell.boardgame.game_modules.brass.BrassCompatibilityFixtures.*;
import static com.fortell.boardgame.game_modules.brass.BrassConnectivityService.*;
import static com.fortell.boardgame.game_modules.brass.BrassIndustryService.*;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;
import static org.junit.jupiter.api.Assertions.*;

class BrassBoardMigrationTest {
    // Historical JSON-shape fixture only; production query stages use immutable Network.withLink.
    private static Map<String, Object> boardWithHypotheticalLinks(Map<String, Object> board, List<Map<String, Object>> additions) {
        var copy = mapOf(deepCopy(board)); var links = listOf(copy.get("links"));
        links.addAll(listOf(deepCopy(additions))); copy.put("links", links); return copy;
    }
    @Test void publicTransportUsesBothOwnersButPersonalNetworkDoesNotExpandThroughOpponentLinks() {
        var board = board(List.of(tile("own", 1, "A", "cotton_mill", 0)),
                List.of(link("A", "B", 1, "rail"), link("B", "C", 2, "rail"), link("D", "C", 2, "rail")));
        assertTrue(citiesConnected(board, "A", "D"));
        assertTrue(citiesConnected(board, "D", "A"));
        assertEquals(List.of("A", "B"), playerNetworkCities(board, 1));
        assertFalse(touchesPlayerNetwork(board, 1, "C", "D"));
        assertFalse(cityBuildableWithIndustryCard(board, 1, "C"));
        assertTrue(cityBuildableWithIndustryCard(board, 1, "B"));
    }

    @Test void emptyPersonalNetworkAllowsFirstBuildAndLink() {
        var board = board(List.of(tile("other", 2, "A", "coal_mine", 0)), List.of(link("A", "B", 2, "rail")));
        assertTrue(playerNetworkCities(board, 1).isEmpty());
        assertTrue(cityBuildableWithIndustryCard(board, 1, "Z"));
        assertTrue(touchesPlayerNetwork(board, 1, "X", "Z"));
        assertFalse(citiesConnected(board, "X", "Z"));
    }

    @Test void reversedDuplicateCyclesAndUnknownLinkTypesPreserveTransportSemantics() {
        var links = List.of(link("A", "B", 1, "canal"), link("B", "A", 2, "rail"),
                link("B", "C", 0, "future-type"), link("C", "A", 2, "rail"));
        var board = board(List.of(), links);
        assertTrue(citiesConnected(board, "A", "C"));
        assertFalse(citiesConnected(board, "A", "D"));
        assertTrue(citiesConnected(board, "D", "D"));
        assertTrue(routeOccupied(links, "B", "A", "canal"));
        assertTrue(routeOccupied(links, "A", "B", "rail"));
        assertFalse(routeOccupied(links, "B", "C", "rail"));
        assertEquals(2, playerLinkCount(links, 2));
        assertEquals(List.of("A", "B", "C"), networkDestinations(links));
        assertEquals(routeKey("A", "B"), routeKey("B", "A"));
    }

    @Test void privateBreweryAccessComesOnlyFromOwnedSpecialRoute() {
        var route = BrassRuleData.routes().stream().filter(r -> Boolean.TRUE.equals(r.get("hasBrewery"))).findFirst().orElseThrow();
        String from = (String) route.get("from"), to = (String) route.get("to");
        var board = board(List.of(), List.of(link(to, from, 2, "rail")));
        assertEquals(List.of(to, from, "Personal_Brewery"), playerNetworkCities(board, 2));
        assertFalse(playerNetworkCities(board, 1).contains("Personal_Brewery"));
        assertFalse(citiesConnected(board, from, "Personal_Brewery"));
    }

    @Test void coalAndBeerHaveDifferentOwnershipAndEmptyDestinationRules() {
        var board = board(List.of(), List.of(link("A", "B", 2, "rail")));
        var own = tile("own-beer", 1, "Z", "brewery", 1);
        var other = tile("other-beer", 2, "Z", "brewery", 1);
        assertTrue(beerSourceReachable(board, own, "A", 1));
        assertFalse(beerSourceReachable(board, other, "A", 1));
        assertFalse(coalSourceReachable(board, own, List.of("A")));
        assertTrue(coalSourceReachable(board, own, List.of()));
        assertTrue(coalSourceReachable(board, own, null));
        assertFalse(coalSourceReachable(board, own, List.of("")));
        assertTrue(beerSourceReachableToAny(board, own, List.of(), 1));
        assertFalse(beerSourceReachableToAny(board, other, List.of(), 1));
        assertFalse(coalMarketReachable(board, List.of()));
        assertFalse(coalMarketReachable(board, null));
    }

    @Test void coalMarketAccessUsesActualPublicPathNotOwnership() {
        String market = Objects.toString((BrassRuleData.MAP_MARKETS.isEmpty() ? BrassRuleData.fallbackMarkets() : BrassRuleData.MAP_MARKETS).getFirst().get("city"));
        var board = board(List.of(), List.of(link("Mine", "Transit", 2, "rail"), link("Transit", market, 2, "rail")));
        assertTrue(coalMarketReachable(board, List.of("Mine")));
        assertTrue(cityConnectedToAnyMarket(board, "Mine"));
        assertFalse(coalMarketReachable(board, List.of("Isolated")));
    }

    @Test void typedNetworksAndWireHypothesesDoNotShareMutableStages() throws Exception {
        var added = link("A", "B", 1, "rail");
        added.put("extension", new ArrayList<>(List.of("first")));
        var original = board(List.of(tile("coal", 2, "B", "coal_mine", 2)), List.of());
        original.put("extension", new ArrayList<>(List.of("board")));
        var before = reload(original);
        var afterFirst = boardWithHypotheticalLinks(original, List.of(added));
        var typedFirst = BrassBoardAdapter.network(afterFirst);
        var typedBoth = typedFirst.withLink(new Link("B", "C", 1, "rail"));
        var afterBoth = boardWithHypotheticalLinks(afterFirst, List.of(link("B", "C", 1, "rail")));
        assertFalse(BrassTypedConnectivity.connected(typedFirst, "A", "C"));
        assertTrue(BrassTypedConnectivity.connected(typedBoth, "A", "C"));
        assertThrows(UnsupportedOperationException.class, () -> typedFirst.links().clear());
        added.put("from", "CHANGED");
        ((List<?>) added.get("extension")).clear();
        assertEquals("A", listOf(afterFirst.get("links")).getFirst().get("from"));
        assertEquals(List.of("first"), listOf(afterFirst.get("links")).getFirst().get("extension"));
        listOf(afterBoth.get("links")).getFirst().put("from", "OTHER");
        assertEquals("A", listOf(afterFirst.get("links")).getFirst().get("from"));
        assertJsonEquals(before, original);
    }

    @Test void connectivityMatchesIndependentGraphOracleForSeededNetworks() {
        var random = new Random(3018);
        for (int sample = 0; sample < 40; sample++) {
            boolean[][] reach = new boolean[9][9];
            var links = new ArrayList<Link>();
            for (int i = 0; i < 9; i++) {
                reach[i][i] = true;
                for (int j = i + 1; j < 9; j++) if (random.nextInt(5) == 0) {
                    reach[i][j] = reach[j][i] = true;
                    links.add(new Link("C" + i, "C" + j, random.nextInt(3), random.nextBoolean() ? "rail" : "canal"));
                }
            }
            for (int k = 0; k < 9; k++) for (int i = 0; i < 9; i++) for (int j = 0; j < 9; j++) reach[i][j] |= reach[i][k] && reach[k][j];
            var board = new Network(links);
            for (int i = 0; i < 9; i++) for (int j = 0; j < 9; j++)
                assertEquals(reach[i][j], BrassTypedConnectivity.connected(board, "C" + i, "C" + j), sample + ":" + i + "->" + j);
        }
    }

    @Test void legacySparseAndNullIndustryFieldsAreNotFilledBackOnRead() throws Exception {
        var sparse = new LinkedHashMap<String, Object>();
        sparse.put("id", "old"); sparse.put("industryType", "coal_mine"); sparse.put("coal", "2");
        sparse.put("ownerId", "2"); sparse.put("extension", null);
        var before = reload(sparse);
        assertEquals(2, remainingIndustryResource(sparse));
        assertEquals(-1, BrassBoardAdapter.industry(sparse).slotIndex());
        assertTrue(isDepleteTile(sparse));
        assertEquals(1, saleBeerRequired(sparse));
        assertJsonEquals(before, sparse);
        sparse.put("flipType", null); sparse.put("saleBeerCost", null);
        assertFalse(isDepleteTile(sparse));
        assertEquals(0, saleBeerRequired(sparse));
        sparse.put("saleBeerCost", -3);
        assertEquals(0, saleBeerRequired(sparse));
    }

    @Test void absentOrNullBoardCollectionsRemainSparseOnRead() throws Exception {
        var board = new LinkedHashMap<String, Object>();
        board.put("industries", null); board.put("links", null); board.put("extension", List.of("kept"));
        var before = reload(board);
        assertTrue(playerNetworkCities(board, 1).isEmpty());
        assertTrue(citiesConnected(board, "A", "A"));
        assertFalse(citiesConnected(board, "A", "B"));
        assertTrue(cityBuildableWithIndustryCard(board, 1, "A"));
        assertJsonEquals(before, board);
        var state = new LinkedHashMap<String, Object>();
        assertTrue(BrassPlayerService.ownedMapIndustries(state, 1).isEmpty());
        assertTrue(state.isEmpty());
    }

    @Test void industryFlipChangesOnlyExplicitFieldsAndLeavesOtherInstancesAlone() throws Exception {
        var first = tile("same-id", 1, "A", "brewery", 1);
        first.put("extension", new LinkedHashMap<>(Collections.singletonMap("nullable", null)));
        first.put("cost", null); first.put("era", "rail");
        var second = tile("same-id", 2, "B", "brewery", 2);
        var otherBefore = reload(second);
        var expected = reload(first);
        expected.put("flipped", true); expected.put("coal", 0); expected.put("iron", 0); expected.put("beer", 0);
        var typedBefore = BrassBoardAdapter.industry(first);
        flipTileByOwnerMap(first);
        assertJsonEquals(expected, first);
        assertJsonEquals(otherBefore, second);
        assertEquals(1, typedBefore.beer());
        assertFalse(typedBefore.flipped());
        assertTrue(BrassBoardAdapter.industry(first).flipped());
        first.put("coal", null);
        flipTileByOwnerMap(first); // Already-flipped tiles are a no-op, even with historical sparse stocks.
        assertNull(first.get("coal"));
    }

    @Test void ownCoverRequiresSameTypeOwnerAndSlotButNotHigherLevelOrFlipped() {
        var tile = tile("x", 1, "City", "coal_mine", 0);
        tile.put("level", 2); tile.put("slotIndex", "1"); tile.put("flipped", true);
        assertSame(tile, coverableOwnFlippedTile(List.of(tile), 1, "City", "coal_mine", 1, 3));
        assertSame(tile, coverableOwnFlippedTile(List.of(tile), 1, "City", "coal_mine", 1, 2));
        assertTrue(coverableOwnFlippedTile(List.of(tile), 2, "City", "coal_mine", 1, 3).isEmpty());
        assertTrue(coverableOwnFlippedTile(List.of(tile), 1, "City", "iron_works", 1, 3).isEmpty());
        assertTrue(coverableOwnFlippedTile(List.of(tile), 1, "City", "coal_mine", 0, 3).isEmpty());
        tile.put("flipped", false);
        assertSame(tile, coverableOwnFlippedTile(List.of(tile), 1, "City", "coal_mine", 3));
    }

    @ParameterizedTest @ValueSource(strings = {"coal_mine", "iron_works"})
    void opponentCoverRequiresEmptyMarketAndNoSupplyAnywhere(String type) {
        String resource = type.equals("coal_mine") ? "coal" : "iron";
        var target = tile("target", 2, "City", type, 0);
        target.put("slotIndex", 0);
        var supply = tile("far-away", 1, "Remote", type, 1);
        var state = new LinkedHashMap<String, Object>(Map.of("board", board(List.of(target, supply), List.of()), "market", Map.of(resource, List.of())));
        assertTrue(coverableOpponentResourceTile(state, 1, "City", type, 0).isEmpty());
        supply.put(resource, 0);
        assertFalse(coverableOpponentResourceTile(state, 1, "City", type, 0).isEmpty());
        assertTrue(coverableOpponentResourceTile(state, 2, "City", type, 0).isEmpty());
        state.put("market", Map.of(resource, List.of(1)));
        assertTrue(coverableOpponentResourceTile(state, 1, "City", type, 0).isEmpty());
    }

    @Test void dedicatedSlotPriorityAndInvalidSlotsRemainConsistent() {
        var slots = Map.of("City", List.of(List.of("coal_mine", "iron_works"), List.of("coal_mine")));
        assertEquals(1, BrassTypedIndustries.firstAvailable(List.of(), slots, "City", "coal_mine"));
        assertEquals(-1, BrassTypedIndustries.buildSlot(List.of(), List.of(), slots, 1, "City", "coal_mine", 2, true, 0));
        var occupied = tile("own", 1, "City", "coal_mine", 0); occupied.put("slotIndex", 1);
        var tiles = BrassBoardAdapter.industries(List.of(occupied));
        assertEquals(0, BrassTypedIndustries.firstAvailable(tiles, slots, "City", "coal_mine"));
        occupied.put("flipped", true);
        tiles = BrassBoardAdapter.industries(List.of(occupied));
        assertEquals(1, BrassTypedIndustries.buildSlot(tiles, tiles, slots, 1, "City", "coal_mine", 2, true, null));
        assertFalse(BrassTypedIndustries.available(tiles, slots, "City", "coal_mine", -1));
        assertFalse(BrassTypedIndustries.available(tiles, slots, "City", "coal_mine", 2));
        assertFalse(BrassTypedIndustries.available(tiles, slots, "Unknown", "coal_mine", 0));
    }

    @Test void findAndOwnershipPreserveOrderingDuplicateIdsAndUnknownFields() {
        var other = tile("same", 2, "Other", "brewery", 1);
        var own = tile("same", 1, "Own", "coal_mine", 2);
        own.put("extension", List.of("owned"));
        var tiles = List.of(other, own);
        assertSame(other, findTile(tiles, "same"));
        assertNull(findTile(tiles, "missing"));
        var state = Map.<String, Object>of("board", board(tiles, List.of()));
        assertEquals(List.of(own), BrassPlayerService.ownedMapIndustries(state, 1));
        assertTrue(playerAlreadyBuiltInCity(tiles, 1, "Own"));
        assertFalse(playerAlreadyBuiltInCity(tiles, 1, "Other"));
    }

    @Test void doubleRailStagesExposeOnlyTheSourcesReachableAtThatStage() throws Exception {
        var state = railwayState();
        var board = mapOf(state.get("board"));
        var routes = railRoutes();
        var afterFirst = boardWithHypotheticalLinks(board, List.of(routes.getFirst()));
        var afterBoth = boardWithHypotheticalLinks(afterFirst, List.of(routes.getLast()));
        assertFalse(touchesPlayerNetwork(board, 1, "Dudley", "Wolverhampton"));
        // Hypothetical links must include their owner when evaluating the personal network.
        var ownFirst = boardWithHypotheticalLinks(board, List.of(link("Birmingham", "Dudley", 1, "rail")));
        assertTrue(touchesPlayerNetwork(ownFirst, 1, "Dudley", "Wolverhampton"));
        var requirements = BrassResourceRequirements.networkRequirements("rail", routes, List.of(afterFirst, afterBoth));
        assertEquals(List.of("first-coal"), BrassTypedResources.eligible(requirements.get(0).sourceBoard(), requirements.get(0), 1).stream().map(BrassResourceModel.ResourceSource::id).toList());
        assertEquals(List.of("first-coal", "second-coal"), BrassTypedResources.eligible(requirements.get(1).sourceBoard(), requirements.get(1), 1).stream().map(BrassResourceModel.ResourceSource::id).toList());
        assertEquals(List.of("other-beer"), BrassTypedResources.eligible(requirements.get(2).sourceBoard(), requirements.get(2), 1).stream().map(BrassResourceModel.ResourceSource::id).toList());
        assertTrue(BrassResourceSources.beerSourceItemsToAny(afterFirst, List.of("Birmingham", "Dudley"), 1).isEmpty());
    }

    @Test void doubleRailCanExtendNetworkAndUseSecondCoalAndOtherBeerAfterSecondLink() throws Exception {
        var state = railwayState(); var before = reload(state);
        var result = executeNetwork(state, selections("first-coal", "second-coal", "other-beer"));
        assertEquals(2, listOf(mapOf(result.get("board")).get("links")).size());
        assertEquals(85, viewMap(viewMap(result.get("playerStats")).get("1")).get("money"));
        assertEquals(1, viewMap(result.get("turn")).get("actionsTaken"));
        for (String id : List.of("first-coal", "second-coal", "other-beer")) {
            var used = findTile(listOf(mapOf(result.get("board")).get("industries")), id);
            assertEquals(0, remainingIndustryResource(used)); assertEquals(true, used.get("flipped"));
        }
        assertJsonEquals(before.get("market"), result.get("market")); // No iron, merchant beer or market coal is spent.
        assertJsonEquals(before, state);
        assertJsonEquals(result, reload(result));
    }

    @Test void doubleRailRejectsFirstCoalBorrowingFutureConnectivityWithoutChangingAnything() throws Exception {
        var state = railwayState(); var before = reload(state);
        var error = assertThrows(ApiException.class, () -> executeNetwork(state, selections("second-coal", "first-coal", "other-beer")));
        assertEquals("选择的资源来源不可用", error.getMessage());
        assertJsonEquals(before, state);
    }

    @Test void doubleRailCannotSpendSharedCoalTwiceEvenWhenBothStagesReachIt() throws Exception {
        var state = railwayState(); var before = reload(state);
        assertThrows(ApiException.class, () -> executeNetwork(state, selections("first-coal", "first-coal", "other-beer")));
        assertJsonEquals(before, state);
    }

    @ParameterizedTest @ValueSource(longs = {1, 2})
    void remoteBeerForDoubleRailDependsOnOwnerNotMapDistance(long owner) throws Exception {
        var state = railwayState();
        var tiles = listOf(viewMap(state.get("board")).get("industries"));
        tiles.add(tile("remote-beer", owner, "Nottingham", "brewery", 1));
        viewMap(state.get("board")).put("industries", tiles);
        var before = reload(state);
        if (owner == 1) {
            var result = executeNetwork(state, selections("first-coal", "second-coal", "remote-beer"));
            var remaining = listOf(viewMap(result.get("board")).get("industries"));
            assertEquals(0, findTile(remaining, "remote-beer").get("beer"));
            assertEquals(1, findTile(remaining, "other-beer").get("beer"));
        } else {
            var error = assertThrows(ApiException.class, () -> executeNetwork(state, selections("first-coal", "second-coal", "remote-beer")));
            assertEquals("选择的资源来源不可用", error.getMessage());
        }
        assertJsonEquals(before, state);
    }

    @ParameterizedTest @ValueSource(ints = {12, 13, 14})
    void linkSupplyBoundaryAllowsExactlyFourteenAndRejectsExcessAtomically(int existing) throws Exception {
        var state = railwayState();
        var links = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < existing; i++) links.add(link("L" + i, "L" + (i + 1), 1, "rail"));
        viewMap(state.get("board")).put("links", links);
        var before = reload(state);
        if (existing == 12) {
            var result = executeNetwork(state, selections("first-coal", "second-coal", "other-beer"));
            assertEquals(14, playerLinkCount(listOf(viewMap(result.get("board")).get("links")), 1));
        } else {
            assertThrows(ApiException.class, () -> executeNetwork(state, selections("first-coal", "second-coal", "other-beer")));
        }
        if (existing == 13) {
            var result = executeNetwork(state, List.of(railRoutes().getFirst()), List.of(selection("coal", "first-coal", 0)));
            assertEquals(14, playerLinkCount(listOf(viewMap(result.get("board")).get("links")), 1));
            assertEquals(1, findTile(listOf(viewMap(result.get("board")).get("industries")), "other-beer").get("beer"));
        } else if (existing == 14) {
            assertThrows(ApiException.class, () -> executeNetwork(state, List.of(railRoutes().getFirst()), List.of(selection("coal", "first-coal", 0))));
        }
        assertJsonEquals(before, state);
    }

    @Test void mapMetadataSurvivesConsumptionJsonReloadAndTheFollowingAction() throws Exception {
        var state = railwayState();
        var hands = mapOf(state.get("hands"));
        hands.put("1", List.of(Map.of("id", "network-card", "type", "location", "key", "Birmingham"),
                Map.of("id", "next-card", "type", "location", "key", "Oxford")));
        state.put("hands", hands);
        var board = viewMap(state.get("board"));
        board.put("extension", new LinkedHashMap<>(Collections.singletonMap("nullable", null)));
        var tiles = listOf(board.get("industries"));
        var legacyCoal = findTile(tiles, "first-coal");
        legacyCoal.put("coal", "1"); legacyCoal.put("incomeReward", "4"); legacyCoal.put("cost", null);
        legacyCoal.put("extension", List.of(Map.of("future", "unchanged"))); board.put("industries", tiles);
        var before = reload(state);
        var result = executeNetwork(reload(state), selections("first-coal", "second-coal", "other-beer"));
        var resumed = act(reload(result), Map.of("type", "skip", "payload", Map.of("cardId", "next-card"))).state();
        var savedCoal = findTile(listOf(viewMap(resumed.get("board")).get("industries")), "first-coal");
        assertEquals("4", savedCoal.get("incomeReward"));
        assertTrue(savedCoal.containsKey("cost")); assertNull(savedCoal.get("cost"));
        assertFalse(savedCoal.containsKey("slotIndex"));
        assertJsonEquals(legacyCoal.get("extension"), savedCoal.get("extension"));
        assertJsonEquals(board.get("extension"), viewMap(resumed.get("board")).get("extension"));
        assertEquals(true, viewMap(resumed.get("turn")).get("awaitingEndTurn"));
        assertJsonEquals(before, state);
    }

    @ParameterizedTest @ValueSource(strings = {"reverse-order", "duplicate-reversed", "disconnected", "occupied"})
    void invalidDoubleRoutesAreRejectedAtomically(String scenario) throws Exception {
        var state = railwayState(); var routes = new ArrayList<>(railRoutes());
        switch (scenario) {
            case "reverse-order" -> Collections.reverse(routes);
            case "duplicate-reversed" -> routes.set(1, Map.of("from", "Dudley", "to", "Birmingham"));
            case "disconnected" -> routes.set(1, Map.of("from", "Derby", "to", "Nottingham"));
            case "occupied" -> viewMap(state.get("board")).put("links", List.of(link("Dudley", "Birmingham", 2, "rail")));
        }
        var before = reload(state);
        assertThrows(ApiException.class, () -> executeNetwork(state, routes, selections("first-coal", "second-coal", "other-beer")));
        assertJsonEquals(before, state);
    }

    @Test void doubleRailQueryAgreesWithStagedExecutionAndDoesNotMutateSave() throws Exception {
        var state = railwayState(); var before = reload(state);
        var pairs = listOf(BrassActionQueryService.availableActions(state).get("networkRoutePairs"));
        var pair = pairs.stream().filter(p -> {
            var r = listOf(p.get("routes"));
            return sameRoute("Birmingham", "Dudley", (String) r.get(0).get("from"), (String) r.get(0).get("to"))
                    && sameRoute("Dudley", "Wolverhampton", (String) r.get(1).get("from"), (String) r.get(1).get("to"));
        }).findFirst().orElseThrow();
        var routes = listOf(pair.get("routes"));
        assertEquals(List.of("first-coal"), sourceIds(listOf(routes.get(0).get("coalSources"))));
        assertEquals(List.of("first-coal", "second-coal"), sourceIds(listOf(routes.get(1).get("coalSources"))));
        assertEquals(List.of("other-beer"), sourceIds(listOf(pair.get("beerSources"))));
        assertFalse(pairs.stream().anyMatch(p -> sameRoute("Dudley", "Wolverhampton",
                (String) listOf(p.get("routes")).getFirst().get("from"), (String) listOf(p.get("routes")).getFirst().get("to"))));
        assertJsonEquals(before, state);
    }

    private static List<String> sourceIds(List<Map<String, Object>> sources) { return sources.stream().map(s -> (String) s.get("id")).toList(); }
    private static Map<String, Object> board(List<Map<String, Object>> industries, List<Map<String, Object>> links) {
        return new LinkedHashMap<>(Map.of("industries", industries, "links", links));
    }
    private static Map<String, Object> link(String from, String to, long owner, String type) {
        return new LinkedHashMap<>(Map.of("from", from, "to", to, "ownerId", owner, "type", type));
    }
    private static Map<String, Object> tile(String id, long owner, String city, String type, int amount) {
        var tile = new LinkedHashMap<String, Object>();
        tile.put("id", id); tile.put("ownerId", owner); tile.put("ownerName", owner == 1 ? "A" : "B");
        tile.put("city", city); tile.put("industryType", type); tile.put("level", 1); tile.put("flipped", false);
        tile.put("coal", type.equals("coal_mine") ? amount : 0); tile.put("iron", type.equals("iron_works") ? amount : 0);
        tile.put("beer", type.equals("brewery") ? amount : 0); tile.put("incomeReward", 4);
        return tile;
    }
    private static Map<String, Object> railwayState() throws Exception {
        var state = opening(); state.put("era", "rail"); state.put("round", 2);
        state.put("turn", new LinkedHashMap<>(Map.of("actionsAllowed", 2, "actionsRemaining", 2, "actionsTaken", 0, "awaitingEndTurn", false, "startedAtRound", 2)));
        viewMap(viewMap(state.get("playerStats")).get("1")).put("money", 100);
        state.put("hands", Map.of("1", List.of(Map.of("id", "network-card", "type", "location", "key", "Birmingham")), "2", List.of()));
        state.put("board", board(List.of(tile("anchor", 1, "Birmingham", "cotton_mill", 0),
                tile("first-coal", 2, "Dudley", "coal_mine", 1), tile("second-coal", 2, "Wolverhampton", "coal_mine", 1),
                tile("other-beer", 2, "Wolverhampton", "brewery", 1)), List.of()));
        state.put("extension", new LinkedHashMap<>(Collections.singletonMap("nullable", null)));
        return state;
    }
    private static List<Map<String, Object>> railRoutes() {
        return List.of(Map.of("from", "Birmingham", "to", "Dudley"), Map.of("from", "Dudley", "to", "Wolverhampton"));
    }
    private static List<Map<String, Object>> selections(String firstCoal, String secondCoal, String beer) {
        return List.of(selection("coal", firstCoal, 0), selection("coal", secondCoal, 1), selection("beer", beer, 2));
    }
    private static Map<String, Object> selection(String type, String source, int index) {
        return Map.of("type", type, "sourceType", "industry", "sourceId", source, "requirementIndex", index, "amount", 1);
    }
    private static Map<String, Object> executeNetwork(Map<String, Object> state, List<Map<String, Object>> selections) {
        return executeNetwork(state, railRoutes(), selections);
    }
    private static Map<String, Object> executeNetwork(Map<String, Object> state, List<Map<String, Object>> routes, List<Map<String, Object>> selections) {
        return BrassActionExecutor.execute(state, new UserSummary(1, "A"), BrassActionCommand.decode("network",
                Map.of("cardId", "network-card", "routes", routes, "resourceSelections", selections)));
    }
}
