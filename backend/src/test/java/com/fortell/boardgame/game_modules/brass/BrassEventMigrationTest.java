package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.ApiException;
import com.fortell.boardgame.models.UserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static com.fortell.boardgame.game_modules.brass.BrassCompatibilityFixtures.*;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;
import static org.junit.jupiter.api.Assertions.*;

class BrassEventMigrationTest {
    @ParameterizedTest @EnumSource(BrassActionType.class)
    void typedMetadataRetainsAllActionAndFlowWireContracts(BrassActionType type) throws Exception {
        var before = BrassQueryMigrationTest.baseState(); before.put("version", 3_000_000_000L);
        var source = BrassFlowAdapter.gameState(before);
        var record = new BrassEventModel(type, 1, "A", "summary", source, source, BrassTypedEvents.startVersion(source, type));
        var wire = BrassEventAdapter.encode(record, before, mapOf(deepCopy(before)), Map.of());
        assertEquals(1, wire.get("schemaVersion")); assertEquals(type.key, wire.get("type"));
        assertEquals(3_000_000_000L, wire.get("beforeVersion")); assertEquals(3_000_000_001L, wire.get("afterVersion"));
        assertEquals(type.businessAction ? "business_action" : "flow_operation", wire.get("kind"));
        assertEquals(type.businessAction ? 1 : null, wire.get("actionIndex"));
        assertEquals(type.businessAction ? "canal/2/1/1/3000000001" : null, wire.get("businessActionId"));
        assertEquals(type.businessAction && type != BrassActionType.SELL, wire.get("actionCompleted"));
        assertTrue(wire.containsKey("businessActionId")); assertTrue(wire.containsKey("actionIndex"));
        assertEquals(Map.of(), wire.get("changes"));
    }

    @Test void operationSummaryIsNotInferredFromTheLatestRoomNoticeOrPreviousUiRecord() throws Exception {
        var before = BrassQueryMigrationTest.baseState(); before.put("notices", List.of("old notice"));
        before.put("actionLog", List.of(Map.of("summary", "old action")));
        var after = BrassStateDocument.executionCopy(before); after.describeOperation("this operation");
        BrassRecordSupport.appendNotice(after, "later resource notice");
        var result = BrassActionHistory.record(before, after, new UserSummary(1, "A"), BrassActionCommand.decode("loan", Map.of("cardId", "card-a")));
        assertEquals("this operation", result.event().get("summary"));
        assertEquals("this operation", listOf(result.state().get("actionLog")).getFirst().get("summary"));
        assertEquals("玩家A this operation", result.message());
        var noSummary = BrassActionHistory.record(before, reload(before), new UserSummary(1, "A"), BrassActionCommand.decode("loan", Map.of()));
        assertEquals(BrassActionType.LOAN.label, noSummary.event().get("summary"));
    }

    @Test void eventsKeepOpaqueInputAndChangedComponentExtensionsButNeverAliasSnapshots() throws Exception {
        var before = BrassQueryMigrationTest.baseState(); before.put("version", 5L);
        viewMap(stateStats(before)).put("future", new LinkedHashMap<>(Map.of("values", new ArrayList<>(List.of("old")))));
        var original = reload(before); var after = BrassStateDocument.executionCopy(before);
        viewMap(stateStats(after)).put("future", new LinkedHashMap<>(Map.of("values", new ArrayList<>(List.of("new")))));
        var input = new LinkedHashMap<String, Object>(); input.put("futureInput", new ArrayList<>(List.of("retain"))); input.put("nullable", null);
        var result = BrassActionHistory.record(before, after, new UserSummary(1, "A"), BrassActionCommand.decode("skip", input));
        assertEquals(input, result.event().get("input"));
        var changes = viewMap(result.event().get("changes"));
        assertTrue(changes.containsKey("playerStats")); assertFalse(changes.containsKey("actionLog"));
        var beforeStats = viewMap(viewMap(changes.get("playerStats")).get("before"));
        var afterStats = viewMap(viewMap(changes.get("playerStats")).get("after"));
        assertEquals("old", objectList(viewMap(viewMap(beforeStats.get("1")).get("future")).get("values")).getFirst());
        ((List<?>) viewMap(result.event().get("input")).get("futureInput")).clear();
        viewMap(afterStats.get("1")).clear(); viewMap(beforeStats.get("1")).clear();
        assertEquals(100, stateStats(result.state()).get("money")); assertEquals(List.of("retain"), input.get("futureInput"));
        assertJsonEquals(original, before);
        var ui = listOf(result.state().get("actionLog")).getFirst();
        assertFalse(ui.containsKey("input")); assertFalse(ui.containsKey("changes")); assertFalse(ui.containsKey("businessActionId"));
    }

    @Test void saleStartVersionSurvivesReloadAndFallsBackForOldSavesWithoutAnId() throws Exception {
        var before = BrassQueryMigrationTest.saleState(); before.put("version", 20L);
        var input = Map.of("cardId", "card-a", "tileId", "sale", "merchantId", "merchant", "resourceSelections", List.of(
                BrassQueryMigrationTest.selection("beer", "industry", "own-beer", 0), BrassQueryMigrationTest.selection("beer", "industry", "other-beer", 0),
                BrassQueryMigrationTest.selection("beer", "merchant", "merchant", 0)));
        var first = act(before, Map.of("type", "sell", "payload", input));
        assertEquals(false, first.event().get("actionCompleted"));
        var done = act(reload(first.state()), Map.of("type", "end_sell", "payload", Map.of()));
        assertEquals(first.event().get("businessActionId"), done.event().get("businessActionId"));
        assertEquals(true, done.event().get("actionCompleted"));
        assertFalse(viewMap(done.state().get("turn")).containsKey("sellActionStartVersion"));
        var old = reload(first.state()); viewMap(old.get("turn")).remove("sellActionStartVersion");
        long fallback = asLong(old.get("version")) + 1;
        assertEquals(fallback, BrassTypedEvents.startVersion(BrassFlowAdapter.gameState(old), BrassActionType.END_SELL));
    }

    @Test void failedRealActionAndSimulationDoNotAppendEventsOrUiHistory() throws Exception {
        var state = BrassQueryMigrationTest.baseState(); var before = reload(state);
        assertThrows(ApiException.class, () -> act(state, Map.of("type", "build", "payload", Map.of("cardId", "card-a", "city", "missing", "industryType", "pottery"))));
        var simulation = BrassStateDocument.simulationCopy(state);
        var simulatedBefore = reload(simulation);
        assertThrows(IllegalArgumentException.class, () -> BrassActionHistory.record(state, simulation, new UserSummary(1, "A"), BrassActionCommand.decode("skip", Map.of())));
        assertJsonEquals(simulatedBefore, simulation); assertJsonEquals(before, state);
    }

    @Test void uiHistoryIsBoundedWithoutUsingItAsThePersistentEventSource() throws Exception {
        var before = BrassQueryMigrationTest.baseState(); var after = BrassStateDocument.executionCopy(before);
        var records = new ArrayList<Map<String, Object>>(); for (int i = 0; i < 35; i++) records.add(Map.of("summary", "old-" + i));
        after.put("actionLog", records); after.describeOperation("new");
        var result = BrassActionHistory.record(before, after, new UserSummary(1, "A"), BrassActionCommand.decode("skip", Map.of()));
        assertEquals(30, listOf(result.state().get("actionLog")).size()); assertEquals("new", result.event().get("summary"));
        assertEquals("old-28", listOf(result.state().get("actionLog")).getLast().get("summary"));
    }

    private static Map<String, Object> stateStats(Map<String, Object> state) { return viewMap(viewMap(state.get("playerStats")).get("1")); }
}
