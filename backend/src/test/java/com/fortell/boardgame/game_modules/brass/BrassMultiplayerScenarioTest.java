package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.GameType;
import com.fortell.boardgame.models.RoomEntity;
import com.fortell.boardgame.models.RoomSeat;
import com.fortell.boardgame.models.RoomStatus;
import com.fortell.boardgame.models.UserSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BrassMultiplayerScenarioTest {

    @Test
    void twoThreeAndFourPlayerOpeningsHaveConsistentState() {
        for (int playerCount = 2; playerCount <= 4; playerCount++) {
            BrassGameModule module = new BrassGameModule();
            List<RoomSeat> seats = seats(playerCount);
            Map<String, Object> state = module.onStart(room(playerCount), seats, module.defaultConfig());

            assertEquals("playing", state.get("phase"));
            assertEquals("canal", state.get("era"));
            assertEquals(1, number(state.get("round")));
            assertEquals(playerCount, list(state.get("players")).size());
            assertEquals(playerCount, list(state.get("turnOrder")).size());
            assertEquals(number(list(state.get("turnOrder")).getFirst()), number(state.get("currentPlayerId")));
            assertEquals(playerCount, new HashSet<>(list(state.get("turnOrder"))).size());
            assertEquals(playerCount, number(state.get("hiddenDiscardCount")));

            Map<String, Object> turn = map(state.get("turn"));
            assertEquals(1, number(turn.get("actionsAllowed")));
            assertEquals(1, number(turn.get("actionsRemaining")));

            Map<String, Object> hands = map(state.get("hands"));
            Map<String, Object> stats = map(state.get("playerStats"));
            Map<String, Object> boards = map(state.get("playerBoards"));
            HashSet<String> colors = new HashSet<>();
            for (int player = 1; player <= playerCount; player++) {
                assertEquals(8, list(hands.get(String.valueOf(player))).size());
                assertNotNull(stats.get(String.valueOf(player)));
                assertNotNull(boards.get(String.valueOf(player)));
                colors.add(String.valueOf(map(list(state.get("players")).get(player - 1)).get("color")));
            }
            assertEquals(playerCount, colors.size());
            assertFalse(colors.contains(""));
        }
    }

    @Test
    void twoThreeAndFourPlayerFirstRoundAdvancesToRoundTwoWithTwoActions() {
        for (int playerCount = 2; playerCount <= 4; playerCount++) {
            BrassGameModule module = new BrassGameModule();
            List<RoomSeat> seats = seats(playerCount);
            Map<String, Object> state = module.onStart(room(playerCount), seats, module.defaultConfig());
            long firstPlayerId = number(state.get("currentPlayerId"));

            for (int player = 1; player <= playerCount; player++) {
                state = skipCurrentAction(module, state, seats);
                assertTrue(actions(state).contains("end_turn"));
                state = act(module, state, seats, "end_turn", Map.of());
            }

            assertEquals(2, number(state.get("round")));
            assertEquals(firstPlayerId, number(state.get("currentPlayerId")));
            assertEquals(2, number(map(state.get("turn")).get("actionsAllowed")));
            assertEquals(2, number(map(state.get("turn")).get("actionsRemaining")));
            for (int player = 1; player <= playerCount; player++) {
                assertEquals(8, list(map(state.get("hands")).get(String.valueOf(player))).size());
            }
        }
    }

    @Test
    void twoThreeAndFourPlayerCanalEraCanReachRailEraByLegalSkipFlow() {
        for (int playerCount = 2; playerCount <= 4; playerCount++) {
            BrassGameModule module = new BrassGameModule();
            List<RoomSeat> seats = seats(playerCount);
            Map<String, Object> state = module.onStart(room(playerCount), seats, module.defaultConfig());

            int steps = 0;
            while ("canal".equals(state.get("era")) && steps++ < 500) {
                List<Object> available = actions(state);
                if (available.contains("maintain_era")) {
                    state = act(module, state, seats, "maintain_era", Map.of());
                } else if (available.contains("skip")) {
                    state = skipCurrentAction(module, state, seats);
                } else if (available.contains("end_turn")) {
                    state = act(module, state, seats, "end_turn", Map.of());
                } else {
                    fail(playerCount + "人局运河时代无合法推进动作，当前动作：" + available);
                }
            }

            assertTrue(steps < 500, playerCount + "人局运河时代未能结束");
            assertEquals("rail", state.get("era"));
            assertEquals("playing", state.get("phase"));
            assertEquals(1, number(state.get("round")));
            assertEquals(8, list(map(state.get("hands")).get(String.valueOf(state.get("currentPlayerId")))).size());
        }
    }

    @Test
    void twoThreeAndFourPlayerGamesCanReachFinishedPhaseByLegalSkipFlow() {
        for (int playerCount = 2; playerCount <= 4; playerCount++) {
            BrassGameModule module = new BrassGameModule();
            List<RoomSeat> seats = seats(playerCount);
            Map<String, Object> state = module.onStart(room(playerCount), seats, module.defaultConfig());

            int steps = 0;
            while (!"finished".equals(state.get("phase")) && steps++ < 1200) {
                List<Object> available = actions(state);
                if (available.contains("maintain_era")) {
                    state = act(module, state, seats, "maintain_era", Map.of());
                } else if (available.contains("skip")) {
                    state = skipCurrentAction(module, state, seats);
                } else if (available.contains("end_turn")) {
                    state = act(module, state, seats, "end_turn", Map.of());
                } else {
                    fail(playerCount + "人局无合法推进动作，时代：" + state.get("era") + "，动作：" + available);
                }
            }

            assertTrue(steps < 1200, playerCount + "人局未能进入终局");
            assertEquals("finished", state.get("phase"));
            assertFalse(list(state.get("winners")).isEmpty());
            assertTrue(list(map(state.get("availableActions")).get("actions")).isEmpty());
        }
    }

    @Test
    void twoThreeAndFourPlayerMerchantSetupHasConsistentOpenAndClosedSlots() {
        for (int playerCount = 2; playerCount <= 4; playerCount++) {
            BrassGameModule module = new BrassGameModule();
            Map<String, Object> state = module.onStart(room(playerCount), seats(playerCount), module.defaultConfig());
            List<Object> merchants = list(map(state.get("market")).get("beerMerchants"));

            assertFalse(merchants.isEmpty());
            for (Object rawMerchant : merchants) {
                Map<String, Object> merchant = map(rawMerchant);
                assertNotNull(merchant.get("id"));
                assertNotNull(merchant.get("city"));
                boolean open = Boolean.TRUE.equals(merchant.get("marketOpen"));
                boolean blank = Boolean.TRUE.equals(merchant.get("blank"));
                int beer = (int) number(merchant.get("beer"));
                if (!open || blank || Boolean.FALSE.equals(merchant.get("providesBeer"))) {
                    assertEquals(0, beer);
                } else {
                    assertEquals(1, beer);
                }
            }
        }
    }

    private Map<String, Object> skipCurrentAction(BrassGameModule module, Map<String, Object> state, List<RoomSeat> seats) {
        long playerId = number(state.get("currentPlayerId"));
        List<Object> hand = list(map(state.get("hands")).get(String.valueOf(playerId)));
        if (hand.isEmpty()) {
            fail("当前玩家没有手牌但仍要求执行跳过行动");
        }
        String cardId = String.valueOf(map(hand.getFirst()).get("id"));
        return act(module, state, seats, "skip", Map.of("cardId", cardId));
    }

    private Map<String, Object> act(BrassGameModule module, Map<String, Object> state, List<RoomSeat> seats,
                                    String action, Map<String, Object> payload) {
        long playerId = number(state.get("currentPlayerId"));
        String username = "P" + playerId;
        return module.onAction(room(seats.size()), seats, module.defaultConfig(), state,
                new UserSummary(playerId, username), action, payload, List.of());
    }

    private List<Object> actions(Map<String, Object> state) {
        return list(map(state.get("availableActions")).get("actions"));
    }

    private RoomEntity room(int playerCount) {
        return new RoomEntity(
                9000 + playerCount,
                GameType.BRASS,
                "9" + playerCount,
                1,
                null,
                playerCount,
                RoomStatus.WAITING,
                "{}",
                "{}",
                Instant.now(),
                Instant.now()
        );
    }

    private List<RoomSeat> seats(int playerCount) {
        List<RoomSeat> seats = new ArrayList<>();
        for (int index = 0; index < playerCount; index++) {
            seats.add(new RoomSeat(index, index + 1L, "P" + (index + 1)));
        }
        return seats;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Object> list(Object value) {
        return value instanceof List<?> raw ? (List<Object>) raw : List.of();
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }
}
