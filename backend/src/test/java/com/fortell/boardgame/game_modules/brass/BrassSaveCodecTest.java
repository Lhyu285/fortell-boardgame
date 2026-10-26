package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.codec.BrassSaveCodec;
import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot;
import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.*;
import static com.fortell.boardgame.game_modules.brass.BrassCompatibilityFixtures.*;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;
import static org.junit.jupiter.api.Assertions.*;

class BrassSaveCodecTest {
    final BrassSaveCodec codec = new BrassSaveCodec();

    @ParameterizedTest @ValueSource(ints = {2, 3, 4})
    void freshRoomsOfEverySupportedSizeHaveCompatibleTypedModels(int count) throws Exception {
        List<com.fortell.boardgame.models.RoomSeat> seats = new ArrayList<>();
        for (int i = 0; i < count; i++) seats.add(new com.fortell.boardgame.models.RoomSeat(i, (long) i + 1, "Player" + i));
        Map<String, Object> state = MODULE.onStart(ROOM, seats, MODULE.defaultConfig());
        var decoded = codec.readState(state);
        assertEquals(count, decoded.value().players().size());
        assertEquals(count, decoded.value().hands().size());
        assertEquals(1, decoded.value().turn().actionsAllowed());
        assertJsonEquals(state, codec.writeState(decoded));
    }

    @ParameterizedTest(name = "{0}") @MethodSource("com.fortell.boardgame.game_modules.brass.BrassCompatibilityFixtures#scenarios")
    void typedRoundTripPreservesSaveQueriesAndResumableCommands(Scenario scenario) throws Exception {
        Map<String, Object> original = scenario.state();
        Map<String, Object> restored = codec.writeState(codec.readState(original));
        assertTrue(original.equals(restored), "Unmodified saves must retain Java numeric types and nulls as well as JSON values");
        assertJsonEquals(original, restored);
        assertJsonEquals(BrassActionQueryService.availableActions(original), BrassActionQueryService.availableActions(restored));
        for (var command : scenario.commands()) {
            var expected = act(original, command);
            var actual = act(restored, command);
            assertJsonEquals(expected.state(), actual.state());
            assertJsonEquals(expected.event(), actual.event());
            assertSubset(mapOf(command.get("expected")), actual.state());
            original = reload(expected.state());
            restored = codec.writeState(codec.readState(reload(actual.state())));
        }
    }

    @Test void omittedNullAndEmptyValuesAreNotCanonicalizedIntoDifferentSaves() {
        Map<String, Object> empty = new LinkedHashMap<>();
        var absent = codec.readState(empty);
        assertEquals(Phase.WAITING, absent.value().phase());
        assertEquals(Era.CANAL, absent.value().era());
        assertEquals(Map.of(), codec.writeState(absent));
        Map<String, Object> mixed = new LinkedHashMap<>();
        for (String key : List.of("players", "hands", "playerStats", "deck", "board", "turn", "turnStartSnapshot", "incomeDebt", "phase", "era")) mixed.put(key, null);
        mixed.put("scoutPool", List.of()); mixed.put("discardPile", List.of()); mixed.put("futureField", null);
        assertEquals(mixed, codec.writeState(codec.readState(mixed)));
        mixed.put("turn", Map.of()); mixed.put("board", Map.of()); mixed.put("incomeDebt", Map.of());
        assertEquals(mixed, codec.writeState(codec.readState(mixed)));
    }

    @Test void nestedUnknownFieldsAndNumericRepresentationsSurviveAnActualTypedEdit() throws Exception {
        Map<String, Object> state = opening();
        state.put("futureRoot", Arrays.asList(null, new BigDecimal("1.2300"), Map.of("中文", true)));
        Map<String, Object> stats = viewMap(viewMap(state.get("playerStats")).get("1"));
        stats.put("money", 17L); stats.put("incomeLevel", "10"); stats.put("futureStats", Map.of("newFlag", true));
        viewMap(state.get("turn")).put("futureTurn", "preserve");
        viewMap(state.get("board")).put("futureBoard", Map.of("nested", List.of(1, 2)));
        viewMap(state.get("turnStartSnapshot")).put("futureSnapshot", "preserve");
        state.put("version", "9007199254740993");
        String original = JSON.writeValueAsString(state);
        var decoded = codec.readState(state);
        assertEquals(9007199254740993L, decoded.value().version());
        assertEquals(10, decoded.value().playerStats().get("1").incomeLevel());
        Map<String, PlayerStats> changedStats = new LinkedHashMap<>(decoded.value().playerStats());
        changedStats.put("1", changedStats.get("1").withMoney(23));
        Map<String, Object> result = codec.writeState(decoded.withValue(decoded.value().withPlayerStats(changedStats)));
        Map<String, Object> expected = mapOf(deepCopy(state));
        viewMap(viewMap(expected.get("playerStats")).get("1")).put("money", 23);
        assertJsonEquals(expected, result);
        assertEquals("10", viewMap(viewMap(result.get("playerStats")).get("1")).get("incomeLevel"));
        assertEquals(original, JSON.writeValueAsString(state));
        assertEquals(17L, viewMap(viewMap(codec.writeState(decoded).get("playerStats")).get("1")).get("money"));
    }

    @Test void tileExtensionsFollowTheirStableIdAcrossEditsReordersAndRemovals() throws Exception {
        Map<String, Object> state = opening();
        Map<String, Object> board = viewMap(state.get("board"));
        board.put("industries", new ArrayList<>(List.of(tile("a", 3), tile("b", 2))));
        var decoded = codec.readState(state);
        List<Industry> tiles = decoded.value().board().industries();
        var updated = decoded.value().withBoard(decoded.value().board().withIndustries(List.of(tiles.get(1), tiles.get(0).withCoal(1))));
        List<Map<String, Object>> result = listOf(viewMap(codec.writeState(decoded.withValue(updated)).get("board")).get("industries"));
        assertEquals(List.of("b", "a"), result.stream().map(item -> item.get("id")).toList());
        assertEquals("extension-b", result.getFirst().get("futureTile"));
        assertEquals("extension-a", result.get(1).get("futureTile"));
        assertEquals(1, result.get(1).get("coal"));
        var removed = decoded.value().withBoard(decoded.value().board().withIndustries(List.of(tiles.get(1))));
        assertEquals(1, listOf(viewMap(codec.writeState(decoded.withValue(removed)).get("board")).get("industries")).size());
        var ambiguous = decoded.value().withBoard(decoded.value().board().withIndustries(List.of(tiles.getFirst(), tiles.getFirst())));
        var failure = assertThrows(BrassSaveCodec.CodecException.class, () -> codec.writeState(decoded.withValue(ambiguous)));
        assertEquals("$.board.industries", failure.path());
    }

    @Test void envelopeModelInputAndEncodedCopiesCannotMutateEachOther() throws Exception {
        Map<String, Object> state = opening();
        var decoded = codec.readState(state);
        int money = decoded.value().playerStats().get("1").money();
        viewMap(viewMap(state.get("playerStats")).get("1")).put("money", -100);
        assertEquals(money, decoded.value().playerStats().get("1").money());
        assertThrows(UnsupportedOperationException.class, () -> decoded.value().playerStats().clear());
        assertThrows(UnsupportedOperationException.class, () -> decoded.value().hands().get("1").clear());
        assertThrows(UnsupportedOperationException.class, () -> decoded.value().playerBoards().get("1").clear());
        assertThrows(UnsupportedOperationException.class, () -> decoded.value().board().citySlots().values().iterator().next().getFirst().clear());
        Map<String, Object> first = codec.writeState(decoded);
        viewMap(viewMap(first.get("playerStats")).get("1")).put("money", -200);
        assertEquals(money, viewMap(viewMap(codec.writeState(decoded).get("playerStats")).get("1")).get("money"));
        var checkpoint = decoded.value().turnStartSnapshot();
        assertNotNull(checkpoint);
        Map<String, PlayerStats> changed = new LinkedHashMap<>(decoded.value().playerStats());
        changed.put("1", changed.get("1").withMoney(0));
        assertEquals(money, decoded.value().withPlayerStats(changed).turnStartSnapshot().playerStats().get("1").money());
    }

    @Test void numericLimitsEnumsWrongShapesAndNonJsonValuesFailWithAPath() {
        for (Object value : List.of(2147483648L, new BigDecimal("1.5"), "not-a-number")) {
            var failure = assertThrows(BrassSaveCodec.CodecException.class, () -> codec.readState(Map.of("playerStats", Map.of("1", Map.of("money", value)))));
            assertTrue(failure.path().contains("money"), failure::getMessage);
        }
        assertThrows(BrassSaveCodec.CodecException.class, () -> codec.readState(Map.of("era", "future-era")));
        assertThrows(BrassSaveCodec.CodecException.class, () -> codec.readState(Map.of("era", 0)));
        assertThrows(BrassSaveCodec.CodecException.class, () -> codec.readState(Map.of("hands", List.of())));
        assertThrows(BrassSaveCodec.CodecException.class, () -> codec.readState(Map.of("deck", List.of("not-a-card"))));
        assertThrows(BrassSaveCodec.CodecException.class, () -> codec.readState(Map.of("future", Double.NaN)));
        Map<String, Object> cyclic = new LinkedHashMap<>(); cyclic.put("future", cyclic);
        assertEquals("$.future", assertThrows(BrassSaveCodec.CodecException.class, () -> codec.readState(cyclic)).path());
        assertThrows(BrassSaveCodec.CodecException.class, () -> codec.readState(null));
        Map<String, Object> limits = Map.of("version", Long.MAX_VALUE, "playerStats", Map.of("1", Map.of("victoryPoints", Integer.MIN_VALUE, "money", Integer.MAX_VALUE)));
        assertEquals(limits, codec.writeState(codec.readState(limits)));
        Map<String, Object> tooDeep = new LinkedHashMap<>(), child = tooDeep;
        for (int i = 0; i < 132; i++) { Map<String, Object> next = new LinkedHashMap<>(); child.put("future", next); child = next; }
        assertThrows(BrassSaveCodec.CodecException.class, () -> codec.readState(tooDeep));
        assertThrows(BrassSaveCodec.CodecException.class, () -> codec.readState(Map.of("version", new java.math.BigInteger("9223372036854775808"))));
    }

    @Test void malformedBooleanFlagsCannotBeSilentlyCoerced() {
        for (Object raw : List.of("true", "false", 0, 1, 1.0)) {
            var error = assertThrows(BrassSaveCodec.CodecException.class, () -> codec.readState(Map.of("turn", Map.of("awaitingEndTurn", raw))));
            assertTrue(error.path().contains("awaitingEndTurn"), error::getMessage);
        }
    }

    @Test void normalizedRuleDefinitionsRoundTripAndAreDeeplyImmutable() throws Exception {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("industries", BrassRuleData.BUILDING_TILES); rules.put("routes", BrassRuleData.routes());
        rules.put("citySlots", BrassRuleData.citySlotOptions()); rules.put("merchants", BrassSetupData.merchantTilesForPlayerCount(2));
        rules.put("incomeByLevel", BrassRuleData.INCOME_BY_LEVEL);
        Map<String, Integer> loan = new LinkedHashMap<>(); BrassRuleData.INCOME_LEVEL_AFTER_LOAN.forEach((key, value) -> loan.put(String.valueOf(key), value));
        rules.put("incomeLevelAfterLoan", loan); rules.put("futureRule", Map.of("revision", 2));
        List<Map<String, Object>> cards = new ArrayList<>();
        for (String deck : List.of("draw_deck", "scout_pool")) for (Map<String, Object> raw : listOf(BrassRuleData.CARD_CONFIG.get(deck))) {
            Map<String, Object> info = viewMap(raw.get("info"));
            Map<String, Object> supply = new LinkedHashMap<>();
            supply.put("type", raw.get("type")); supply.put("city", info.get("city"));
            supply.put("industryTypes", BrassRuleData.configuredIndustryTypes(info.get("factory")));
            supply.put("wild", "all".equals(info.get("city")) || "all".equals(info.get("factory")));
            supply.put("amountByPlayerCount", raw.get("amount_by_player_count"));
            supply.put("amount", raw.get("amount")); supply.put("deck", deck);
            cards.add(supply);
        }
        rules.put("cards", cards); rules.put("markets", BrassRuleData.MAP_MARKETS);
        var decoded = codec.readRules(rules);
        assertJsonEquals(rules, codec.writeRules(decoded));
        assertEquals(BrassRuleData.BUILDING_TILES.get("coal_mine").size(), decoded.value().industries().get("coal_mine").size());
        assertThrows(UnsupportedOperationException.class, () -> decoded.value().industries().get("coal_mine").clear());
        assertThrows(UnsupportedOperationException.class, () -> decoded.value().citySlots().values().iterator().next().getFirst().clear());
        assertThrows(UnsupportedOperationException.class, () -> decoded.value().industries().get("coal_mine").getFirst().resourceAmounts().clear());
        assertEquals(cards.size(), decoded.value().cards().size());
        assertTrue(decoded.value().cards().stream().anyMatch(card -> card.wild() && card.amount() == 4));
        assertThrows(UnsupportedOperationException.class, () -> decoded.value().cards().getFirst().amountByPlayerCount().clear());
    }

    @Test void missingDefinitionDefaultsArePreservedWithoutInventingAPrice() {
        var definitions = codec.readRules(Map.of("industries", Map.of("coal_mine", List.of(Map.of("id", "old-tile", "industryType", "coal_mine")))));
        var tile = definitions.value().industries().get("coal_mine").getFirst();
        assertTrue(tile.canDevelop());
        assertNull(tile.cost(), "Missing price must stay distinguishable from a real zero price");
        assertFalse(viewMap(listOf(viewMap(codec.writeRules(definitions).get("industries")).get("coal_mine")).getFirst()).containsKey("canDevelop"));
    }

    @Test void newlyAddedTilesAndSharedButAcyclicExtensionValuesAreSafe() throws Exception {
        Map<String, Object> state = opening();
        Map<String, Object> extension = Map.of("nested", Arrays.asList(1, null));
        state.put("futureA", extension); state.put("futureB", extension);
        var decoded = codec.readState(state);
        Industry added = new Industry("new-tile", "supply-tile", 1, "A", "Walsall", 0, "coal_mine", 1, Era.RAIL,
                false, 5, 3, 0, 0, 0, 0, 0, 1, "deplete", 4, 3, false);
        var next = decoded.value().withBoard(decoded.value().board().withIndustries(List.of(added)));
        Map<String, Object> encoded = codec.writeState(decoded.withValue(next));
        var reloaded = codec.readState(encoded);
        assertEquals(added, reloaded.value().board().industries().getFirst());
        assertEquals(extension, encoded.get("futureA"));
        assertEquals(extension, encoded.get("futureB"));
        assertJsonEquals(state, codec.writeState(decoded));
    }

    static Map<String, Object> tile(String id, int coal) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id); result.put("city", "Walsall"); result.put("ownerId", 1L);
        result.put("industryType", "coal_mine"); result.put("era", "rail"); result.put("coal", coal);
        result.put("futureTile", "extension-" + id);
        return result;
    }
}
