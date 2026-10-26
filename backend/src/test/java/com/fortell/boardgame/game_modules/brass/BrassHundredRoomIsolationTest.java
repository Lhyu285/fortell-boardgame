package com.fortell.boardgame.game_modules.brass;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortell.boardgame.models.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;
import static org.junit.jupiter.api.Assertions.*;

/** In-process isolation regression, not an HTTP/SQLite/network capacity benchmark. */
class BrassHundredRoomIsolationTest {
    @Test
    void oneSharedModuleExecutesOneHundredIndependentRoomsWithoutMutatingInputSnapshots() throws Exception {
        var module = new BrassGameModule();
        var mapper = new ObjectMapper();
        var start = new CountDownLatch(1);
        var results = new ArrayList<Future<Map<String, Object>>>();
        try (var workers = Executors.newFixedThreadPool(16)) {
            for (int index = 0; index < 100; index++) {
                final int table = index;
                results.add(workers.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    long first = table * 2L + 1, second = first + 1;
                    var seats = List.of(new RoomSeat(0, first, "A" + table), new RoomSeat(1, second, "B" + table));
                    var room = new RoomEntity(table + 1, GameType.BRASS, "table-" + table, first, null, 2,
                            RoomStatus.IN_PROGRESS, "{}", "{}", Instant.EPOCH, Instant.EPOCH);
                    var state = module.onStart(room, seats, module.defaultConfig());
                    state.put("isolationToken", table);
                    String before = mapper.writeValueAsString(state);
                    long actorId = asLong(state.get("currentPlayerId"));
                    String card = Objects.toString(listOf(mapOf(state.get("hands")).get(String.valueOf(actorId))).getFirst().get("id"));
                    var transition = module.onTransition(room, seats, module.defaultConfig(), state,
                            new UserSummary(actorId, "Actor" + table), "skip", Map.of("cardId", card), List.of());
                    assertEquals(before, mapper.writeValueAsString(state), "input snapshot must remain untouched");
                    assertEquals(table, transition.state().get("isolationToken"));
                    assertEquals(1, BrassFlowAdapter.turnState(transition.state()).actionsTaken());
                    assertEquals("skip", transition.event().get("type"));
                    assertEquals(Set.of(String.valueOf(first), String.valueOf(second)), viewMap(transition.state().get("hands")).keySet());
                    return transition.state();
                }));
            }
            start.countDown();
            var states = new ArrayList<Map<String, Object>>();
            for (var result : results) states.add(result.get(90, TimeUnit.SECONDS));
            viewMap(states.getFirst().get("market")).put("onlyRoomZero", true);
            for (var state : states.subList(1, states.size())) assertFalse(viewMap(state.get("market")).containsKey("onlyRoomZero"));
        }
    }
}
