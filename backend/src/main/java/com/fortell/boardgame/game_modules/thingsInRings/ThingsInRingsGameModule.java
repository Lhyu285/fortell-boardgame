package com.fortell.boardgame.game_modules.thingsInRings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortell.boardgame.game_modules.GameModule;
import com.fortell.boardgame.models.ApiException;
import com.fortell.boardgame.models.GameDescriptor;
import com.fortell.boardgame.models.GameType;
import com.fortell.boardgame.models.RoomEntity;
import com.fortell.boardgame.models.RoomSeat;
import com.fortell.boardgame.models.UserSummary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class ThingsInRingsGameModule implements GameModule {
    private static final int STARTING_HAND_SIZE = 5;
    private static final List<String> AREAS = List.of(
            "SCENE",
            "WORD",
            "ATTRIBUTE",
            "SCENE_WORD",
            "SCENE_ATTRIBUTE",
            "WORD_ATTRIBUTE",
            "SCENE_WORD_ATTRIBUTE",
            "NONE"
    );
    private static final Map<String, String> AREA_SHORT_NAMES = Map.of(
            "SCENE", "红",
            "WORD", "黄",
            "ATTRIBUTE", "蓝",
            "SCENE_WORD", "红黄",
            "SCENE_ATTRIBUTE", "红蓝",
            "WORD_ATTRIBUTE", "黄蓝",
            "SCENE_WORD_ATTRIBUTE", "红黄蓝",
            "NONE", "无关"
    );

    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    public ThingsInRingsGameModule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public GameType gameType() {
        return GameType.THINGS_IN_RINGS;
    }

    @Override
    public GameDescriptor descriptor() {
        return new GameDescriptor(
                "thingsInRings",
                "环中物语",
                "/thingsInRings",
                "/thingsInRings/rule",
                2,
                6,
                "主持人掌握隐藏规则，竞猜者推理词语应该落入哪些环"
        );
    }

    @Override
    public Map<String, Object> defaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("difficulty", "hard");
        config.put("spectatorView", "god");
        config.put("customRules", new LinkedHashMap<>(Map.of(
                "SCENE", "",
                "WORD", "",
                "ATTRIBUTE", ""
        )));
        return config;
    }

    @Override
    public Map<String, Object> initialState(RoomEntity room, List<RoomSeat> seats) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("phase", "waiting");
        state.put("players", playersOf(seats));
        state.put("hostPlayerId", null);
        state.put("currentPlayerId", null);
        state.put("rules", emptyRules());
        state.put("wordDeck", List.of());
        state.put("playerHands", new LinkedHashMap<>());
        state.put("placedWords", emptyPlacedWords());
        state.put("currentHostWord", null);
        state.put("initialPlacementsRemaining", 0);
        state.put("hasCurrentGuesserPlacedCorrectly", false);
        state.put("hostHintUsed", false);
        state.put("hostSkipUsed", false);
        state.put("pendingGuess", null);
        state.put("winners", List.of());
        state.put("notices", new ArrayList<>());
        return state;
    }

    @Override
    public Map<String, Object> sanitizeConfig(Map<String, Object> requested, RoomEntity room, List<RoomSeat> seats) {
        Map<String, Object> merged = new LinkedHashMap<>(defaultConfig());
        if (requested != null) {
            merged.putAll(requested);
        }

        String difficulty = Objects.toString(merged.get("difficulty"), "hard");
        if (!List.of("easy", "medium", "hard", "custom").contains(difficulty)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "环中物语难度不支持");
        }
        String spectatorView = Objects.toString(merged.get("spectatorView"), "god");
        if (!List.of("god", "player").contains(spectatorView)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "观战视角不支持");
        }

        merged.put("difficulty", difficulty);
        merged.put("spectatorView", spectatorView);
        merged.put("customRules", normalizeCustomRules(merged.get("customRules")));
        return merged;
    }

    @Override
    public void validateCanStart(RoomEntity room, List<RoomSeat> seats) {
        int seated = playersOf(seats).size();
        if (seated < gameType().minPlayers()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "至少需要2名玩家");
        }
        if (seated > gameType().maxPlayers()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "最多支持6名玩家");
        }
    }

    @Override
    public Map<String, Object> onStart(RoomEntity room, List<RoomSeat> seats, Map<String, Object> config) {
        Map<String, Object> state = initialState(room, seats);
        state.put("phase", "SELECTING_HOST");
        state.put("players", playersOf(seats));
        appendNotice(state, "环中物语开始，请房主选择主持人。");
        return state;
    }

    @Override
    public Map<String, Object> onAction(RoomEntity room, List<RoomSeat> seats, Map<String, Object> config,
                                        Map<String, Object> state, UserSummary actor, String actionType,
                                        Map<String, Object> payload, List<String> notices) {
        return switch (actionType) {
            case "set_host" -> setHost(room, seats, config, state, actor, payload);
            case "random_set_host" -> randomSetHost(room, seats, config, state, actor);
            case "host_place" -> hostPlace(state, actor, payload);
            case "guesser_submit" -> guesserSubmit(state, actor, payload);
            case "host_judge" -> hostJudge(state, actor, payload, seats);
            case "end_turn" -> endGuesserTurn(state, actor, seats);
            case "host_hint" -> hostHint(state, actor);
            case "host_skip" -> hostSkip(state, actor, seats);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的环中物语动作");
        };
    }

    @Override
    public List<String> rules() {
        return List.of(
                "房主选择主持人后，主持人获得情境、词汇、属性三条隐藏规则。",
                "主持人先放置3张提示词语，竞猜者随后轮流尝试把手牌放入8个区域。",
                "竞猜者放置正确后可以结束回合；放置错误会补一张词语并强制结束回合。",
                "主持人回合开始前检查是否有竞猜者手牌清空，清空者获胜。"
        );
    }

    private Map<String, Object> setHost(RoomEntity room, List<RoomSeat> seats, Map<String, Object> config,
                                        Map<String, Object> state, UserSummary actor, Map<String, Object> payload) {
        ensureOwner(room, actor);
        ensurePhase(state, "SELECTING_HOST");
        long hostId = asLong(payload.get("userId"));
        return prepareHostState(seats, config, state, hostId);
    }

    private Map<String, Object> randomSetHost(RoomEntity room, List<RoomSeat> seats, Map<String, Object> config,
                                              Map<String, Object> state, UserSummary actor) {
        ensureOwner(room, actor);
        ensurePhase(state, "SELECTING_HOST");
        List<Map<String, Object>> players = playersOf(seats);
        long hostId = asLong(players.get(random.nextInt(players.size())).get("userId"));
        return prepareHostState(seats, config, state, hostId);
    }

    private Map<String, Object> prepareHostState(List<RoomSeat> seats, Map<String, Object> config,
                                                 Map<String, Object> state, long hostId) {
        List<Map<String, Object>> players = playersOf(seats);
        Map<String, Object> host = findPlayer(players, hostId);
        if (host == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "主持人必须是在座玩家");
        }

        List<Map<String, Object>> deck = shuffledWords();
        Map<String, Object> hands = new LinkedHashMap<>();
        for (Map<String, Object> player : players) {
            long playerId = asLong(player.get("userId"));
            hands.put(String.valueOf(playerId), playerId == hostId ? new ArrayList<>() : drawMany(deck, STARTING_HAND_SIZE));
        }

        state.put("players", players);
        state.put("hostPlayerId", hostId);
        state.put("currentPlayerId", hostId);
        state.put("rules", rulesFor(config));
        state.put("wordDeck", deck);
        state.put("playerHands", hands);
        state.put("placedWords", emptyPlacedWords());
        state.put("phase", "HOST_INITIAL_PLACEMENT");
        state.put("initialPlacementsRemaining", 3);
        state.put("currentHostWord", drawOne(deck));
        state.put("winners", List.of());
        state.put("pendingGuess", null);
        appendNotice(state, "玩家" + host.get("username") + "成为主持人，请完成3次主持人放置。");
        return state;
    }

    private Map<String, Object> hostPlace(Map<String, Object> state, UserSummary actor, Map<String, Object> payload) {
        ensureHost(state, actor);
        String phase = Objects.toString(state.get("phase"), "");
        if (!List.of("HOST_INITIAL_PLACEMENT", "HOST_TURN").contains(phase)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前不能执行主持人放置");
        }
        String area = validArea(payload.get("area"));
        Map<String, Object> word = mapOf(state.get("currentHostWord"));
        if (word.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前没有待放置词语");
        }

        Map<String, Object> placedWords = placedWordsOf(state);
        listAt(placedWords, area).add(word);
        state.put("placedWords", placedWords);
        appendNotice(state, "主持人将" + wordName(word) + "放在" + areaShort(area) + "区域。");

        List<Map<String, Object>> deck = wordDeckOf(state);
        state.put("wordDeck", deck);
        if ("HOST_INITIAL_PLACEMENT".equals(phase)) {
            int remaining = parseInteger(state.get("initialPlacementsRemaining"), 0) - 1;
            state.put("initialPlacementsRemaining", remaining);
            if (remaining > 0) {
                state.put("currentHostWord", drawOne(deck));
            } else {
                state.put("currentHostWord", null);
                beginGuesserTurnAfterHost(state);
            }
        } else {
            state.put("currentHostWord", null);
        }
        return state;
    }

    private Map<String, Object> guesserSubmit(Map<String, Object> state, UserSummary actor, Map<String, Object> payload) {
        ensurePhase(state, "GUESSER_TURN");
        Long currentPlayerId = nullableLong(state.get("currentPlayerId"));
        if (!Objects.equals(currentPlayerId, actor.id())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "还没轮到你放置词语");
        }
        if (!mapOf(state.get("pendingGuess")).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "上一条放置仍在等待主持人判断");
        }
        String area = validArea(payload.get("area"));
        String wordId = Objects.toString(payload.get("wordId"), "");
        Map<String, Object> word = findWordInHand(state, actor.id(), wordId);
        if (word == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "词语卡不存在");
        }

        state.put("pendingGuess", new LinkedHashMap<>(Map.of(
                "playerId", actor.id(),
                "playerName", actor.username(),
                "word", word,
                "area", area
        )));
        appendNotice(state, "玩家" + actor.username() + "提交了一个放置，等待主持人判断。");
        return state;
    }

    private Map<String, Object> hostJudge(Map<String, Object> state, UserSummary actor, Map<String, Object> payload,
                                          List<RoomSeat> seats) {
        ensureHost(state, actor);
        ensurePhase(state, "GUESSER_TURN");
        Map<String, Object> pending = mapOf(state.get("pendingGuess"));
        if (pending.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前没有待判断的放置");
        }

        long playerId = asLong(pending.get("playerId"));
        String playerName = Objects.toString(pending.get("playerName"), "");
        String proposedArea = Objects.toString(pending.get("area"), "");
        Map<String, Object> word = mapOf(pending.get("word"));
        boolean correct = Boolean.TRUE.equals(payload.get("correct"));
        String finalArea = correct ? proposedArea : validArea(payload.get("correctArea"));
        if (!correct && finalArea.equals(proposedArea)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "正确区域不能与玩家选择相同");
        }

        removeWordFromHand(state, playerId, Objects.toString(word.get("id"), ""));
        Map<String, Object> placedWords = placedWordsOf(state);
        listAt(placedWords, finalArea).add(word);
        state.put("placedWords", placedWords);
        state.put("pendingGuess", null);

        if (correct) {
            state.put("hasCurrentGuesserPlacedCorrectly", true);
            appendNotice(state, "玩家" + playerName + "尝试将" + wordName(word) + "词语放到" + areaShort(proposedArea) + "区域。放置正确！");
        } else {
            drawToHand(state, playerId);
            state.put("hasCurrentGuesserPlacedCorrectly", false);
            appendNotice(state, "玩家" + playerName + "尝试将" + wordName(word) + "词语放到" + areaShort(proposedArea)
                    + "区域。放置错误！正确的区域为" + areaShort(finalArea) + "！");
            advanceAfterGuesser(state, playerId, seats);
        }
        return state;
    }

    private Map<String, Object> endGuesserTurn(Map<String, Object> state, UserSummary actor, List<RoomSeat> seats) {
        ensurePhase(state, "GUESSER_TURN");
        Long currentPlayerId = nullableLong(state.get("currentPlayerId"));
        if (!Objects.equals(currentPlayerId, actor.id())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "还没轮到你结束回合");
        }
        if (!Boolean.TRUE.equals(state.get("hasCurrentGuesserPlacedCorrectly"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "本回合至少需要正确放置一次");
        }
        if (!mapOf(state.get("pendingGuess")).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "仍有放置等待主持人判断");
        }
        appendNotice(state, "玩家" + actor.username() + "结束回合。");
        advanceAfterGuesser(state, actor.id(), seats);
        return state;
    }

    private Map<String, Object> hostHint(Map<String, Object> state, UserSummary actor) {
        ensureHost(state, actor);
        ensurePhase(state, "HOST_TURN");
        if (Boolean.TRUE.equals(state.get("hostHintUsed"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "本回合已经使用过提示");
        }
        if (!mapOf(state.get("currentHostWord")).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请先放置当前提示词语");
        }
        List<Map<String, Object>> deck = wordDeckOf(state);
        Map<String, Object> word = drawOne(deck);
        state.put("wordDeck", deck);
        state.put("currentHostWord", word);
        state.put("hostHintUsed", true);
        if (word == null) {
            appendNotice(state, "词语卡牌库已为空，无法继续抽取词语卡。");
        } else {
            appendNotice(state, "主持人抽取了一张提示词语卡。");
        }
        return state;
    }

    private Map<String, Object> hostSkip(Map<String, Object> state, UserSummary actor, List<RoomSeat> seats) {
        ensureHost(state, actor);
        ensurePhase(state, "HOST_TURN");
        if (Boolean.TRUE.equals(state.get("hostSkipUsed"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "本回合已经跳过");
        }
        if (!mapOf(state.get("currentHostWord")).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请先放置当前提示词语");
        }
        state.put("hostSkipUsed", true);
        appendNotice(state, "主持人结束回合。");
        beginGuesserTurnAfterHost(state);
        return state;
    }

    private void advanceAfterGuesser(Map<String, Object> state, long currentPlayerId, List<RoomSeat> seats) {
        List<Map<String, Object>> players = playersOf(seats);
        long hostId = asLong(state.get("hostPlayerId"));
        int currentIndex = indexOfPlayer(players, currentPlayerId);
        for (int step = 1; step <= players.size(); step++) {
            Map<String, Object> next = players.get((currentIndex + step) % players.size());
            long nextId = asLong(next.get("userId"));
            if (nextId == hostId) {
                beginHostTurnOrGameOver(state);
                return;
            }
            state.put("phase", "GUESSER_TURN");
            state.put("currentPlayerId", nextId);
            state.put("hasCurrentGuesserPlacedCorrectly", false);
            appendNotice(state, "轮到玩家" + next.get("username") + "行动。");
            return;
        }
        beginHostTurnOrGameOver(state);
    }

    private void beginGuesserTurnAfterHost(Map<String, Object> state) {
        List<Map<String, Object>> players = playerListOf(state);
        long hostId = asLong(state.get("hostPlayerId"));
        int hostIndex = indexOfPlayer(players, hostId);
        for (int step = 1; step <= players.size(); step++) {
            Map<String, Object> next = players.get((hostIndex + step) % players.size());
            long nextId = asLong(next.get("userId"));
            if (nextId != hostId) {
                state.put("phase", "GUESSER_TURN");
                state.put("currentPlayerId", nextId);
                state.put("hasCurrentGuesserPlacedCorrectly", false);
                state.put("hostHintUsed", false);
                state.put("hostSkipUsed", false);
                appendNotice(state, "轮到玩家" + next.get("username") + "行动。");
                return;
            }
        }
    }

    private void beginHostTurnOrGameOver(Map<String, Object> state) {
        List<Map<String, Object>> winners = winnersOf(state);
        if (!winners.isEmpty()) {
            state.put("phase", "GAME_OVER");
            state.put("currentPlayerId", null);
            state.put("winners", winners);
            String names = winners.stream()
                    .map(winner -> String.valueOf(winner.get("username")))
                    .reduce((left, right) -> left + "、" + right)
                    .orElse("");
            appendNotice(state, "游戏结束，玩家" + names + "获胜！");
            return;
        }

        state.put("phase", "HOST_TURN");
        state.put("currentPlayerId", state.get("hostPlayerId"));
        state.put("hasCurrentGuesserPlacedCorrectly", false);
        state.put("hostHintUsed", false);
        state.put("hostSkipUsed", false);
        appendNotice(state, "进入主持人回合。");
    }

    private List<Map<String, Object>> winnersOf(Map<String, Object> state) {
        long hostId = asLong(state.get("hostPlayerId"));
        Map<String, Object> hands = handsOf(state);
        return playerListOf(state).stream()
                .filter(player -> asLong(player.get("userId")) != hostId)
                .filter(player -> listOf(hands.get(String.valueOf(player.get("userId")))).isEmpty())
                .toList();
    }

    private Map<String, Object> rulesFor(Map<String, Object> config) {
        String difficulty = Objects.toString(config.get("difficulty"), "hard");
        if ("custom".equals(difficulty)) {
            Map<String, Object> custom = normalizeCustomRules(config.get("customRules"));
            return new LinkedHashMap<>(Map.of(
                    "SCENE", ruleCard("情境", Objects.toString(custom.get("SCENE"), "自定义情境规则"), "自定义", ""),
                    "WORD", ruleCard("词汇", Objects.toString(custom.get("WORD"), "自定义词汇规则"), "自定义", ""),
                    "ATTRIBUTE", ruleCard("属性", Objects.toString(custom.get("ATTRIBUTE"), "自定义属性规则"), "自定义", "")
            ));
        }

        List<String> levels = switch (difficulty) {
            case "easy" -> List.of("1");
            case "medium" -> List.of("1", "2");
            default -> List.of("1", "2", "3");
        };
        List<Map<String, Object>> rules = loadJsonArray("rules.json");
        Map<String, Object> selected = new LinkedHashMap<>();
        selected.put("SCENE", randomRule(rules, "情境", levels));
        selected.put("WORD", randomRule(rules, "词汇", levels));
        selected.put("ATTRIBUTE", randomRule(rules, "属性", levels));
        return selected;
    }

    private Map<String, Object> randomRule(List<Map<String, Object>> rules, String type, List<String> levels) {
        List<Map<String, Object>> candidates = rules.stream()
                .filter(rule -> type.equals(rule.get("type")))
                .filter(rule -> levels.contains(String.valueOf(rule.get("level"))))
                .toList();
        if (candidates.isEmpty()) {
            return ruleCard(type, "未找到可用规则", "", "");
        }
        return normalizeRuleCard(candidates.get(random.nextInt(candidates.size())), type);
    }

    private List<Map<String, Object>> shuffledWords() {
        List<Map<String, Object>> words = new ArrayList<>();
        int index = 1;
        for (Map<String, Object> source : loadJsonArray("words.json")) {
            Map<String, Object> word = new LinkedHashMap<>(source);
            word.put("id", "w" + index++);
            words.add(word);
        }
        Collections.shuffle(words, random);
        return words;
    }

    private List<Map<String, Object>> loadJsonArray(String fileName) {
        Path path = Path.of("src", "main", "java", "com", "fortell", "boardgame", "game_modules", "thingsInRings", fileName);
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "读取环中物语数据失败");
        }
    }

    private List<Map<String, Object>> playersOf(List<RoomSeat> seats) {
        return seats.stream()
                .filter(RoomSeat::occupied)
                .filter(seat -> !seat.bot())
                .sorted(Comparator.comparingInt(RoomSeat::seatIndex))
                .map(seat -> {
                    Map<String, Object> player = new LinkedHashMap<>();
                    player.put("userId", seat.userId());
                    player.put("username", seat.username());
                    player.put("seatIndex", seat.seatIndex());
                    return player;
                })
                .toList();
    }

    private Map<String, Object> emptyRules() {
        return new LinkedHashMap<>(Map.of(
                "SCENE", ruleCard("情境", "", "", ""),
                "WORD", ruleCard("词汇", "", "", ""),
                "ATTRIBUTE", ruleCard("属性", "", "", "")
        ));
    }

    private Map<String, Object> ruleCard(String type, String description, String level, String note) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("type", type);
        rule.put("description", description == null || description.isBlank() ? "自定义" + type + "规则" : description);
        rule.put("level", level);
        rule.put("note", note == null ? "" : note);
        return rule;
    }

    private Map<String, Object> normalizeRuleCard(Map<String, Object> rawRule, String fallbackType) {
        String description = Objects.toString(
                rawRule.getOrDefault("description", rawRule.getOrDefault("describe", "")),
                ""
        );
        String type = Objects.toString(rawRule.getOrDefault("type", fallbackType), fallbackType);
        String level = Objects.toString(rawRule.getOrDefault("level", ""), "");
        String note = Objects.toString(rawRule.getOrDefault("note", ""), "");
        return ruleCard(type, description, level, note);
    }

    private Map<String, Object> emptyPlacedWords() {
        Map<String, Object> areas = new LinkedHashMap<>();
        for (String area : AREAS) {
            areas.put(area, new ArrayList<>());
        }
        return areas;
    }

    private Map<String, Object> normalizeCustomRules(Object raw) {
        Map<String, Object> input = mapOf(raw);
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("SCENE", Objects.toString(input.getOrDefault("SCENE", ""), ""));
        rules.put("WORD", Objects.toString(input.getOrDefault("WORD", ""), ""));
        rules.put("ATTRIBUTE", Objects.toString(input.getOrDefault("ATTRIBUTE", ""), ""));
        return rules;
    }

    private List<Map<String, Object>> drawMany(List<Map<String, Object>> deck, int count) {
        List<Map<String, Object>> drawn = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Map<String, Object> word = drawOne(deck);
            if (word == null) {
                break;
            }
            drawn.add(word);
        }
        return drawn;
    }

    private Map<String, Object> drawOne(List<Map<String, Object>> deck) {
        if (deck.isEmpty()) {
            return null;
        }
        return deck.remove(0);
    }

    private void drawToHand(Map<String, Object> state, long playerId) {
        List<Map<String, Object>> deck = wordDeckOf(state);
        Map<String, Object> word = drawOne(deck);
        state.put("wordDeck", deck);
        if (word == null) {
            appendNotice(state, "词语卡牌库已为空，无法继续抽取词语卡。");
            return;
        }
        Map<String, Object> hands = handsOf(state);
        listAt(hands, String.valueOf(playerId)).add(word);
        state.put("playerHands", hands);
    }

    private Map<String, Object> findWordInHand(Map<String, Object> state, long playerId, String wordId) {
        return handOf(state, playerId).stream()
                .filter(word -> wordId.equals(String.valueOf(word.get("id"))))
                .findFirst()
                .orElse(null);
    }

    private void removeWordFromHand(Map<String, Object> state, long playerId, String wordId) {
        Map<String, Object> hands = handsOf(state);
        List<Map<String, Object>> hand = listAt(hands, String.valueOf(playerId));
        hand.removeIf(word -> wordId.equals(String.valueOf(word.get("id"))));
        hands.put(String.valueOf(playerId), hand);
        state.put("playerHands", hands);
    }

    private List<Map<String, Object>> handOf(Map<String, Object> state, long playerId) {
        return listOf(handsOf(state).get(String.valueOf(playerId)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOf(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    private List<Map<String, Object>> playerListOf(Map<String, Object> state) {
        return listOf(state.get("players"));
    }

    private List<Map<String, Object>> wordDeckOf(Map<String, Object> state) {
        return listOf(state.get("wordDeck"));
    }

    private Map<String, Object> handsOf(Map<String, Object> state) {
        return mapOf(state.get("playerHands"));
    }

    private Map<String, Object> placedWordsOf(Map<String, Object> state) {
        Map<String, Object> placedWords = mapOf(state.get("placedWords"));
        for (String area : AREAS) {
            placedWords.putIfAbsent(area, new ArrayList<>());
        }
        return placedWords;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOf(Object raw) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return values;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                values.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listAt(Map<String, Object> map, String key) {
        Object raw = map.get(key);
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> values = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> itemMap) {
                    values.add(new LinkedHashMap<>((Map<String, Object>) itemMap));
                }
            }
            map.put(key, values);
            return values;
        }
        List<Map<String, Object>> values = new ArrayList<>();
        map.put(key, values);
        return values;
    }

    private Map<String, Object> findPlayer(List<Map<String, Object>> players, long playerId) {
        return players.stream()
                .filter(player -> asLong(player.get("userId")) == playerId)
                .findFirst()
                .orElse(null);
    }

    private int indexOfPlayer(List<Map<String, Object>> players, long playerId) {
        for (int index = 0; index < players.size(); index++) {
            if (asLong(players.get(index).get("userId")) == playerId) {
                return index;
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "玩家不在本局游戏中");
    }

    private void ensureOwner(RoomEntity room, UserSummary actor) {
        if (room.ownerUserId() != actor.id()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只有房主可以执行该操作");
        }
    }

    private void ensureHost(Map<String, Object> state, UserSummary actor) {
        if (!Objects.equals(nullableLong(state.get("hostPlayerId")), actor.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只有主持人可以执行该操作");
        }
    }

    private void ensurePhase(Map<String, Object> state, String expected) {
        String phase = Objects.toString(state.get("phase"), "");
        if (!expected.equals(phase)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前阶段不能执行该操作");
        }
    }

    private String validArea(Object raw) {
        String area = Objects.toString(raw, "");
        if (!AREAS.contains(area)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "区域不合法");
        }
        return area;
    }

    private String areaShort(String area) {
        return AREA_SHORT_NAMES.getOrDefault(area, area);
    }

    private String wordName(Map<String, Object> word) {
        return Objects.toString(word.get("Chinese"), "");
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

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Long nullableLong(Object value) {
        if (value == null) {
            return null;
        }
        return asLong(value);
    }

    @SuppressWarnings("unchecked")
    private void appendNotice(Map<String, Object> state, String notice) {
        List<String> notices = new ArrayList<>((List<String>) state.getOrDefault("notices", List.of()));
        notices.add(0, notice);
        state.put("notices", notices.stream().limit(40).toList());
    }
}
