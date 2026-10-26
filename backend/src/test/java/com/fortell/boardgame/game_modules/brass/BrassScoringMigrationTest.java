package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.ApiException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.fortell.boardgame.game_modules.brass.BrassCompatibilityFixtures.*;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;
import static org.junit.jupiter.api.Assertions.*;

class BrassScoringMigrationTest {
    @Test void scoringAwardsLinksThenIndustriesOnceAndPreservesMetadata() throws Exception {
        var state = BrassFlowMigrationTest.flowState(2, "canal", 10);
        var first = industry("same-id", 1, "A", true, 5, 2);
        var second = industry("same-id", 2, "B", true, 7, 3);
        var unused = industry("unflipped", 1, "A", false, 99, 100);
        var link = link(1, "A", "B", "canal");
        viewMap(state.get("board")).put("industries", List.of(first, second, unused));
        viewMap(state.get("board")).put("links", List.of(link, link(2, "A", "B", "rail")));
        var prior = reload(state);
        BrassScoringService.scoreCurrentEra(state);
        assertEquals(10, points(state, "1")); assertEquals(7, points(state, "2"));
        assertEquals(List.of("link", "industry", "industry"), listOf(state.get("eraScores")).stream().map(s -> s.get("type")).toList());
        var tiles = listOf(viewMap(state.get("board")).get("industries"));
        assertEquals(List.of("owner-1"), tiles.get(0).get("extension"));
        assertEquals(List.of("owner-2"), tiles.get(1).get("extension"));
        assertEquals(true, tiles.get(0).get("industryVpScored")); assertEquals(true, tiles.get(1).get("industryVpScored"));
        assertFalse(tiles.get(2).containsKey("industryVpScored"));
        assertEquals(5, listOf(viewMap(state.get("board")).get("links")).getFirst().get("score"));
        var scoredHistory = deepCopy(state.get("eraScores"));
        BrassScoringService.scoreCurrentEra(state);
        assertEquals(10, points(state, "1")); assertEquals(7, points(state, "2"));
        assertJsonEquals(scoredHistory, state.get("eraScores"));
        assertJsonEquals(prior.get("hands"), state.get("hands"));
    }

    @Test void previouslyScoredHigherLevelIndustryDoesNotScoreAgainInRail() throws Exception {
        var state = BrassFlowMigrationTest.flowState(2, "canal", 10);
        var tile = industry("retained", 1, "A", true, 5, 2);
        viewMap(state.get("board")).put("industries", List.of(tile));
        BrassScoringService.scoreCurrentEra(state);
        assertEquals(5, points(state, "1"));
        state.put("era", "rail");
        BrassScoringService.scoreCurrentEra(state);
        assertEquals(5, points(state, "1"));
        // A retained canal tile that flips only in rail must still receive its first VP award.
        tile = industry("later", 2, "B", true, 7, 1);
        var tiles = listOf(viewMap(state.get("board")).get("industries")); tiles.add(tile);
        viewMap(state.get("board")).put("industries", tiles);
        BrassScoringService.scoreCurrentEra(state);
        assertEquals(7, points(state, "2"));
    }

    @Test void roadPointsUseFlippedIndustriesOfBothOwnersAndClampNegativeIndustryPoints() {
        var tiles = List.of(industry("a", 1, "A", true, 1, 2), industry("b", 2, "A", true, 1, -20),
                industry("c", 2, "B", false, 1, 100));
        var typed = BrassFlowAdapter.industries(tiles);
        var markets = List.of(new BrassFlowModel.MarketRoad("A", 1), new BrassFlowModel.MarketRoad("A", 99),
                new BrassFlowModel.MarketRoad("B", 2));
        assertEquals(3, BrassTypedScoring.endpoint(typed, markets, "A"));
        assertEquals(5, BrassTypedScoring.route(typed, markets, "A", "B"));
        assertEquals(0, BrassTypedScoring.endpoint(typed, markets, "Unknown"));
        var plan = BrassTypedScoring.score(typed, List.of(), markets, "rail");
        assertThrows(UnsupportedOperationException.class, () -> plan.awards().clear());
    }

    @Test void winnerTiesNegativeScoresAndRawIdentityShapesRemainCompatible() throws Exception {
        var state = BrassFlowMigrationTest.flowState(3, "rail", 9);
        viewMap(viewMap(state.get("playerStats")).get("1")).put("victoryPoints", -8);
        viewMap(viewMap(state.get("playerStats")).get("2")).put("victoryPoints", "-4");
        viewMap(viewMap(state.get("playerStats")).get("3")).put("victoryPoints", -4);
        var players = listOf(state.get("players")); players.get(1).put("userId", "2"); players.get(1).put("username", null);
        state.put("players", players);
        var before = reload(state);
        var winners = BrassScoringService.winnersOf(state);
        assertEquals(List.of("2", 3L), winners.stream().map(w -> w.get("userId")).toList());
        assertNull(winners.getFirst().get("username"));
        assertEquals(-4, winners.getFirst().get("victoryPoints"));
        assertJsonEquals(before, state);
        assertTrue(BrassTypedScoring.winnerIndices(List.of()).isEmpty());
    }

    @Test void scoringHistoryKeepsNewEntriesFirstAndOnlyTrimsTheOldTail() throws Exception {
        var state = BrassFlowMigrationTest.flowState(2, "rail", 10);
        var history = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 80; i++) history.add(Map.of("extension", "old-" + i));
        state.put("eraScores", history);
        viewMap(state.get("board")).put("industries", List.of(industry("new", 1, "A", true, 3, 1)));
        BrassScoringService.scoreCurrentEra(state);
        var scores = listOf(state.get("eraScores"));
        assertEquals(80, scores.size()); assertEquals("industry", scores.getFirst().get("type"));
        assertEquals("old-0", scores.get(1).get("extension")); assertEquals("old-78", scores.getLast().get("extension"));
    }

    @Test void finalMaintenanceFinishesGameOnceAndRejectsFurtherActions() throws Exception {
        var state = BrassFlowMigrationTest.flowState(2, "rail", 10);
        state.put("currentPlayerId", 1L);
        BrassTurnService.enterEraEnding(state);
        viewMap(state.get("board")).put("industries", List.of(industry("winner", 2, "A", true, 9, 1)));
        var before = reload(state);
        var result = act(state, Map.of("type", "maintain_era", "payload", Map.of()));
        assertEquals("finished", result.state().get("phase"));
        assertEquals(2L, asLong(listOf(result.state().get("winners")).getFirst().get("userId")));
        assertTrue(viewMap(result.state().get("turn")).isEmpty()); assertNull(result.state().get("turnStartSnapshot"));
        var finished = reload(result.state());
        assertThrows(ApiException.class, () -> act(result.state(), Map.of("type", "maintain_era", "payload", Map.of())));
        assertJsonEquals(finished, result.state()); assertJsonEquals(before, state);
    }

    private static int points(Map<String, Object> state, String id) { return parseInteger(viewMap(viewMap(state.get("playerStats")).get(id)).get("victoryPoints"), 0); }
    private static Map<String, Object> industry(String id, long owner, String city, boolean flipped, int vp, int road) {
        var tile = new LinkedHashMap<String, Object>();
        tile.put("id", id); tile.put("ownerId", owner); tile.put("ownerName", "P" + owner); tile.put("city", city);
        tile.put("industryType", "cotton_mill"); tile.put("era", "canal"); tile.put("level", 2);
        tile.put("flipped", flipped); tile.put("victoryPoints", vp); tile.put("roadPoints", road); tile.put("extension", List.of("owner-" + owner));
        return tile;
    }
    private static Map<String, Object> link(long owner, String from, String to, String type) {
        return new LinkedHashMap<>(Map.of("ownerId", owner, "ownerName", "P" + owner, "from", from, "to", to, "type", type));
    }
}
