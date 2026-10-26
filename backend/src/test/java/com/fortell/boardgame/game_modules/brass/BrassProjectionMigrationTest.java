package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.UserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.*;
import java.util.stream.Stream;
import static com.fortell.boardgame.game_modules.brass.BrassCompatibilityFixtures.*;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;
import static org.junit.jupiter.api.Assertions.*;

class BrassProjectionMigrationTest {
    static Stream<Scenario> saves() throws Exception { return scenarios(); }
    @ParameterizedTest @MethodSource("saves")
    void everyPhaseProjectsOnlyOwnCardsAndPublicQuerySummary(Scenario scenario) throws Exception {
        var state = scenario.state(); var before = reload(state);
        for (UserSummary viewer : Arrays.asList(null, new UserSummary(1, "A"), new UserSummary(2, "B"), new UserSummary(99, "Spectator"))) {
            var visible = BrassViewerProjection.forViewer(state, viewer);
            assertFalse(visible.containsKey("turnStartSnapshot")); assertFalse(visible.containsKey("_candidateSimulation"));
            assertEquals(objectList(state.get("deck")).size(), objectList(visible.get("deck")).size());
            assertTrue(listOf(visible.get("deck")).stream().allMatch(card -> card.equals(Map.of("hidden", true))));
            for (var entry : viewMap(state.get("hands")).entrySet()) {
                var hand = viewMap(visible.get("hands")).get(entry.getKey());
                if (viewer != null && entry.getKey().equals(String.valueOf(viewer.id()))) assertEquals(entry.getValue(), hand);
                else {
                    assertEquals(objectList(entry.getValue()).size(), objectList(hand).size());
                    assertTrue(listOf(hand).stream().allMatch(card -> card.equals(Map.of("hidden", true))));
                }
            }
            var actions = viewMap(visible.get("availableActions"));
            if (viewer != null && viewer.id() == asLong(state.get("currentPlayerId"))) assertEquals(state.get("availableActions"), actions);
            else {
                assertEquals(List.of(), actions.get("actions"));
                assertTrue(Set.of("currentPlayerId", "maintenanceReady", "handSize", "sellInProgress", "sellCount", "incomeDebt", "actions").containsAll(actions.keySet()));
            }
        }
        assertJsonEquals(before, state);
    }

    @Test void actorAndSpectatorProjectionsAreDeeplyDetachedIncludingUnknownPublicExtensions() throws Exception {
        var state = opening(); state.put("futurePublic", new LinkedHashMap<>(Map.of("rows", new ArrayList<>(List.of("keep")))));
        var before = reload(state);
        var actor = BrassViewerProjection.forViewer(state, new UserSummary(1, "A"));
        var spectator = BrassViewerProjection.forViewer(state, null);
        ((List<?>) viewMap(actor.get("futurePublic")).get("rows")).clear();
        viewMap(objectList(viewMap(actor.get("hands")).get("1")).getFirst()).put("name", "changed");
        ((List<?>) viewMap(actor.get("availableActions")).get("buildOptions")).clear();
        viewMap(spectator.get("market")).clear();
        assertEquals(List.of("keep"), viewMap(spectator.get("futurePublic")).get("rows"));
        assertJsonEquals(before, state);
    }

    @Test void unknownCandidateFieldsAreNotForwardedToOtherPlayersOrAnonymousViewers() throws Exception {
        var state = opening(); viewMap(state.get("availableActions")).put("futurePrivateCandidates", List.of("secret-card"));
        for (UserSummary viewer : Arrays.asList(null, new UserSummary(2, "B"), new UserSummary(99, "Spectator"))) {
            String encoded = JSON.writeValueAsString(BrassViewerProjection.forViewer(state, viewer));
            assertFalse(encoded.contains("secret-card")); assertFalse(encoded.contains("futurePrivateCandidates"));
        }
        assertTrue(viewMap(BrassViewerProjection.forViewer(state, new UserSummary(1, "A")).get("availableActions")).containsKey("futurePrivateCandidates"));
    }

    @Test void sparseHandsKeepTheirShapesAndAnonymousViewerNeverOwnsAnEmptyIdentity() {
        var state = new LinkedHashMap<String, Object>(); var hands = new LinkedHashMap<String, Object>();
        hands.put("1", null); hands.put("2", "legacy-non-list"); hands.put("", List.of(Map.of("id", "must-hide")));
        state.put("hands", hands); state.put("deck", null);
        var actor = BrassViewerProjection.forViewer(state, new UserSummary(1, "A"));
        assertNull(viewMap(actor.get("hands")).get("1")); assertEquals(List.of(), viewMap(actor.get("hands")).get("2"));
        var anonymous = BrassViewerProjection.forViewer(state, null);
        assertEquals(List.of(Map.of("hidden", true)), viewMap(anonymous.get("hands")).get(""));
        assertEquals(List.of(), anonymous.get("deck"));
    }

    @Test void derivedDisplayAliasesPreserveRawIdsAndUnknownFields() throws Exception {
        var state = BrassQueryMigrationTest.baseState();
        var tile = BrassQueryMigrationTest.tile("tile", 1, "Birmingham", "coal_mine", 2);
        tile.put("ownerId", "1"); tile.put("future", List.of("keep")); tile.put("victoryPoints", "5");
        var link = new LinkedHashMap<String, Object>(Map.of("id", "link", "ownerId", "2", "from", "Birmingham", "to", "Dudley", "type", "canal"));
        viewMap(state.get("board")).put("industries", List.of(tile)); viewMap(state.get("board")).put("links", List.of(link));
        BrassStateProjection.refreshBrassDerivedState(state);
        var projectedTile = listOf(viewMap(state.get("board")).get("industries")).getFirst();
        assertEquals("1", projectedTile.get("ownerPlayerId")); assertEquals("5", projectedTile.get("victoryPoints"));
        assertEquals(5, projectedTile.get("vp")); assertEquals(2, projectedTile.get("remainingResource"));
        assertEquals(List.of("keep"), projectedTile.get("future"));
        var projectedLink = listOf(viewMap(state.get("board")).get("links")).getFirst();
        assertEquals("2", projectedLink.get("ownerPlayerId")); assertEquals("link", projectedLink.get("builtLinkId"));
    }

    @Test void displayEstimateRetainsExistingEraFilterWithoutApplyingActualScoringFlags() {
        var tiles = List.of(new BrassFlowModel.ScoringIndustry(1, "A", "pottery", "canal", 2, true, true, 5, 1),
                new BrassFlowModel.ScoringIndustry(1, "A", "pottery", "rail", 2, true, false, 8, 1),
                new BrassFlowModel.ScoringIndustry(2, "A", "pottery", "canal", 2, true, false, 20, 1));
        var links = List.of(new BrassProjectionModel.LinkScore(1, "canal", 3), new BrassProjectionModel.LinkScore(1, "rail", 7));
        assertEquals(8, BrassTypedProjection.estimate("canal", "canal", 1, tiles, links));
        assertEquals(15, BrassTypedProjection.estimate("rail", "rail", 1, tiles, links));
    }
}
