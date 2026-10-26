package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.RoomEntity;
import com.fortell.boardgame.models.RoomSeat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.fortell.boardgame.game_modules.brass.BrassCardService.buildEraDeck;
import static com.fortell.boardgame.game_modules.brass.BrassCardService.initialCardHints;
import static com.fortell.boardgame.game_modules.brass.BrassCardService.initialScoutPool;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.asLong;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;
import static com.fortell.boardgame.game_modules.brass.BrassSetupData.assignRandomPlayerColors;
import static com.fortell.boardgame.game_modules.brass.BrassSetupData.initialBoard;
import static com.fortell.boardgame.game_modules.brass.BrassSetupData.initialDevelopments;
import static com.fortell.boardgame.game_modules.brass.BrassSetupData.initialMarket;
import static com.fortell.boardgame.game_modules.brass.BrassSetupData.initialPlayerBoards;
import static com.fortell.boardgame.game_modules.brass.BrassSetupData.initialPlayerStats;
import static com.fortell.boardgame.game_modules.brass.BrassSetupData.playersOf;
import static com.fortell.boardgame.game_modules.brass.BrassTurnService.beginTurn;

/** Setup Service rules; all working state is passed explicitly. */
final class BrassSetupService {
    private BrassSetupService() {}

    static Map<String, Object> initialState(RoomEntity room, List<RoomSeat> seats) {
        List<Map<String, Object>> players = playersOf(seats);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("version", 0);
        state.put("phase", "waiting");
        state.put("era", "canal");
        state.put("round", 0);
        state.put("turnIndex", 0);
        state.put("currentPlayerId", null);
        state.put("players", players);
        state.put("turnOrder", List.of());
        state.put("initialTurnOrder", List.of());
        state.put("playerStats", new LinkedHashMap<>());
        state.put("hands", new LinkedHashMap<>());
        state.put("developments", new LinkedHashMap<>());
        state.put("playerBoards", new LinkedHashMap<>());
        state.put("deck", List.of());
        state.put("scoutPool", initialScoutPool());
        state.put("discardPile", List.of());
        state.put("hiddenDiscardCount", 0);
        state.put("cardHints", new LinkedHashMap<>());
        state.put("market", initialMarket(players.size()));
        state.put("board", initialBoard());
        state.put("turn", new LinkedHashMap<>());
        state.put("turnStartSnapshot", null);
        state.put("canMaintainEra", false);
        state.put("availableActions", new LinkedHashMap<>());
        state.put("eraScores", new ArrayList<>());
        state.put("winners", new ArrayList<>());
        state.put("actionLog", new ArrayList<>());
        state.put("notices", new ArrayList<>(List.of("\u7b49\u5f85\u5f00\u59cb\u6e38\u620f")));
        state.put("log", new ArrayList<>());
        return state;
    }

    static Map<String, Object> onStart(RoomEntity room, List<RoomSeat> seats, Map<String, Object> config) {
        List<Map<String, Object>> players = playersOf(seats);
        assignRandomPlayerColors(players);
        List<Long> turnOrder = new ArrayList<>(players.stream()
                .map(player -> asLong(player.get("userId")))
                .toList());
        Collections.shuffle(turnOrder);

        List<Map<String, Object>> deck = buildEraDeck(players.size());
        Collections.shuffle(deck);
        Map<String, Object> cardHints = initialCardHints(deck);
        Map<String, Object> hands = new LinkedHashMap<>();
        for (Map<String, Object> player : players) {
            List<Map<String, Object>> hand = new ArrayList<>();
            for (int index = 0; index < STARTING_HAND_SIZE && !deck.isEmpty(); index++) {
                hand.add(deck.remove(0));
            }
            hands.put(String.valueOf(player.get("userId")), hand);
        }
        int hiddenDiscardCount = 0;
        for (int index = 0; index < players.size() && !deck.isEmpty(); index++) {
            deck.remove(0);
            hiddenDiscardCount++;
        }

        Map<String, Object> state = initialState(room, seats);
        state.put("phase", "playing");
        state.put("era", "canal");
        state.put("round", 1);
        state.put("turnIndex", 0);
        state.put("currentPlayerId", turnOrder.getFirst());
        state.put("players", players);
        state.put("turnOrder", turnOrder);
        state.put("initialTurnOrder", new ArrayList<>(turnOrder));
        state.put("playerStats", initialPlayerStats(players));
        state.put("developments", initialDevelopments(players));
        state.put("playerBoards", initialPlayerBoards(players));
        state.put("hands", hands);
        state.put("deck", deck);
        state.put("hiddenDiscardCount", hiddenDiscardCount);
        state.put("cardHints", cardHints);
        state.put("scoutPool", initialScoutPool());
        state.put("discardPile", new ArrayList<>());
        state.put("market", initialMarket(players.size()));
        state.put("board", initialBoard());
        beginTurn(state, "\u6e38\u620f\u5f00\u59cb\uff0c\u8f6e\u5230 " + playerName(players, turnOrder.getFirst()) + " \u884c\u52a8");

        return state;
    }
}
