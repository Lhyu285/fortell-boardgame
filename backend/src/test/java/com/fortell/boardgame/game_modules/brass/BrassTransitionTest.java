package com.fortell.boardgame.game_modules.brass;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortell.boardgame.game_modules.GameTransitionResult;
import com.fortell.boardgame.models.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;
import static org.junit.jupiter.api.Assertions.*;

class BrassTransitionTest {
    BrassGameModule module = new BrassGameModule();
    List<RoomSeat> seats = List.of(new RoomSeat(0, 1L, "A"), new RoomSeat(1, 2L, "B"));
    RoomEntity room = new RoomEntity(1, GameType.BRASS, "transition", 1, null, 2, RoomStatus.IN_PROGRESS, "{}", "{}", Instant.EPOCH, Instant.EPOCH);

    @Test void saleSubmissionsSurviveReloadAndShareOneBusinessAction() throws Exception {
        Map<String, Object> state = module.onStart(room, seats, module.defaultConfig());
        long actorId = asLong(state.get("currentPlayerId")); UserSummary actor = new UserSummary(actorId, "Actor");
        Map<String, Object> board = mapOf(state.get("board"));
        List<Map<String, Object>> tiles = new ArrayList<>();
        for (String id : List.of("sale-a", "sale-b")) tiles.add(new LinkedHashMap<>(Map.of("id", id, "ownerId", actorId,
                "ownerName", "Actor", "city", "Birmingham", "industryType", "manufacturer", "flipType", "sell",
                "flipped", false, "saleBeerCost", 0, "incomeReward", 2, "level", 1)));
        board.put("industries", tiles);
        board.put("links", List.of(new LinkedHashMap<>(Map.of("id", "access", "from", "Birmingham", "to", "Oxford", "type", "canal", "ownerId", actorId))));
        state.put("board", board);
        Map<String, Object> market = mapOf(state.get("market"));
        market.put("beerMerchants", List.of(new LinkedHashMap<>(Map.of("id", "merchant", "city", "Oxford", "blank", false,
                "marketOpen", true, "acceptedIndustryTypes", List.of("manufacturer"), "beer", 1, "reward", ""))));
        state.put("market", market); state.put("version", 7L);
        String card = Objects.toString(listOf(mapOf(state.get("hands")).get(String.valueOf(actorId))).getFirst().get("id"));
        GameTransitionResult first = transition(state, actor, "sell", Map.of("cardId", card, "tileId", "sale-a", "merchantId", "merchant"));
        assertEquals(false, first.event().get("actionCompleted"));
        assertEquals(0, BrassFlowAdapter.turnState(first.state()).actionsTaken());
        first.state().put("version", 8L);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> reloaded = mapper.readValue(mapper.writeValueAsString(first.state()), new TypeReference<>() {});
        String beforeFailure = mapper.writeValueAsString(reloaded);
        assertThrows(ApiException.class, () -> transition(reloaded, actor, "sell", Map.of("tileId", "absent", "merchantId", "merchant")));
        assertEquals(beforeFailure, mapper.writeValueAsString(reloaded));
        GameTransitionResult second = transition(reloaded, actor, "sell", Map.of("tileId", "sale-b", "merchantId", "merchant"));
        second.state().put("version", 9L);
        GameTransitionResult done = transition(second.state(), actor, "end_sell", Map.of());
        assertEquals(first.event().get("businessActionId"), second.event().get("businessActionId"));
        assertEquals(first.event().get("businessActionId"), done.event().get("businessActionId"));
        assertEquals(true, done.event().get("actionCompleted"));
        assertEquals("end_sell", done.event().get("type"));
        assertEquals(1, BrassFlowAdapter.turnState(done.state()).actionsTaken());
    }

    @Test void candidateSimulationDoesNotWriteLogsOrChangeInputAndClientsCannotSetSimulationMode() {
        Map<String, Object> state = module.onStart(room, seats, module.defaultConfig());
        Map<String, Object> copy = mapOf(deepCopy(state));
        BrassActionQueryService.availableActions(state);
        assertEquals(copy, state);
        Map<String, Object> simulated = BrassStateDocument.simulationCopy(state);
        BrassRecordSupport.appendNotice(simulated, "not real");
        BrassRecordSupport.describeOperation(simulated, new UserSummary(1, "A"), "build", "not real");
        assertFalse(simulated.containsKey("notices"));
        assertFalse(simulated.containsKey("actionLog"));
        state.put("_candidateSimulation", true);
        assertThrows(ApiException.class, () -> transition(state, new UserSummary(asLong(state.get("currentPlayerId")), "Actor"), "develop", Map.of()));
    }

    @Test void configuredRulesAreDeeplyImmutableAndRoomsAreIndependent() {
        assertThrows(UnsupportedOperationException.class, () -> BrassRuleData.BUILDING_TILES.get("coal_mine").getFirst().put("cost", 0));
        assertThrows(UnsupportedOperationException.class, () -> BrassRuleData.ROUTES.getFirst().put("canal", false));
        Map<String, Object> a = module.onStart(room, seats, module.defaultConfig());
        Map<String, Object> b = module.onStart(room, seats, module.defaultConfig());
        viewMap(a.get("market")).put("unrelated", 1);
        List<?> merchantsA = (List<?>) viewMap(a.get("market")).get("beerMerchants");
        List<?> merchantsB = (List<?>) viewMap(b.get("market")).get("beerMerchants");
        viewMap(merchantsA.getFirst()).put("unrelated", 1);
        assertFalse(mapOf(b.get("market")).containsKey("unrelated"));
        assertFalse(viewMap(merchantsB.getFirst()).containsKey("unrelated"));
        assertFalse(BrassRuleData.CARD_CONFIG.containsKey("unrelated"));
    }

    @Test void viewerProjectionConcealsPrivateCardsAndSnapshotsWithoutChangingAuthoritativeState() throws Exception {
        Map<String, Object> state = module.onStart(room, seats, module.defaultConfig());
        ObjectMapper mapper = new ObjectMapper();
        String before = mapper.writeValueAsString(state);
        long current = asLong(state.get("currentPlayerId"));
        Map<String, Object> actor = module.stateForViewer(state, new UserSummary(current, "Actor"));
        assertEquals(viewMap(state.get("hands")).get(String.valueOf(current)), viewMap(actor.get("hands")).get(String.valueOf(current)));
        assertEquals(state.get("availableActions"), actor.get("availableActions"));
        for (long viewerId : List.of(1L, 2L, 99L)) {
            Map<String, Object> projected = module.stateForViewer(state, new UserSummary(viewerId, "Viewer"));
            assertFalse(projected.containsKey("turnStartSnapshot"));
            assertEquals(objectList(state.get("deck")).size(), objectList(projected.get("deck")).size());
            assertTrue(listOf(projected.get("deck")).stream().allMatch(card -> card.equals(Map.of("hidden", true))));
            for (var entry : viewMap(projected.get("hands")).entrySet()) {
                assertEquals(objectList(viewMap(state.get("hands")).get(entry.getKey())).size(), objectList(entry.getValue()).size());
                if (!entry.getKey().equals(String.valueOf(viewerId))) {
                    assertTrue(listOf(entry.getValue()).stream().allMatch(card -> card.equals(Map.of("hidden", true))));
                }
            }
            if (viewerId != current) {
                assertEquals(List.of(), viewMap(projected.get("availableActions")).get("actions"));
                assertFalse(viewMap(projected.get("availableActions")).containsKey("buildOptions"));
            }
        }
        viewMap(actor.get("market")).put("privateMutation", true);
        assertEquals(before, mapper.writeValueAsString(state));
    }

    GameTransitionResult transition(Map<String, Object> state, UserSummary actor, String action, Map<String, Object> payload) {
        return module.onTransition(room, seats, module.defaultConfig(), state, actor, action, payload, List.of());
    }
}
