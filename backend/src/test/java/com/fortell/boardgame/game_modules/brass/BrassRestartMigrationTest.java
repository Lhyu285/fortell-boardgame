package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.ApiException;
import com.fortell.boardgame.models.UserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static com.fortell.boardgame.game_modules.brass.BrassCompatibilityFixtures.*;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;
import static org.junit.jupiter.api.Assertions.*;

class BrassRestartMigrationTest {
    @Test void snapshotIsDeepDetachedAndNeverNestsThePreviousSnapshot() throws Exception {
        var state = opening();
        state.put("extension", new ArrayList<>(List.of(new LinkedHashMap<>(Collections.singletonMap("null", null)))));
        state.put("turnStartSnapshot", Map.of("old", true));
        var before = reload(state);
        var snapshot = BrassTurnService.snapshotOf(state);
        assertFalse(snapshot.containsKey("turnStartSnapshot"));
        ((List<?>) snapshot.get("extension")).clear();
        listOf(viewMap(snapshot.get("hands")).get("1")).getFirst().put("id", "changed");
        assertJsonEquals(before, state);
    }

    @ParameterizedTest @ValueSource(longs = {1, 2})
    void restartRestoresResourcesCardsMoneyAndMetadataButAdvancesVersionAndAudit(long resourceOwner) throws Exception {
        var state = checkpointState();
        var source = listOf(viewMap(state.get("board")).get("industries")).getFirst();
        source.put("ownerId", resourceOwner); source.put("ownerName", resourceOwner == 1 ? "A" : "B");
        viewMap(state.get("board")).put("industries", List.of(source));
        BrassStateProjection.updateMaintenanceFlag(state);
        state.put("turnStartSnapshot", BrassTurnService.snapshotOf(state));
        var original = reload(state);
        var loan = act(state, command("loan", Map.of("cardId", "card-a")));
        var network = act(reload(loan.state()), command("network", Map.of("cardId", "card-b", "from", "Birmingham", "to", "Dudley",
                "resourceSelections", List.of(Map.of("type", "coal", "sourceType", "industry", "sourceId", "own-coal", "amount", 1)))));
        assertEquals(0, listOf(viewMap(network.state().get("board")).get("industries")).getFirst().get("coal"));
        assertEquals(1, listOf(viewMap(network.state().get("board")).get("links")).size());
        var changed = reload(network.state());
        var restart = act(reload(network.state()), command("restart_turn", Map.of()));
        for (String field : List.of("era", "round", "turnOrder", "currentPlayerId", "turn", "playerStats", "hands", "deck",
                "discardPile", "scoutPool", "playerBoards", "developments", "board", "market", "extension"))
            assertJsonEquals(original.get(field), restart.state().get(field));
        assertEquals(43L, asLong(restart.state().get("version")));
        assertEquals(42L, asLong(restart.event().get("beforeVersion"))); assertEquals(43L, asLong(restart.event().get("afterVersion")));
        assertEquals(40L, asLong(restart.event().get("restoredFromVersion")));
        assertEquals("flow_operation", restart.event().get("kind")); assertNull(restart.event().get("businessActionId"));
        assertJsonEquals(original.get("turnStartSnapshot"), restart.state().get("turnStartSnapshot"));
        assertJsonEquals(changed, network.state()); assertJsonEquals(original, state);
        var restored = reload(restart.state());
        assertThrows(ApiException.class, () -> act(restart.state(), command("restart_turn", Map.of())));
        assertJsonEquals(restored, restart.state());
        var next = act(reload(restart.state()), command("skip", Map.of("cardId", "card-a")));
        assertEquals(44L, asLong(next.state().get("version"))); assertEquals(1, viewMap(next.state().get("turn")).get("actionsTaken"));
    }

    @Test void restoredLiveBoardSnapshotAndOriginalRemainIndependent() throws Exception {
        var state = checkpointState();
        var loan = act(state, command("loan", Map.of("cardId", "card-a")));
        var result = act(loan.state(), command("restart_turn", Map.of())).state();
        var snapshot = mapOf(deepCopy(result.get("turnStartSnapshot")));
        listOf(viewMap(result.get("board")).get("industries")).getFirst().put("coal", 99);
        assertJsonEquals(snapshot, result.get("turnStartSnapshot"));
        assertEquals(1, listOf(viewMap(state.get("board")).get("industries")).getFirst().get("coal"));
    }

    @Test void restartNeverExposesPrivateCheckpointOrOtherHandsToAnyViewer() throws Exception {
        var state = checkpointState();
        var loan = act(state, command("loan", Map.of("cardId", "card-a")));
        var restored = act(reload(loan.state()), command("restart_turn", Map.of())).state();
        for (long id : List.of(1L, 2L, 99L)) {
            var visible = MODULE.stateForViewer(restored, new UserSummary(id, "viewer"));
            assertFalse(visible.containsKey("turnStartSnapshot"));
            if (id != 1) assertTrue(listOf(viewMap(visible.get("hands")).get("1")).stream().allMatch(card -> Boolean.TRUE.equals(card.get("hidden"))));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "null", "empty", "no-actions", "negative-actions"})
    void unavailableRestartDoesNotModifyTheState(String reason) throws Exception {
        var state = checkpointState();
        viewMap(state.get("turn")).put("actionsTaken", 1);
        switch (reason) {
            case "missing" -> state.remove("turnStartSnapshot");
            case "null" -> state.put("turnStartSnapshot", null);
            case "empty" -> state.put("turnStartSnapshot", Map.of());
            case "no-actions" -> viewMap(state.get("turn")).put("actionsTaken", 0);
            case "negative-actions" -> viewMap(state.get("turn")).put("actionsTaken", -1);
        }
        var before = reload(state);
        assertThrows(ApiException.class, () -> BrassTurnService.restartTurn(state, new UserSummary(1, "A")));
        assertJsonEquals(before, state);
    }

    @Test void previousPlayerCannotRestartAfterEndingTurn() throws Exception {
        var state = checkpointState();
        var skipped = act(state, command("skip", Map.of("cardId", "card-a"))).state();
        skipped = act(skipped, command("skip", Map.of("cardId", "card-b"))).state();
        var ended = act(skipped, command("end_turn", Map.of())).state();
        assertEquals(2L, asLong(ended.get("currentPlayerId")));
        var before = reload(ended);
        assertThrows(ApiException.class, () -> MODULE.onTransition(ROOM, SEATS, MODULE.defaultConfig(), ended, new UserSummary(1, "A"), "restart_turn", Map.of(), List.of()));
        assertJsonEquals(before, ended);
    }

    @Test void counterUpdatesPreserveRawAllowancesAndExtensionsAndFreshTurnsUseRawRound() throws Exception {
        var state = checkpointState();
        var turn = viewMap(state.get("turn")); turn.put("actionsAllowed", "2"); turn.put("extension", null);
        BrassTurnService.consumeAction(state, new UserSummary(1, "A"));
        assertEquals("2", viewMap(state.get("turn")).get("actionsAllowed"));
        assertTrue(viewMap(state.get("turn")).containsKey("extension"));
        assertEquals(1, viewMap(state.get("turn")).get("actionsRemaining"));
        state.put("round", "2"); BrassTurnService.beginTurn(state, "new");
        assertEquals("2", viewMap(state.get("turn")).get("startedAtRound"));
        assertFalse(viewMap(state.get("turn")).containsKey("extension"));
    }

    private static Map<String, Object> checkpointState() throws Exception {
        var state = opening(); state.put("era", "rail"); state.put("round", 2); state.put("version", 40L);
        state.put("turn", new LinkedHashMap<>(Map.of("actionsAllowed", 2, "actionsRemaining", 2, "actionsTaken", 0, "awaitingEndTurn", false, "startedAtRound", 2)));
        state.put("hands", new LinkedHashMap<>(Map.of("1", new ArrayList<>(List.of(Map.of("id", "card-a", "type", "location", "key", "Birmingham"),
                Map.of("id", "card-b", "type", "location", "key", "Oxford"))), "2", new ArrayList<>(List.of(Map.of("id", "private-other", "type", "location", "key", "Oxford"))))));
        state.put("deck", new ArrayList<>());
        viewMap(viewMap(state.get("playerStats")).get("1")).put("money", 100);
        var coal = new LinkedHashMap<String, Object>(Map.of("id", "own-coal", "ownerId", 1L, "ownerName", "A", "city", "Birmingham",
                "industryType", "coal_mine", "coal", 1, "incomeReward", 4, "flipped", false, "level", 2));
        coal.put("extension", new LinkedHashMap<>(Collections.singletonMap("nullable", null)));
        state.put("board", new LinkedHashMap<>(Map.of("industries", new ArrayList<>(List.of(coal)), "links", new ArrayList<>())));
        state.put("extension", new LinkedHashMap<>(Collections.singletonMap("nullable", null)));
        BrassStateProjection.updateMaintenanceFlag(state);
        state.put("turnStartSnapshot", BrassTurnService.snapshotOf(state));
        return state;
    }
    private static Map<String, Object> command(String type, Map<String, Object> payload) { return Map.of("type", type, "payload", payload); }
}
