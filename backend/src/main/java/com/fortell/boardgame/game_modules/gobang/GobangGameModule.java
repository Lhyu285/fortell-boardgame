package com.fortell.boardgame.game_modules.gobang;

import com.fortell.boardgame.game_modules.GameModule;
import com.fortell.boardgame.models.ApiException;
import com.fortell.boardgame.models.GameDescriptor;
import com.fortell.boardgame.models.GameType;
import com.fortell.boardgame.models.RoomEntity;
import com.fortell.boardgame.models.RoomSeat;
import com.fortell.boardgame.models.UserSummary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class GobangGameModule implements GameModule {
    private static final int BOARD_SIZE = 19;
    private final SecureRandom random = new SecureRandom();

    @Override
    public GameType gameType() {
        return GameType.GOBANG;
    }

    @Override
    public GameDescriptor descriptor() {
        return new GameDescriptor("gobang", "五子棋", "/gobang", "/gobang/rule", 2, 2, "19x19 棋盘，支持普通规则胜负判定");
    }

    @Override
    public Map<String, Object> defaultConfig() {
        return new LinkedHashMap<>(Map.of("ruleMode", "normal", "firstHand", "random"));
    }

    @Override
    public Map<String, Object> initialState(RoomEntity room, List<RoomSeat> seats) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("phase", "waiting");
        state.put("board", new ArrayList<>());
        state.put("moves", new ArrayList<>());
        state.put("currentPlayerId", null);
        state.put("blackPlayerId", null);
        state.put("whitePlayerId", null);
        state.put("winnerUserId", null);
        state.put("winnerColor", null);
        state.put("notices", new ArrayList<>());
        return state;
    }

    @Override
    public Map<String, Object> sanitizeConfig(Map<String, Object> requested, RoomEntity room, List<RoomSeat> seats) {
        Map<String, Object> merged = new LinkedHashMap<>(defaultConfig());
        if (requested != null) {
            merged.putAll(requested);
        }
        String ruleMode = Objects.toString(merged.get("ruleMode"), "normal");
        String firstHand = Objects.toString(merged.get("firstHand"), "random");
        if (!List.of("normal", "professional").contains(ruleMode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "五子棋规则不支持");
        }
        if (!List.of("random", "host_first", "host_second").contains(firstHand)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "五子棋先后手配置不支持");
        }
        merged.put("ruleMode", ruleMode);
        merged.put("firstHand", firstHand);
        return merged;
    }

    @Override
    public void validateCanStart(RoomEntity room, List<RoomSeat> seats) {
        if (seats.stream().filter(RoomSeat::occupied).count() != 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "五子棋必须 2 名玩家都入座后才能开始");
        }
    }

    @Override
    public Map<String, Object> onStart(RoomEntity room, List<RoomSeat> seats, Map<String, Object> config) {
        List<RoomSeat> occupied = seats.stream().filter(RoomSeat::occupied).toList();
        RoomSeat first = occupied.get(0);
        RoomSeat second = occupied.get(1);
        long blackPlayerId = chooseBlackPlayer(room, occupied, Objects.toString(config.get("firstHand"), "random"));
        long whitePlayerId = blackPlayerId == first.userId() ? second.userId() : first.userId();

        Map<String, Object> state = initialState(room, seats);
        state.put("phase", "playing");
        state.put("board", buildEmptyBoard());
        state.put("currentPlayerId", blackPlayerId);
        state.put("blackPlayerId", blackPlayerId);
        state.put("whitePlayerId", whitePlayerId);
        appendNotice(state, "对局开始，黑棋先手。");
        return state;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> onAction(RoomEntity room, List<RoomSeat> seats, Map<String, Object> config,
                                        Map<String, Object> state, UserSummary actor, String actionType,
                                        Map<String, Object> payload, List<String> notices) {
        if (!"place_stone".equals(actionType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的五子棋动作");
        }
        long currentPlayerId = ((Number) state.getOrDefault("currentPlayerId", -1)).longValue();
        if (actor.id() != currentPlayerId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "还没轮到你落子");
        }

        int row = parseInteger(payload.get("row"), -1);
        int col = parseInteger(payload.get("col"), -1);
        if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "落子位置超出棋盘");
        }

        List<List<String>> board = (List<List<String>>) state.get("board");
        if (!Objects.equals(board.get(row).get(col), "")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该位置已有棋子");
        }

        long blackPlayerId = ((Number) state.get("blackPlayerId")).longValue();
        long whitePlayerId = ((Number) state.get("whitePlayerId")).longValue();
        String color = actor.id() == blackPlayerId ? "black" : "white";
        board.get(row).set(col, color);

        List<Map<String, Object>> moves = new ArrayList<>((List<Map<String, Object>>) state.getOrDefault("moves", List.of()));
        int moveNumber = moves.size() + 1;
        moves.add(Map.of(
                "moveNumber", moveNumber,
                "playerName", actor.username(),
                "color", color,
                "row", row + 1,
                "column", String.valueOf((char) ('A' + col))
        ));
        state.put("moves", moves);

        if (isWin(board, row, col, color)) {
            state.put("phase", "finished");
            state.put("winnerUserId", actor.id());
            state.put("winnerColor", color);
            state.put("_roomStatus", "WAITING");
            appendNotice(state, actor.username() + " 获胜，颜色为" + ("black".equals(color) ? "黑棋" : "白棋") + "。");
            return state;
        }

        if (moveNumber >= BOARD_SIZE * BOARD_SIZE) {
            state.put("phase", "finished");
            state.put("winnerUserId", null);
            state.put("winnerColor", null);
            state.put("_roomStatus", "WAITING");
            appendNotice(state, "棋盘已满，本局平局。");
            return state;
        }

        state.put("currentPlayerId", actor.id() == blackPlayerId ? whitePlayerId : blackPlayerId);
        appendNotice(state, actor.username() + " 在 " + (row + 1) + "," + (char) ('A' + col) + " 落子。");
        return state;
    }

    @Override
    public List<String> rules() {
        return List.of(
                "棋盘 19x19，点击位置后确认落子。",
                "普通模式下任意方向先连成五子获胜。",
                "专业模式接口已预留，本版不实现禁手判定。"
        );
    }

    private List<List<String>> buildEmptyBoard() {
        List<List<String>> board = new ArrayList<>();
        for (int row = 0; row < BOARD_SIZE; row++) {
            List<String> line = new ArrayList<>();
            for (int col = 0; col < BOARD_SIZE; col++) {
                line.add("");
            }
            board.add(line);
        }
        return board;
    }

    private long chooseBlackPlayer(RoomEntity room, List<RoomSeat> occupied, String firstHand) {
        RoomSeat first = occupied.get(0);
        RoomSeat second = occupied.get(1);
        return switch (firstHand) {
            case "host_first" -> room.ownerUserId() == first.userId() ? first.userId() : second.userId();
            case "host_second" -> room.ownerUserId() == first.userId() ? second.userId() : first.userId();
            default -> random.nextBoolean() ? first.userId() : second.userId();
        };
    }

    @SuppressWarnings("unchecked")
    private void appendNotice(Map<String, Object> state, String notice) {
        List<String> notices = new ArrayList<>((List<String>) state.getOrDefault("notices", List.of()));
        notices.add(0, notice);
        state.put("notices", notices.stream().limit(12).toList());
    }

    private boolean isWin(List<List<String>> board, int row, int col, String color) {
        int[][] directions = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] direction : directions) {
            int count = 1;
            count += scan(board, row, col, direction[0], direction[1], color);
            count += scan(board, row, col, -direction[0], -direction[1], color);
            if (count >= 5) {
                return true;
            }
        }
        return false;
    }

    private int scan(List<List<String>> board, int row, int col, int rowStep, int colStep, String color) {
        int count = 0;
        int currentRow = row + rowStep;
        int currentCol = col + colStep;
        while (currentRow >= 0 && currentRow < BOARD_SIZE && currentCol >= 0 && currentCol < BOARD_SIZE) {
            if (!color.equals(board.get(currentRow).get(currentCol))) {
                break;
            }
            count++;
            currentRow += rowStep;
            currentCol += colStep;
        }
        return count;
    }

    private int parseInteger(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Integer.parseInt(stringValue);
        }
        return fallback;
    }
}
