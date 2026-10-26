package com.fortell.boardgame.game_modules.incan_gold;

import com.fortell.boardgame.models.GameType;
import com.fortell.boardgame.models.RoomEntity;
import com.fortell.boardgame.models.RoomSeat;
import com.fortell.boardgame.models.RoomStatus;
import com.fortell.boardgame.models.UserSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncanGoldGameModuleTest {
    private final IncanGoldGameModule module = new IncanGoldGameModule();

    @Test
    void exposesTwoToEightPlayersAndDisabledExpansionByDefault() {
        assertEquals(GameType.INCAN_GOLD, module.gameType());
        assertEquals("incan_gold", module.descriptor().key());
        assertEquals(2, module.descriptor().minPlayers());
        assertEquals(8, module.descriptor().maxPlayers());
        assertEquals(false, module.defaultConfig().get("thievesExpansion"));
        assertEquals(false, module.defaultConfig().get("eventsExpansion"));
    }

    @Test
    void distributesTreasureInWholeGreenStonesAndKeepsRemainderOnRoad() {
        List<RoomSeat> seats = seats(4);
        Map<String, Object> state = started(seats, false);
        state.put("deck", new ArrayList<>(List.of(card("treasure-9", "treasure", Map.of("value", 9)))));

        module.onAction(room(), seats, module.defaultConfig(), state, user(1), "draw_exploration_card", Map.of(), List.of());
        for (int id = 1; id <= 3; id++) {
            module.onAction(room(), seats, module.defaultConfig(), state, user(id), "choose_expedition_action", Map.of("choice", "return"), List.of());
        }
        module.onAction(room(), seats, module.defaultConfig(), state, user(4), "choose_expedition_action", Map.of("choice", "continue"), List.of());

        assertEquals(1, state.get("roadTreasure"));
        assertEquals(2, player(state, 1).get("totalTreasure"));
        assertEquals(2, player(state, 2).get("totalTreasure"));
        assertEquals(2, player(state, 3).get("totalTreasure"));
        assertEquals(2, player(state, 4).get("roundTreasure"));
        assertEquals("EXPLORATION", state.get("phase"));
        assertEquals(3, ((List<?>) ((Map<?, ?>) state.get("lastSettlement")).get("players")).size());
    }

    @Test
    void soleReturningPlayerClaimsArtifactsBeforeNextExploration() {
        List<RoomSeat> seats = seats(2);
        Map<String, Object> state = started(seats, false);
        state.put("deck", new ArrayList<>(List.of(card("artifact-5", "artifact", Map.of("value", 5)))));

        module.onAction(room(), seats, module.defaultConfig(), state, user(1), "draw_exploration_card", Map.of(), List.of());
        module.onAction(room(), seats, module.defaultConfig(), state, user(1), "choose_expedition_action", Map.of("choice", "return"), List.of());
        module.onAction(room(), seats, module.defaultConfig(), state, user(2), "choose_expedition_action", Map.of("choice", "continue"), List.of());

        List<Map<String, Object>> artifacts = cards(player(state, 1).get("artifacts"));
        assertEquals(1, artifacts.size());
        assertEquals(5, artifacts.getFirst().get("value"));
        assertTrue(Boolean.FALSE.equals(player(state, 1).get("active")));
        assertTrue(Boolean.TRUE.equals(player(state, 2).get("active")));
    }

    @Test
    void automaticallyRevealsNextCardAfterReturnSettlement() {
        List<RoomSeat> seats = seats(2);
        Map<String, Object> state = started(seats, false);
        state.put("deck", new ArrayList<>(List.of(
                card("treasure-4", "treasure", Map.of("value", 4)),
                card("artifact-5", "artifact", Map.of("value", 5))
        )));

        module.onAction(room(), seats, module.defaultConfig(), state, user(1), "draw_exploration_card", Map.of(), List.of());
        module.onAction(room(), seats, module.defaultConfig(), state, user(1), "choose_expedition_action", Map.of("choice", "return"), List.of());
        module.onAction(room(), seats, module.defaultConfig(), state, user(2), "choose_expedition_action", Map.of("choice", "continue"), List.of());

        assertEquals("DECISION", state.get("phase"));
        assertEquals("artifact", ((List<Map<String, Object>>) state.get("currentCards")).getLast().get("type"));
        assertEquals(1, ((List<Map<String, Object>>) state.get("roadArtifacts")).size());
        assertEquals(2, player(state, 1).get("totalTreasure"));
    }

    @Test
    void disasterClearsOnlyPlayersStillExploringAndRemovesOneCard() {
        List<RoomSeat> seats = seats(2);
        Map<String, Object> state = started(seats, false);
        state.put("deck", new ArrayList<>(List.of(
                card("treasure-4", "treasure", Map.of("value", 4)),
                card("disaster-trap-1", "disaster", Map.of("disaster", "trap")),
                card("disaster-trap-2", "disaster", Map.of("disaster", "trap"))
        )));

        module.onAction(room(), seats, module.defaultConfig(), state, user(1), "draw_exploration_card", Map.of(), List.of());
        module.onAction(room(), seats, module.defaultConfig(), state, user(1), "choose_expedition_action", Map.of("choice", "return"), List.of());
        module.onAction(room(), seats, module.defaultConfig(), state, user(2), "choose_expedition_action", Map.of("choice", "continue"), List.of());
        module.onAction(room(), seats, module.defaultConfig(), state, user(2), "choose_expedition_action", Map.of("choice", "continue"), List.of());

        assertEquals(2, player(state, 1).get("totalTreasure"));
        assertEquals(0, player(state, 2).get("totalTreasure"));
        assertEquals(2, ((Map<?, ?>) state.get("disasterRemaining")).get("trap"));
        assertEquals(2, state.get("round"));
        assertEquals("trap", ((Map<?, ?>) state.get("lastDisaster")).get("disaster"));
        assertEquals(1, ((List<?>) ((Map<?, ?>) state.get("lastDisaster")).get("players")).size());
    }

    @Test
    void multipleThievesSplitThreeStonesAndReturnTheRemainder() {
        List<RoomSeat> seats = seats(3);
        Map<String, Object> config = module.defaultConfig();
        config.put("thievesExpansion", true);
        Map<String, Object> state = started(seats, true);
        state.put("phase", "DECISION");
        state.put("deck", new ArrayList<>());
        player(state, 3).put("roundTreasure", 5);

        module.onAction(room(), seats, config, state, user(1), "choose_expedition_action", Map.of("choice", "steal"), List.of());
        module.onAction(room(), seats, config, state, user(2), "choose_expedition_action", Map.of("choice", "steal"), List.of());
        module.onAction(room(), seats, config, state, user(3), "choose_expedition_action", Map.of("choice", "return"), List.of());
        module.onAction(room(), seats, config, state, user(1), "submit_theft_target", Map.of("targetUserId", 3), List.of());
        module.onAction(room(), seats, config, state, user(2), "submit_theft_target", Map.of("targetUserId", 3), List.of());

        assertEquals(1, player(state, 1).get("roundTreasure"));
        assertEquals(1, player(state, 2).get("roundTreasure"));
        assertEquals(3, player(state, 3).get("totalTreasure"));
        assertEquals(0, player(state, 3).get("roundTreasure"));
        assertEquals("EXPLORATION", state.get("phase"));
    }

    @Test
    void pendingChoicesAreMaskedFromPublicStateUntilResolution() {
        List<RoomSeat> seats = seats(2);
        Map<String, Object> config = module.defaultConfig();
        config.put("thievesExpansion", true);
        Map<String, Object> state = started(seats, true);
        state.put("phase", "DECISION");

        module.onAction(room(), seats, config, state, user(1), "choose_expedition_action", Map.of("choice", "steal"), List.of());
        Map<String, Object> visible = module.stateForViewer(state, user(2));

        assertEquals(true, ((Map<?, ?>) visible.get("pendingDecisions")).get("1"));
        assertFalse(((Map<?, ?>) visible.get("pendingDecisions")).containsValue("steal"));
    }

    @Test
    void eventsExpansionDrawsAndPublishesOneEventAtRoundStart() {
        List<RoomSeat> seats = seats(2);
        Map<String, Object> config = module.defaultConfig();
        config.put("eventsExpansion", true);

        Map<String, Object> state = module.onStart(room(), seats, config);

        assertTrue(Boolean.TRUE.equals(state.get("eventsExpansion")));
        assertNotNull(state.get("currentEvent"));
        assertEquals(1, ((List<?>) state.get("eventsUsed")).size());
    }

    @Test
    void farewellLeavesAllTreasureOnTheRoad() {
        List<RoomSeat> seats = seats(2);
        Map<String, Object> state = started(seats, false);
        state.put("currentEvent", Map.of("id", "farewell"));
        state.put("deck", new ArrayList<>(List.of(card("treasure-9", "treasure", Map.of("value", 9)))));

        module.onAction(room(), seats, module.defaultConfig(), state, user(1), "draw_exploration_card", Map.of(), List.of());

        assertEquals(9, state.get("roadTreasure"));
        assertEquals(0, player(state, 1).get("roundTreasure"));
        assertEquals("DECISION", state.get("phase"));
    }

    @Test
    void endlessWealthAddsFiveStonesOnFirstDisasterOfEachType() {
        List<RoomSeat> seats = seats(2);
        Map<String, Object> state = started(seats, false);
        state.put("currentEvent", Map.of("id", "endless_wealth"));
        state.put("deck", new ArrayList<>(List.of(card("disaster-trap-1", "disaster", Map.of("disaster", "trap")))));

        module.onAction(room(), seats, module.defaultConfig(), state, user(1), "draw_exploration_card", Map.of(), List.of());

        assertEquals(5, state.get("roadTreasure"));
        assertEquals(1, ((Map<?, ?>) state.get("disasterSeen")).get("trap"));
    }

    @Test
    void doubleChallengeRevealsTwoCardsBeforeDecision() {
        List<RoomSeat> seats = seats(2);
        Map<String, Object> state = started(seats, false);
        state.put("currentEvent", Map.of("id", "double_challenge"));
        state.put("deck", new ArrayList<>(List.of(
                card("treasure-4", "treasure", Map.of("value", 4)),
                card("artifact-5", "artifact", Map.of("value", 5)),
                card("treasure-2", "treasure", Map.of("value", 2)),
                card("artifact-7", "artifact", Map.of("value", 7))
        )));

        module.onAction(room(), seats, module.defaultConfig(), state, user(1), "draw_exploration_card", Map.of(), List.of());

        assertEquals(2, cards(state.get("currentCards")).size());
        assertEquals("DECISION", state.get("phase"));
        assertEquals(2, player(state, 1).get("roundTreasure"));

        module.onAction(room(), seats, module.defaultConfig(), state, user(1), "choose_expedition_action", Map.of("choice", "continue"), List.of());
        module.onAction(room(), seats, module.defaultConfig(), state, user(2), "choose_expedition_action", Map.of("choice", "continue"), List.of());

        assertEquals(4, cards(state.get("currentCards")).size());
        assertEquals("DECISION", state.get("phase"));
    }

    @Test
    void tripleChallengeWaitsForThirdMatchingDisaster() {
        List<RoomSeat> seats = seats(2);
        Map<String, Object> state = started(seats, false);
        state.put("currentEvent", Map.of("id", "triple_challenge"));
        state.put("deck", new ArrayList<>(List.of(
                card("disaster-trap-1", "disaster", Map.of("disaster", "trap")),
                card("disaster-trap-2", "disaster", Map.of("disaster", "trap")),
                card("disaster-trap-3", "disaster", Map.of("disaster", "trap"))
        )));

        module.onAction(room(), seats, module.defaultConfig(), state, user(1), "draw_exploration_card", Map.of(), List.of());
        for (int round = 0; round < 2; round++) {
            module.onAction(room(), seats, module.defaultConfig(), state, user(1), "choose_expedition_action", Map.of("choice", "continue"), List.of());
            module.onAction(room(), seats, module.defaultConfig(), state, user(2), "choose_expedition_action", Map.of("choice", "continue"), List.of());
        }
        assertEquals(2, state.get("round"));
        assertEquals(2, ((Map<?, ?>) state.get("disasterRemaining")).get("trap"));
        assertEquals(0, player(state, 1).get("totalTreasure"));
    }

    @Test
    void chupacabraAttacksTheOnlyReturningPlayer() {
        List<RoomSeat> seats = seats(3);
        Map<String, Object> state = started(seats, false);
        state.put("currentEvent", Map.of("id", "chupacabra"));
        state.put("phase", "DECISION");
        state.put("deck", new ArrayList<>());
        state.put("roadTreasure", 4);
        player(state, 1).put("roundTreasure", 3);

        module.onAction(room(), seats, module.defaultConfig(), state, user(1), "choose_expedition_action", Map.of("choice", "return"), List.of());
        module.onAction(room(), seats, module.defaultConfig(), state, user(2), "choose_expedition_action", Map.of("choice", "continue"), List.of());
        module.onAction(room(), seats, module.defaultConfig(), state, user(3), "choose_expedition_action", Map.of("choice", "continue"), List.of());
        assertEquals(0, player(state, 1).get("totalTreasure"));
        assertEquals(0, player(state, 1).get("roundTreasure"));
        assertEquals(false, player(state, 1).get("active"));
        assertEquals(4, state.get("roadTreasure"));
        assertTrue(((List<String>) state.get("notices")).stream()
                .anyMatch(notice -> notice.contains("独自返回营地")));
    }

    private Map<String, Object> started(List<RoomSeat> seats, boolean expansion) {
        Map<String, Object> config = module.defaultConfig();
        config.put("thievesExpansion", expansion);
        return module.onStart(room(), seats, config);
    }

    private Map<String, Object> player(Map<String, Object> state, int id) {
        return ((List<Map<String, Object>>) state.get("players")).stream()
                .filter(value -> Long.valueOf(id).equals(((Number) value.get("userId")).longValue()))
                .findFirst()
                .orElseThrow();
    }

    private List<Map<String, Object>> cards(Object raw) {
        return (List<Map<String, Object>>) raw;
    }

    private Map<String, Object> card(String id, String type, Map<String, Object> extra) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", id);
        card.put("type", type);
        card.putAll(extra);
        return card;
    }

    private UserSummary user(long id) {
        return new UserSummary(id, "P" + id);
    }

    private List<RoomSeat> seats(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new RoomSeat(index, (long) index + 1, "P" + (index + 1)))
                .toList();
    }

    private RoomEntity room() {
        return new RoomEntity(1, GameType.INCAN_GOLD, "1234", 1, null, 4,
                RoomStatus.WAITING, "{}", "{}", Instant.now(), Instant.now());
    }
}
