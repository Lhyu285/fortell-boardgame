package com.fortell.boardgame.game_modules.rps;

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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class RpsGameModule implements GameModule {
    private static final int FREE_FOR_ALL_ROUNDS = 9999;
    private static final String MODE_FREE_FOR_ALL = "free_for_all";
    private static final String MODE_BRACKET = "bracket";
    private static final List<String> MOVES_THREE = List.of("stone", "scissors", "paper");
    private static final List<String> MOVES_FIVE = List.of("stone", "scissors", "paper", "lizard", "spock");
    private static final Map<String, List<String>> BEATS = Map.of(
            "stone", List.of("scissors", "lizard"),
            "scissors", List.of("paper", "lizard"),
            "paper", List.of("stone", "spock"),
            "lizard", List.of("spock", "paper"),
            "spock", List.of("stone", "scissors")
    );

    private final SecureRandom random = new SecureRandom();

    @Override
    public GameType gameType() {
        return GameType.RPS;
    }

    @Override
    public GameDescriptor descriptor() {
        return new GameDescriptor("rps", "猜拳", "/rps", "/rps/rule", 2, 8, "支持大乱斗和淘汰赛");
    }

    @Override
    public Map<String, Object> defaultConfig() {
        return new LinkedHashMap<>(Map.of("moveSet", 3, "mode", MODE_FREE_FOR_ALL));
    }

    @Override
    public Map<String, Object> initialState(RoomEntity room, List<RoomSeat> seats) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("phase", "waiting");
        state.put("round", 0);
        state.put("mode", MODE_FREE_FOR_ALL);
        state.put("moveSet", 3);
        state.put("activePlayers", List.of());
        state.put("candidatePlayers", List.of());
        state.put("groups", List.of());
        state.put("submissions", new LinkedHashMap<>());
        state.put("lastRoundSubmissions", new LinkedHashMap<>());
        state.put("lastResultText", "");
        state.put("wins", new LinkedHashMap<>());
        state.put("winnerNames", List.of());
        state.put("notices", new ArrayList<>());
        return state;
    }

    @Override
    public Map<String, Object> sanitizeConfig(Map<String, Object> requested, RoomEntity room, List<RoomSeat> seats) {
        Map<String, Object> merged = new LinkedHashMap<>(defaultConfig());
        if (requested != null) {
            merged.putAll(requested);
        }
        int moveSet = parseInteger(merged.get("moveSet"), 3);
        if (moveSet != 3 && moveSet != 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "拳法种类只能是三种或五种");
        }
        String mode = Objects.toString(merged.get("mode"), MODE_FREE_FOR_ALL);
        if (!MODE_FREE_FOR_ALL.equals(mode) && !MODE_BRACKET.equals(mode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "猜拳模式不支持");
        }
        merged.put("moveSet", moveSet);
        merged.put("mode", mode);
        return merged;
    }

    @Override
    public void validateCanStart(RoomEntity room, List<RoomSeat> seats) {
        if (playersOf(seats).size() < gameType().minPlayers()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "至少需要2名玩家");
        }
    }

    @Override
    public Map<String, Object> onStart(RoomEntity room, List<RoomSeat> seats, Map<String, Object> config) {
        String mode = Objects.toString(config.get("mode"), MODE_FREE_FOR_ALL);
        int moveSet = parseInteger(config.get("moveSet"), 3);
        List<Map<String, Object>> players = playersOf(seats);

        Map<String, Object> state = initialState(room, seats);
        state.put("phase", "collecting");
        state.put("round", 1);
        state.put("mode", mode);
        state.put("moveSet", moveSet);
        state.put("activePlayers", players.stream().map(player -> player.get("userId")).toList());
        state.put("wins", initialWins(players));
        state.put("groups", MODE_BRACKET.equals(mode) ? bracketGroups(players) : List.of(group(1, players)));
        appendNotice(state, "猜拳开始，请所有在座玩家选择拳法。");
        return state;
    }

    @Override
    public Map<String, Object> onAction(RoomEntity room, List<RoomSeat> seats, Map<String, Object> config,
                                        Map<String, Object> state, UserSummary actor, String actionType,
                                        Map<String, Object> payload, List<String> notices) {
        if (!"submit_move".equals(actionType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的猜拳动作");
        }
        if (!activePlayerIds(state).contains(actor.id())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "你当前不在本轮对战中");
        }

        List<String> allowedMoves = parseInteger(config.get("moveSet"), 3) == 5 ? MOVES_FIVE : MOVES_THREE;
        String move = Objects.toString(payload.get("move"), "");
        if (!allowedMoves.contains(move)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "无效出拳");
        }

        Map<String, Object> submissions = submissionsOf(state);
        String actorKey = String.valueOf(actor.id());
        if (submissions.containsKey(actorKey)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "你已经完成选择");
        }
        submissions.put(actorKey, move);
        state.put("submissions", submissions);
        appendNotice(state, "玩家" + actor.username() + "已完成选择");

        if (MODE_BRACKET.equals(Objects.toString(state.get("mode"), MODE_FREE_FOR_ALL))) {
            resolveBracketIfReady(state, allowedMoves);
        } else {
            resolveFreeForAllIfReady(state, submissions);
        }
        return state;
    }

    @Override
    public List<String> rules() {
        return List.of(
                "三种拳法：石头克制剪刀，剪刀克制布，布克制石头。",
                "五种拳法：石头克制剪刀和蜥蜴；剪刀克制布和蜥蜴；布克制石头和史波克；蜥蜴克制史波克和布；史波克克制石头和剪刀。",
                "大乱斗模式默认进行9999轮。淘汰赛模式下玩家随机两两分组，胜者进入下一轮。"
        );
    }

    private void resolveFreeForAllIfReady(Map<String, Object> state, Map<String, Object> submissions) {
        List<Map<String, Object>> groups = groupsOf(state);
        List<Map<String, Object>> players = groupPlayers(groups.getFirst());
        if (!players.stream().allMatch(player -> submissions.containsKey(String.valueOf(player.get("userId"))))) {
            return;
        }

        Result result = resolvePlayers(players, submissions, true);
        String resultText = summaryText(players, submissions) + "。" + winnerText(result.winners());
        Map<String, Object> wins = winsOf(state);
        if (result.winners().size() == 1) {
            String winnerId = String.valueOf(result.winners().getFirst().get("userId"));
            wins.put(winnerId, parseInteger(wins.get(winnerId), 0) + 1);
        }

        groups.getFirst().put("resultText", resultText);
        int currentRound = parseInteger(state.get("round"), 1);
        state.put("groups", groups);
        state.put("wins", wins);
        state.put("lastRoundSubmissions", new LinkedHashMap<>(submissions));
        state.put("lastResultText", resultText);
        state.put("winnerNames", result.winners().stream().map(player -> String.valueOf(player.get("username"))).toList());
        appendNotice(state, resultText);

        if (currentRound >= FREE_FOR_ALL_ROUNDS) {
            state.put("phase", "finished");
            state.put("activePlayers", List.of());
            appendNotice(state, "大乱斗9999轮已结束。");
            return;
        }

        state.put("round", currentRound + 1);
        state.put("submissions", new LinkedHashMap<>());
        state.put("phase", "collecting");
    }

    private void resolveBracketIfReady(Map<String, Object> state, List<String> allowedMoves) {
        Map<String, Object> submissions = submissionsOf(state);
        List<Map<String, Object>> groups = groupsOf(state);
        boolean changed = false;

        for (Map<String, Object> group : groups) {
            if ("finished".equals(group.get("status"))) {
                continue;
            }

            List<Map<String, Object>> players = groupPlayers(group);
            for (Map<String, Object> player : players) {
                if (Boolean.TRUE.equals(player.get("bot"))) {
                    submissions.putIfAbsent(String.valueOf(player.get("userId")), allowedMoves.get(random.nextInt(allowedMoves.size())));
                }
            }

            if (!players.stream().allMatch(player -> submissions.containsKey(String.valueOf(player.get("userId"))))) {
                continue;
            }

            Result result = resolvePlayers(players, submissions, false);
            String resultText = summaryText(players, submissions) + "。" + winnerText(result.winners());
            group.put("resultText", resultText);
            appendNotice(state, resultText);

            if (result.resolved()) {
                group.put("status", "finished");
                if (result.winners().size() == 1) {
                    group.put("winnerId", result.winners().getFirst().get("userId"));
                }
                changed = true;
            } else {
                group.put("status", "collecting");
                removeGroupSubmissions(submissions, players);
                appendNotice(state, "第" + group.get("groupId") + "组未分出胜负，继续重赛。");
                changed = true;
            }
        }

        state.put("groups", groups);
        state.put("submissions", submissions);
        state.put("activePlayers", activePlayersFromGroups(groups));
        state.put("candidatePlayers", candidatePlayersOf(state, groups));

        if (!changed || groups.stream().anyMatch(group -> !"finished".equals(group.get("status")))) {
            return;
        }

        List<Map<String, Object>> winners = groups.stream()
                .map(group -> findHumanWinner(groupPlayers(group), asLong(group.get("winnerId"))))
                .filter(Objects::nonNull)
                .toList();

        if (winners.size() <= 1) {
            String champion = winners.isEmpty() ? "本次未分出胜负" : String.valueOf(winners.getFirst().get("username"));
            state.put("phase", "finished");
            state.put("winnerNames", winners.isEmpty() ? List.of() : List.of(champion));
            state.put("lastResultText", winners.isEmpty() ? "淘汰赛结束，本次未分出胜负。" : "淘汰赛结束，胜者为" + champion + "。");
            state.put("activePlayers", List.of());
            appendNotice(state, String.valueOf(state.get("lastResultText")));
            return;
        }

        int nextRound = parseInteger(state.get("round"), 1) + 1;
        List<Long> winnerIds = winners.stream().map(player -> asLong(player.get("userId"))).toList();
        List<Long> candidates = ((List<?>) state.getOrDefault("candidatePlayers", List.of())).stream()
                .map(this::asLong)
                .filter(playerId -> !winnerIds.contains(playerId))
                .toList();
        state.put("round", nextRound);
        state.put("groups", bracketGroups(new ArrayList<>(winners)));
        state.put("submissions", new LinkedHashMap<>());
        state.put("activePlayers", winnerIds);
        state.put("candidatePlayers", candidates);
        appendNotice(state, "进入第" + nextRound + "轮，请晋级玩家继续选择拳法。");
    }

    private Result resolvePlayers(List<Map<String, Object>> players, Map<String, Object> submissions, boolean allowMultiplePlayers) {
        Map<Long, Integer> scores = new LinkedHashMap<>();
        for (Map<String, Object> player : players) {
            scores.put(asLong(player.get("userId")), 0);
        }

        for (Map<String, Object> left : players) {
            for (Map<String, Object> right : players) {
                long leftId = asLong(left.get("userId"));
                long rightId = asLong(right.get("userId"));
                if (leftId == rightId) {
                    continue;
                }
                String leftMove = Objects.toString(submissions.get(String.valueOf(leftId)), "");
                String rightMove = Objects.toString(submissions.get(String.valueOf(rightId)), "");
                if (beats(leftMove, rightMove)) {
                    scores.put(leftId, scores.get(leftId) + 1);
                }
            }
        }

        int best = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Map<String, Object>> bestPlayers = players.stream()
                .filter(player -> scores.getOrDefault(asLong(player.get("userId")), 0) == best)
                .toList();
        List<Map<String, Object>> winners = players.stream()
                .filter(player -> !Boolean.TRUE.equals(player.get("bot")))
                .filter(player -> scores.getOrDefault(asLong(player.get("userId")), 0) == best)
                .toList();
        if (allowMultiplePlayers) {
            return winners.size() == 1 ? new Result(winners, true) : new Result(List.of(), true);
        }
        if (bestPlayers.size() != 1) {
            return new Result(List.of(), false);
        }
        Map<String, Object> bestPlayer = bestPlayers.getFirst();
        return Boolean.TRUE.equals(bestPlayer.get("bot"))
                ? new Result(List.of(), true)
                : new Result(List.of(bestPlayer), true);
    }

    private List<Map<String, Object>> bracketGroups(List<Map<String, Object>> players) {
        List<Map<String, Object>> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled, random);
        List<Map<String, Object>> groups = new ArrayList<>();
        int groupId = 1;
        for (int index = 0; index < shuffled.size(); index += 2) {
            List<Map<String, Object>> groupPlayers = new ArrayList<>();
            groupPlayers.add(shuffled.get(index));
            if (index + 1 < shuffled.size()) {
                groupPlayers.add(shuffled.get(index + 1));
            } else {
                groupPlayers.add(botPlayer(groupId));
            }
            groups.add(group(groupId, groupPlayers));
            groupId++;
        }
        return groups;
    }

    private Map<String, Object> group(int groupId, List<Map<String, Object>> players) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("groupId", groupId);
        group.put("status", "collecting");
        group.put("players", players);
        group.put("resultText", "");
        return group;
    }

    private Map<String, Object> botPlayer(int groupId) {
        Map<String, Object> bot = new LinkedHashMap<>();
        bot.put("userId", -1000L - groupId - random.nextInt(100000));
        bot.put("username", "机器人");
        bot.put("seatIndex", null);
        bot.put("bot", true);
        return bot;
    }

    private List<Map<String, Object>> playersOf(List<RoomSeat> seats) {
        return seats.stream()
                .filter(RoomSeat::occupied)
                .filter(seat -> !seat.bot())
                .map(seat -> {
                    Map<String, Object> player = new LinkedHashMap<>();
                    player.put("userId", seat.userId());
                    player.put("username", seat.username());
                    player.put("seatIndex", seat.seatIndex());
                    player.put("bot", false);
                    return player;
                })
                .toList();
    }

    private Map<String, Object> initialWins(List<Map<String, Object>> players) {
        Map<String, Object> wins = new LinkedHashMap<>();
        for (Map<String, Object> player : players) {
            wins.put(String.valueOf(player.get("userId")), 0);
        }
        return wins;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> groupsOf(Map<String, Object> state) {
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Object item : (List<?>) state.getOrDefault("groups", List.of())) {
            groups.add(new LinkedHashMap<>((Map<String, Object>) item));
        }
        return groups;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> groupPlayers(Map<String, Object> group) {
        List<Map<String, Object>> players = new ArrayList<>();
        for (Object item : (List<?>) group.getOrDefault("players", List.of())) {
            players.add(new LinkedHashMap<>((Map<String, Object>) item));
        }
        return players;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> submissionsOf(Map<String, Object> state) {
        return new LinkedHashMap<>((Map<String, Object>) state.getOrDefault("submissions", Map.of()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> winsOf(Map<String, Object> state) {
        return new LinkedHashMap<>((Map<String, Object>) state.getOrDefault("wins", Map.of()));
    }

    private List<Long> activePlayerIds(Map<String, Object> state) {
        return ((List<?>) state.getOrDefault("activePlayers", List.of())).stream()
                .map(this::asLong)
                .toList();
    }

    private List<Long> activePlayersFromGroups(List<Map<String, Object>> groups) {
        return groups.stream()
                .filter(group -> !"finished".equals(group.get("status")))
                .flatMap(group -> groupPlayers(group).stream())
                .filter(player -> !Boolean.TRUE.equals(player.get("bot")))
                .map(player -> asLong(player.get("userId")))
                .toList();
    }

    private List<Long> candidatePlayersOf(Map<String, Object> state, List<Map<String, Object>> groups) {
        List<Long> active = activePlayersFromGroups(groups);
        List<Long> candidates = new ArrayList<>(((List<?>) state.getOrDefault("candidatePlayers", List.of())).stream()
                .map(this::asLong)
                .toList());
        groups.stream()
                .filter(group -> "finished".equals(group.get("status")))
                .flatMap(group -> groupPlayers(group).stream())
                .filter(player -> !Boolean.TRUE.equals(player.get("bot")))
                .map(player -> asLong(player.get("userId")))
                .filter(playerId -> !active.contains(playerId))
                .filter(playerId -> candidates.stream().noneMatch(existing -> Objects.equals(existing, playerId)))
                .forEach(candidates::add);
        return candidates;
    }

    private Map<String, Object> findHumanWinner(List<Map<String, Object>> players, Long winnerId) {
        if (winnerId == null) {
            return null;
        }
        return players.stream()
                .filter(player -> !Boolean.TRUE.equals(player.get("bot")))
                .filter(player -> Objects.equals(asLong(player.get("userId")), winnerId))
                .findFirst()
                .orElse(null);
    }

    private void removeGroupSubmissions(Map<String, Object> submissions, List<Map<String, Object>> players) {
        for (Map<String, Object> player : players) {
            submissions.remove(String.valueOf(player.get("userId")));
        }
    }

    private String summaryText(List<Map<String, Object>> players, Map<String, Object> submissions) {
        return players.stream()
                .map(player -> playerLabel(player) + "：" + translateMove(Objects.toString(submissions.get(String.valueOf(player.get("userId"))), "")))
                .reduce((left, right) -> left + "，" + right)
                .orElse("");
    }

    private String winnerText(List<Map<String, Object>> winners) {
        if (winners.size() == 1) {
            return "胜者为" + playerLabel(winners.getFirst()) + "！";
        }
        return "本次未分出胜负";
    }

    private String playerLabel(Map<String, Object> player) {
        if (Boolean.TRUE.equals(player.get("bot"))) {
            return "机器人";
        }
        Object seatIndex = player.get("seatIndex");
        return seatIndex instanceof Number number ? number.intValue() + 1 + "号玩家" : String.valueOf(player.get("username"));
    }

    private boolean beats(String left, String right) {
        return BEATS.getOrDefault(left, List.of()).contains(right);
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

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private void appendNotice(Map<String, Object> state, String notice) {
        List<String> notices = new ArrayList<>((List<String>) state.getOrDefault("notices", List.of()));
        notices.addFirst(notice);
        state.put("notices", notices.stream().limit(20).toList());
    }

    private String translateMove(String move) {
        return switch (move) {
            case "stone" -> "石头";
            case "scissors" -> "剪刀";
            case "paper" -> "布";
            case "lizard" -> "蜥蜴";
            case "spock" -> "史波克";
            default -> move;
        };
    }

    private record Result(List<Map<String, Object>> winners, boolean resolved) {
    }
}
