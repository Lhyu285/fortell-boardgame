package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.ApiException;
import com.fortell.boardgame.models.UserSummary;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import static com.fortell.boardgame.game_modules.brass.BrassCompatibilityFixtures.*;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;
import static org.junit.jupiter.api.Assertions.*;

/** Rule corrections use synthetic legacy-shaped saves, never a live room/database. */
class BrassSaleOverbuildRulesTest {
    @ParameterizedTest @CsvSource({"2 Income,1", "3 VP,1", "5,1", "Develop,1", "2 Income,2", "3 VP,2", "5,2", "Develop,2"})
    void breweryOnlySaleNeverAwardsMerchantBonusEvenWhenMerchantHasBeer(String reward, long breweryOwner) throws Exception {
        var state = saleState();
        merchant(state, "merchant-a").put("reward", reward);
        industry(state, "brewery").put("ownerId", breweryOwner);
        if (breweryOwner != 1) industry(state, "brewery").put("city", "Birmingham");
        // An invalid development choice must be ignored when no merchant beer is used.
        var before = reload(state);
        var result = act(state, command("sell", Map.of("cardId", "card-a", "tileId", "sale-a", "merchantId", "merchant-a",
                "freeDevelopIndustryType", "invalid", "resourceSelections", List.of(selection("industry", "brewery", 1))))).state();
        assertEquals(13, stat(result, "incomeLevel"));
        assertEquals(200, stat(result, "money"));
        assertEquals(0, stat(result, "victoryPoints"));
        assertEquals(1, merchant(result, "merchant-a").get("beer"));
        assertJsonEquals(before.get("playerBoards"), result.get("playerBoards"));
        assertFalse(objectList(result.get("notices")).stream().anyMatch(n -> n.toString().contains("因消耗")));
        assertJsonEquals(before, state);
        assertJsonEquals(result, reload(result));
    }

    @Test void sellSessionCanEarnBonusesAtDifferentMarketsButNotAgainUsingBreweryBeer() throws Exception {
        var state = saleState();
        var first = act(state, sale("sale-a", "merchant-a", "merchant", "merchant-a", true));
        assertEquals(15, stat(first.state(), "incomeLevel")); // Tile +3, merchant +2.
        var second = act(reload(first.state()), sale("sale-b", "merchant-b", "merchant", "merchant-b", false));
        assertEquals(18, stat(second.state(), "incomeLevel"));
        assertEquals(205, stat(second.state(), "money"));
        var third = act(reload(second.state()), sale("sale-c", "merchant-b", "industry", "brewery", false));
        assertEquals(21, stat(third.state(), "incomeLevel"));
        assertEquals(205, stat(third.state(), "money"));
        assertEquals(3, viewMap(third.state().get("turn")).get("sellCount"));
        assertEquals(0, viewMap(third.state().get("turn")).get("actionsTaken"));
        assertEquals(first.event().get("businessActionId"), second.event().get("businessActionId"));
        var ended = act(reload(third.state()), command("end_sell", Map.of()));
        assertEquals(1, viewMap(ended.state().get("turn")).get("actionsTaken"));
        assertEquals(first.event().get("businessActionId"), ended.event().get("businessActionId"));
        assertEquals(0, merchant(ended.state(), "merchant-a").get("beer"));
        assertEquals(0, merchant(ended.state(), "merchant-b").get("beer"));
    }

    @Test void splitSelectionsForSameMerchantAwardBonusOnlyOnce() throws Exception {
        var state = saleState();
        industry(state, "sale-a").put("saleBeerCost", 2);
        merchant(state, "merchant-a").put("beer", 2);
        var result = act(state, command("sell", Map.of("cardId", "card-a", "tileId", "sale-a", "merchantId", "merchant-a",
                "resourceSelections", List.of(selection("merchant", "merchant-a", 1), selection("merchant", "merchant-a", 1))))).state();
        assertEquals(15, stat(result, "incomeLevel"));
        assertEquals(0, merchant(result, "merchant-a").get("beer"));
    }

    @Test void merchantRewardRunsAfterTileFlipAndFailureStillRollsBackPublicAction() throws Exception {
        var state = saleState();
        merchant(state, "merchant-a").put("reward", "Develop");
        var payload = new LinkedHashMap<>(viewMap(sale("sale-a", "merchant-a", "merchant", "merchant-a", true).get("payload")));
        payload.put("freeDevelopIndustryType", "invalid");
        var before = reload(state);
        assertThrows(ApiException.class, () -> act(state, command("sell", payload)));
        assertJsonEquals(before, state);
        // Inspect an isolated handler working copy at the deliberately failing reward boundary.
        var working = reload(state);
        assertThrows(ApiException.class, () -> BrassSellAction.sellAction(working, new UserSummary(1, "A"),
                BrassActionCommand.decode("sell", payload), BrassExecutionMode.EXECUTE));
        assertEquals(true, industry(working, "sale-a").get("flipped"));
        assertEquals(13, stat(working, "incomeLevel"));
        assertEquals(0, merchant(working, "merchant-a").get("beer"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void zeroBeerSaleNeverAwardsBonusWithOrWithoutEmptySelections(boolean explicit) throws Exception {
        var state = saleState(); industry(state, "sale-a").put("saleBeerCost", 0);
        var payload = new LinkedHashMap<String, Object>(Map.of("cardId", "card-a", "tileId", "sale-a", "merchantId", "merchant-a"));
        if (explicit) payload.put("resourceSelections", List.of());
        var result = act(state, command("sell", payload)).state();
        assertEquals(13, stat(result, "incomeLevel"));
        assertJsonEquals(state.get("market"), result.get("market"));
    }

    @ParameterizedTest @ValueSource(strings = {"coal_mine", "iron_works", "brewery", "cotton_mill", "manufacturer", "pottery"})
    void ownSameLevelOverbuildAcceptsBothFacesWithOrWithoutExplicitSlot(String type) throws Exception {
        for (boolean flipped : List.of(false, true)) for (boolean explicitSlot : List.of(false, true)) {
            var state = buildState(type, 1, flipped, 3);
            var before = reload(state);
            var options = listOf(BrassActionQueryService.availableActions(state).get("buildOptions"));
            assertTrue(options.stream().anyMatch(o -> buildCity(type).equals(o.get("city"))
                    && type.equals(o.get("industryType")) && Boolean.TRUE.equals(o.get("coversOwn"))));
            assertJsonEquals(before, state); // Query/simulation never removes the real tile.
            var result = act(state, build(type, explicitSlot, List.of())).state();
            var tiles = listOf(viewMap(result.get("board")).get("industries"));
            assertEquals(1, tiles.size());
            assertNotEquals("old", tiles.getFirst().get("id"));
            assertEquals("new-tile", tiles.getFirst().get("sourceTileId"));
            assertEquals(1, tiles.getFirst().get("level"));
            assertEquals(0, BrassIndustryService.remainingIndustryResource(tiles.getFirst()));
            assertEquals(10, stat(result, "incomeLevel")); // Discarding stock is not resource depletion/flip.
            assertJsonEquals(before.get("market"), result.get("market"));
            assertJsonEquals(before, state);
            assertJsonEquals(result, reload(result));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"coal_mine", "iron_works"})
    void opponentOverbuildReplacesExactlyOneTileAndChecksGlobalStockAndMarket(String type) throws Exception {
        for (boolean explicitSlot : List.of(false, true)) {
            var state = buildState(type, 2, true, 0);
            viewMap(state.get("market")).put(resource(type), List.of());
            var result = act(state, build(type, explicitSlot, List.of())).state();
            var tiles = listOf(viewMap(result.get("board")).get("industries"));
            assertEquals(1, tiles.size());
            assertEquals(1L, asLong(tiles.getFirst().get("ownerId")));
            assertEquals(buildSlot(type), parseInteger(tiles.getFirst().get("slotIndex"), -1));
            for (String blocker : List.of("market", "remote-stock")) {
                var blocked = reload(state);
                if (blocker.equals("market")) viewMap(blocked.get("market")).put(resource(type), List.of(8));
                else {
                    var all = listOf(viewMap(blocked.get("board")).get("industries"));
                    all.add(tile("remote", 1, "Remote", type, 1)); viewMap(blocked.get("board")).put("industries", all);
                }
                var before = reload(blocked);
                assertThrows(ApiException.class, () -> act(blocked, build(type, explicitSlot, List.of())));
                assertJsonEquals(before, blocked);
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"brewery", "cotton_mill", "manufacturer", "pottery"})
    void opponentNonResourceIndustriesCannotBeOverbuilt(String type) throws Exception {
        var state = buildState(type, 2, true, 0); var before = reload(state);
        assertThrows(ApiException.class, () -> act(state, build(type, true, List.of())));
        assertJsonEquals(before, state);
    }

    @ParameterizedTest @ValueSource(strings = {"coal_mine", "iron_works"})
    void discardedFactoryStockIsNotASelectableBuildSourceAndFailureRestoresOldTile(String type) throws Exception {
        String resource = resource(type);
        var state = buildState(type, 1, false, 4);
        viewMap(state.get("board")).put("links", List.of(Map.of("from", buildCity(type), "to", "Oxford", "ownerId", 2L, "type", "canal")));
        var supply = viewMap(viewMap(state.get("playerBoards")).get("1"));
        var definitions = listOf(supply.get(type)); definitions.getFirst().put(resource + "Cost", 1); supply.put(type, definitions);
        var before = reload(state);
        var option = listOf(BrassActionQueryService.availableActions(state).get("buildOptions")).stream()
                .filter(o -> type.equals(o.get("industryType")) && buildCity(type).equals(o.get("city")))
                .findFirst().orElseThrow();
        assertFalse(listOf(option.get(resource + "Sources")).stream().anyMatch(s -> "old".equals(s.get("id"))));
        assertJsonEquals(before, state);
        var invalid = List.<Map<String, Object>>of(Map.of("type", resource, "sourceType", "industry", "sourceId", "old", "requirementIndex", 0, "amount", 1));
        assertThrows(ApiException.class, () -> act(state, build(type, true, invalid)));
        assertJsonEquals(before, state);
        var valid = List.<Map<String, Object>>of(Map.of("type", resource, "sourceType", "market", "sourceId", "market:" + resource, "requirementIndex", 0, "amount", 1));
        var result = act(state, build(type, true, valid)).state();
        assertEquals(199, stat(result, "money"));
        assertEquals(10, stat(result, "incomeLevel"));
        assertEquals(1, listOf(viewMap(result.get("board")).get("industries")).size());
    }

    @Test void ownOverbuildStillTakesLowestPersonalTileAndLeavesHigherTileInSupply() throws Exception {
        var state = buildState("cotton_mill", 1, false, 0);
        var higher = definition("cotton_mill"); higher.put("id", "higher-tile"); higher.put("level", 2);
        viewMap(viewMap(state.get("playerBoards")).get("1")).put("cotton_mill", List.of(definition("cotton_mill"), higher));
        var result = act(state, build("cotton_mill", true, List.of())).state();
        assertEquals("new-tile", listOf(viewMap(result.get("board")).get("industries")).getFirst().get("sourceTileId"));
        assertEquals(List.of(higher), viewMap(viewMap(result.get("playerBoards")).get("1")).get("cotton_mill"));
    }

    @Test void coveringOwnTileCannotBypassCanalCityLimitForANewEmptySlot() throws Exception {
        var state = buildState("cotton_mill", 1, false, 0);
        String city = buildCity("cotton_mill");
        int occupied = buildSlot("cotton_mill");
        var slots = citySlotOptions().get(city);
        int other = java.util.stream.IntStream.range(0, slots.size()).filter(i -> i != occupied && slots.get(i).contains("cotton_mill")).findFirst().orElseThrow();
        var before = reload(state);
        assertThrows(ApiException.class, () -> act(state, command("build", Map.of("cardId", "card-a", "city", city, "industryType", "cotton_mill", "slotIndex", other))));
        assertJsonEquals(before, state);
    }

    @Test void overbuildCannotChangeIndustryTypeEvenOnAMixedSlot() throws Exception {
        var site = citySlotOptions().entrySet().stream().filter(e -> e.getValue().stream().anyMatch(s -> s.size() > 1)).findFirst().orElseThrow();
        int slot = java.util.stream.IntStream.range(0, site.getValue().size()).filter(i -> site.getValue().get(i).size() > 1).findFirst().orElseThrow();
        String oldType = site.getValue().get(slot).get(0), newType = site.getValue().get(slot).get(1);
        for (long owner : List.of(1L, 2L)) {
            var state = base(); setCard(state, site.getKey());
            var old = tile("old", owner, site.getKey(), oldType, 0); old.put("slotIndex", slot); old.put("flipped", true);
            viewMap(state.get("board")).put("industries", List.of(old));
            viewMap(state.get("market")).put("coal", List.of()); viewMap(state.get("market")).put("iron", List.of());
            setSupply(state, newType, definition(newType)); var before = reload(state);
            assertThrows(ApiException.class, () -> act(state, command("build", Map.of("cardId", "card-a", "city", site.getKey(), "slotIndex", slot, "industryType", newType))));
            assertJsonEquals(before, state);
        }
    }

    private static Map<String, Object> base() throws Exception {
        var state = opening(); state.put("round", 2);
        state.put("board", new LinkedHashMap<>(Map.of("industries", List.of(), "links", List.of())));
        state.put("turn", new LinkedHashMap<>(Map.of("actionsAllowed", 2, "actionsRemaining", 2, "actionsTaken", 0, "awaitingEndTurn", false, "startedAtRound", 2)));
        viewMap(viewMap(state.get("playerStats")).get("1")).put("money", 200);
        state.put("market", new LinkedHashMap<>(Map.of("coal", new ArrayList<>(FULL_COAL_MARKET), "iron", new ArrayList<>(FULL_IRON_MARKET), "beerMerchants", List.of())));
        setCard(state, "Birmingham"); return state;
    }
    private static Map<String, Object> buildState(String type, long owner, boolean flipped, int stock) throws Exception {
        var state = base(); setCard(state, buildCity(type)); setSupply(state, type, definition(type));
        var old = tile("old", owner, buildCity(type), type, stock); old.put("slotIndex", buildSlot(type)); old.put("flipped", flipped);
        viewMap(state.get("board")).put("industries", List.of(old)); return state;
    }
    private static String buildCity(String type) { return citySlotOptions().entrySet().stream().filter(e -> e.getValue().stream().anyMatch(s -> s.contains(type))).findFirst().orElseThrow().getKey(); }
    private static int buildSlot(String type) { var slots = citySlotOptions().get(buildCity(type)); return java.util.stream.IntStream.range(0, slots.size()).filter(i -> slots.get(i).contains(type)).findFirst().orElseThrow(); }
    private static String resource(String type) { return type.equals("coal_mine") ? "coal" : "iron"; }
    private static void setCard(Map<String, Object> state, String city) { state.put("hands", new LinkedHashMap<>(Map.of("1", List.of(Map.of("id", "card-a", "type", "location", "key", city)), "2", List.of()))); }
    private static void setSupply(Map<String, Object> state, String type, Map<String, Object> tile) { viewMap(viewMap(state.get("playerBoards")).get("1")).put(type, List.of(tile)); }
    private static Map<String, Object> definition(String type) { return new LinkedHashMap<>(Map.of("id", "new-tile", "industryType", type, "level", 1, "period", 0, "cost", 0, "coalCost", 0, "ironCost", 0, "resourceAmounts", List.of(0, 0))); }
    private static Map<String, Object> tile(String id, long owner, String city, String type, int stock) {
        var tile = new LinkedHashMap<String, Object>(Map.of("id", id, "ownerId", owner, "city", city, "industryType", type, "level", 1, "flipped", false));
        tile.put("coal", type.equals("coal_mine") ? stock : 0); tile.put("iron", type.equals("iron_works") ? stock : 0); tile.put("beer", type.equals("brewery") ? stock : 0);
        tile.put("incomeReward", 3); tile.put("flipType", List.of("cotton_mill", "manufacturer", "pottery").contains(type) ? "sell" : "deplete");
        return tile;
    }
    private static Map<String, Object> build(String type, boolean explicitSlot, List<Map<String, Object>> selections) {
        var payload = new LinkedHashMap<String, Object>(Map.of("cardId", "card-a", "city", buildCity(type), "industryType", type));
        if (explicitSlot) payload.put("slotIndex", buildSlot(type));
        if (!selections.isEmpty()) payload.put("resourceSelections", selections);
        return command("build", payload);
    }
    private static Map<String, Object> saleState() throws Exception {
        var state = base(); var tiles = new ArrayList<Map<String, Object>>();
        for (String id : List.of("sale-a", "sale-b", "sale-c")) { var sale = tile(id, 1, "Birmingham", "cotton_mill", 0); sale.put("saleBeerCost", 1); tiles.add(sale); }
        tiles.add(tile("brewery", 1, "Remote", "brewery", 5));
        viewMap(state.get("board")).put("industries", tiles);
        viewMap(state.get("board")).put("links", List.of(Map.of("from", "Birmingham", "to", "Oxford", "ownerId", 2L, "type", "canal"), Map.of("from", "Birmingham", "to", "Warrington", "ownerId", 2L, "type", "canal")));
        viewMap(state.get("market")).put("beerMerchants", List.of(new LinkedHashMap<>(Map.of("id", "merchant-a", "city", "Oxford", "beer", 1, "acceptedIndustryTypes", List.of("cotton_mill"), "reward", "2 Income")), new LinkedHashMap<>(Map.of("id", "merchant-b", "city", "Warrington", "beer", 1, "acceptedIndustryTypes", List.of("cotton_mill"), "reward", "5"))));
        return state;
    }
    private static Map<String, Object> sale(String tile, String merchant, String sourceType, String source, boolean first) {
        var payload = new LinkedHashMap<String, Object>(Map.of("tileId", tile, "merchantId", merchant, "resourceSelections", List.of(selection(sourceType, source, 1))));
        if (first) payload.put("cardId", "card-a"); return command("sell", payload);
    }
    private static Map<String, Object> selection(String type, String source, int amount) { return Map.of("type", "beer", "sourceType", type, "sourceId", source, "requirementIndex", 0, "amount", amount); }
    private static Map<String, Object> command(String type, Map<String, Object> payload) { return Map.of("type", type, "payload", payload); }
    private static Map<String, Object> merchant(Map<String, Object> state, String id) { return ((List<?>) viewMap(state.get("market")).get("beerMerchants")).stream().map(BrassJsonCodec::viewMap).filter(m -> id.equals(m.get("id"))).findFirst().orElseThrow(); }
    private static Map<String, Object> industry(Map<String, Object> state, String id) { return ((List<?>) viewMap(state.get("board")).get("industries")).stream().map(BrassJsonCodec::viewMap).filter(t -> id.equals(t.get("id"))).findFirst().orElseThrow(); }
    private static int stat(Map<String, Object> state, String key) { return parseInteger(viewMap(viewMap(state.get("playerStats")).get("1")).get(key), 0); }
}
