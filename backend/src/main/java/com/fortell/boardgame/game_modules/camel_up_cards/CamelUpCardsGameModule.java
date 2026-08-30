package com.fortell.boardgame.game_modules.camel_up_cards;

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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class CamelUpCardsGameModule implements GameModule {
    private static final List<String> RACING_COLORS = List.of("red", "yellow", "blue", "green", "purple");
    private static final List<String> ALL_COLORS = List.of("red", "yellow", "blue", "green", "purple", "black");
    private static final Map<String, String> COLOR_NAMES = Map.of(
            "red", "红色",
            "yellow", "黄色",
            "blue", "蓝色",
            "green", "绿色",
            "purple", "紫色",
            "black", "黑色"
    );

    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    public CamelUpCardsGameModule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public GameType gameType() {
        return GameType.CAMEL_UP_CARDS;
    }

    @Override
    public GameDescriptor descriptor() {
        return new GameDescriptor(
                "camel_up_cards",
                "狂野骆驼：卡牌版",
                "/camel_up_cards",
                "/camel_up_cards/rule",
                2,
                6,
                "卡牌驱动的骆驼竞速、赛段下注和最终下注"
        );
    }

    @Override
    public Map<String, Object> defaultConfig() {
        return new LinkedHashMap<>(Map.of("expansion", "shortcut_fennec"));
    }

    @Override
    public Map<String, Object> initialState(RoomEntity room, List<RoomSeat> seats) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("phase", "waiting");
        state.put("players", playersOf(seats));
        state.put("currentPlayerId", null);
        state.put("startingPlayerId", null);
        state.put("leg", 0);
        state.put("finishPosition", 0);
        state.put("stacks", new LinkedHashMap<>());
        state.put("raceDeck", List.of());
        state.put("raceDiscard", List.of());
        state.put("temporaryDiscard", List.of());
        state.put("hands", new LinkedHashMap<>());
        state.put("setup", new LinkedHashMap<>());
        state.put("money", new LinkedHashMap<>());
        state.put("playerTokens", new LinkedHashMap<>());
        state.put("trackTokens", new LinkedHashMap<>());
        state.put("betMarket", emptyBetMarket());
        state.put("bets", new LinkedHashMap<>());
        state.put("turn", new LinkedHashMap<>());
        state.put("rankings", List.of());
        state.put("sandstormPairs", List.of());
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
        String expansion = Objects.toString(merged.get("expansion"), "shortcut_fennec");
        if (!List.of("none", "shortcut", "fennec", "shortcut_fennec").contains(expansion)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "扩展规则不支持");
        }
        merged.put("expansion", expansion);
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
        List<Map<String, Object>> players = playersOf(seats);
        long startingPlayerId = asLong(players.get(random.nextInt(players.size())).get("userId"));
        int finishPosition = 13 + players.size();

        Map<String, Object> state = initialState(room, seats);
        state.put("phase", "SETUP_SELECTION");
        state.put("players", players);
        state.put("currentPlayerId", startingPlayerId);
        state.put("startingPlayerId", startingPlayerId);
        state.put("leg", 1);
        state.put("finishPosition", finishPosition);
        state.put("money", initialMoney(players));
        state.put("bets", emptyPlayerBets(players));
        state.put("playerTokens", initialPlayerTokens(players, config));
        state.put("trackTokens", initialTrackTokens(config));
        state.put("stacks", initialStacks());

        List<Map<String, Object>> normalCards = normalRaceCards();
        shuffle(normalCards);
        for (int index = 0; index < 5 && !normalCards.isEmpty(); index++) {
            Map<String, Object> card = normalCards.remove(0);
            moveCamel(state, card);
            appendNotice(state, "开局展示" + cardText(card) + "。");
        }
        putCamelAtBottom(state, "black", 7);
        beginSetupSelection(state, players, startingPlayerId);
        appendNotice(state, "起始玩家为" + playerName(players, startingPlayerId) + "。");
        return state;
    }

    @Override
    public Map<String, Object> onAction(RoomEntity room, List<RoomSeat> seats, Map<String, Object> config,
                                        Map<String, Object> state, UserSummary actor, String actionType,
                                        Map<String, Object> payload, List<String> notices) {
        if ("submit_setup_selection".equals(actionType)) {
            return submitSetupSelection(state, actor, payload);
        }
        ensurePlaying(state);
        ensureCurrentPlayer(state, actor);
        return switch (actionType) {
            case "place_token" -> placeToken(state, actor, payload);
            case "draw_race_card" -> drawRaceCard(state, actor);
            case "play_hand" -> playHand(state, actor, payload);
            case "take_bet" -> takeBet(state, actor, payload);
            case "skip_bet" -> skipBet(state, actor);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的狂野骆驼动作");
        };
    }

    @Override
    public List<String> rules() {
        return List.of(
                "每回合必须执行一个赛道行动，可以在赛道行动前或后执行一个下注行动。",
                "竞赛骆驼向终点移动，黑色疯狂骆驼向起点移动。",
                "赛段牌库为空后结算赛段；任一竞赛骆驼到达终点后结算终局。"
        );
    }

    private Map<String, Object> placeToken(Map<String, Object> state, UserSummary actor, Map<String, Object> payload) {
        ensureTrackActionAvailable(state);
        String tokenType = Objects.toString(payload.get("tokenType"), "");
        if (!List.of("shortcut", "fennec").contains(tokenType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "标记类型不合法");
        }
        int position = parseInteger(payload.get("position"), -1);
        int finishPosition = parseInteger(state.get("finishPosition"), 0);
        if (position <= 0 || position >= finishPosition) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "标记不能放在起点或终点");
        }
        if (!stackAt(state, position).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "只能放在空格");
        }
        if (isSandstormPosition(state, position)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不能放在沙尘暴格");
        }

        Map<String, Object> trackTokens = mapOf(state.get("trackTokens"));
        if (hasTrackTokenAt(trackTokens, position)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该格已有捷径或耳廓狐标记");
        }
        Map<String, Object> trackToken = mapOf(trackTokens.get(tokenType));
        if (!Boolean.TRUE.equals(trackToken.get("enabled")) || !"unplaced".equals(trackToken.get("status"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该标记当前不能放置");
        }
        Map<String, Object> playerTokens = mapOf(state.get("playerTokens"));
        Map<String, Object> actorTokens = mapOf(playerTokens.get(String.valueOf(actor.id())));
        if (!"available".equals(actorTokens.get(tokenType))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "你没有可用的该标记");
        }

        actorTokens.put(tokenType, "used");
        playerTokens.put(String.valueOf(actor.id()), actorTokens);
        trackToken.put("status", "placed");
        trackToken.put("position", position);
        trackToken.put("ownerId", actor.id());
        trackTokens.put(tokenType, trackToken);
        state.put("playerTokens", playerTokens);
        state.put("trackTokens", trackTokens);
        appendNotice(state, "玩家" + actor.username() + "执行了赛道行动，在" + position + "格放置了" + tokenName(tokenType) + "。");
        return finishTrackAction(state, actor);
    }

    private Map<String, Object> drawRaceCard(Map<String, Object> state, UserSummary actor) {
        ensureTrackActionAvailable(state);
        List<Map<String, Object>> raceDeck = listOf(state.get("raceDeck"));
        if (raceDeck.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "竞赛牌库已为空");
        }
        Map<String, Object> card = raceDeck.remove(0);
        state.put("raceDeck", raceDeck);
        MoveResult result = moveCamel(state, card);
        addTemporaryDiscard(state, card);
        appendNotice(state, "玩家" + actor.username() + "执行了赛道行动，翻开竞赛牌库顶部的一张卡牌，使得"
                + colorName(Objects.toString(card.get("color"), "")) + "骆驼前进" + card.get("steps") + "步，当前位于" + result.position() + "格。");

        boolean raceFinished = racingRankings(state).stream()
                .anyMatch(rank -> parseInteger(rank.get("position"), 0) >= parseInteger(state.get("finishPosition"), 0));
        if (raceDeck.isEmpty() || raceFinished) {
            settleLeg(state);
            if (raceFinished) {
                settleFinal(state);
                return state;
            }
            beginSetupSelection(state, playerListOf(state), nextPlayerId(state));
            return state;
        }
        return finishTrackAction(state, actor);
    }

    private Map<String, Object> playHand(Map<String, Object> state, UserSummary actor, Map<String, Object> payload) {
        ensureTrackActionAvailable(state);
        long ownerId = asLong(payload.getOrDefault("ownerUserId", actor.id()));
        Map<String, Object> hands = mapOf(state.get("hands"));
        Map<String, Object> card = mapOf(hands.get(String.valueOf(ownerId)));
        if (card.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该玩家没有可打出的手牌");
        }
        if (ownerId != actor.id()) {
            Map<String, Object> money = mapOf(state.get("money"));
            int actorMoney = parseInteger(money.get(String.valueOf(actor.id())), 0);
            if (actorMoney < 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "埃及镑不足，无法使用他人手牌");
            }
            money.put(String.valueOf(actor.id()), actorMoney - 1);
            money.put(String.valueOf(ownerId), parseInteger(money.get(String.valueOf(ownerId)), 0) + 1);
            state.put("money", money);
        }
        hands.put(String.valueOf(ownerId), null);
        state.put("hands", hands);

        MoveResult result = moveCamel(state, card);
        addTemporaryDiscard(state, card);
        appendNotice(state, "玩家" + actor.username() + "执行了赛道行动，打出"
                + (ownerId == actor.id() ? "自己" : "玩家" + playerName(playerListOf(state), ownerId))
                + "的手牌，使得" + colorName(Objects.toString(card.get("color"), "")) + "骆驼前进"
                + card.get("steps") + "步，当前位于" + result.position() + "格。");

        boolean raceFinished = racingRankings(state).stream()
                .anyMatch(rank -> parseInteger(rank.get("position"), 0) >= parseInteger(state.get("finishPosition"), 0));
        if (raceFinished) {
            settleLeg(state);
            settleFinal(state);
            return state;
        }
        return finishTrackAction(state, actor);
    }

    private Map<String, Object> takeBet(Map<String, Object> state, UserSummary actor, Map<String, Object> payload) {
        ensureBetActionAvailable(state);
        String cardId = Objects.toString(payload.get("cardId"), "");
        Map<String, Object> market = mapOf(state.get("betMarket"));
        Map<String, Object> card = removeBetCard(market, cardId);
        if (card.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "下注卡牌不存在");
        }

        String type = Objects.toString(card.get("type"), "");
        Map<String, Object> bets = mapOf(state.get("bets"));
        Map<String, Object> actorBets = mapOf(bets.get(String.valueOf(actor.id())));
        if ("final_winner".equals(type) || "final_loser".equals(type)) {
            Map<String, Object> previous = mapOf(actorBets.get(type));
            if (!previous.isEmpty()) {
                addBetCard(market, previous);
            }
            actorBets.put(type, card);
        } else {
            List<Map<String, Object>> leg = listOf(actorBets.get("leg"));
            leg.add(card);
            actorBets.put("leg", leg);
        }
        bets.put(String.valueOf(actor.id()), actorBets);
        state.put("betMarket", market);
        state.put("bets", bets);

        appendNotice(state, "玩家" + actor.username() + "执行了下注行动，拿取" + colorName(Objects.toString(card.get("color"), ""))
                + "骆驼的" + betCardName(card) + "卡牌。");
        return finishBetAction(state, actor);
    }

    private Map<String, Object> skipBet(Map<String, Object> state, UserSummary actor) {
        Map<String, Object> turn = mapOf(state.get("turn"));
        if (!Boolean.TRUE.equals(turn.get("trackActionDone")) || Boolean.TRUE.equals(turn.get("betActionDone"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前不能跳过下注");
        }
        appendNotice(state, "玩家" + actor.username() + "跳过下注。");
        return advanceTurn(state);
    }

    private Map<String, Object> finishTrackAction(Map<String, Object> state, UserSummary actor) {
        Map<String, Object> turn = mapOf(state.get("turn"));
        turn.put("trackActionDone", true);
        state.put("turn", turn);
        state.put("rankings", allRankings(state));
        if (Boolean.TRUE.equals(turn.get("betActionDone"))) {
            return advanceTurn(state);
        }
        return state;
    }

    private Map<String, Object> finishBetAction(Map<String, Object> state, UserSummary actor) {
        Map<String, Object> turn = mapOf(state.get("turn"));
        turn.put("betActionDone", true);
        state.put("turn", turn);
        if (Boolean.TRUE.equals(turn.get("trackActionDone"))) {
            return advanceTurn(state);
        }
        return state;
    }

    private Map<String, Object> advanceTurn(Map<String, Object> state) {
        List<Map<String, Object>> players = playerListOf(state);
        long current = asLong(state.get("currentPlayerId"));
        int index = indexOfPlayer(players, current);
        Map<String, Object> next = players.get((index + 1) % players.size());
        state.put("currentPlayerId", next.get("userId"));
        state.put("turn", newTurn());
        appendNotice(state, "轮到玩家" + next.get("username") + "行动。");
        return state;
    }

    private void beginSetupSelection(Map<String, Object> state, List<Map<String, Object>> players, long nextPlayerId) {
        moveBlackIfLagging(state);
        state.put("betMarket", buildLegBetMarketPreservingFinal(state));
        resetTrackTokensForLeg(state);
        maybeExtendTrack(state);
        state.put("temporaryDiscard", new ArrayList<>());
        state.put("phase", "SETUP_SELECTION");
        state.put("turn", new LinkedHashMap<>());
        state.put("currentPlayerId", nextPlayerId);
        state.put("setup", buildSetupState(players, nextPlayerId));
        state.put("rankings", allRankings(state));
        state.put("sandstormPairs", sandstormPairs(state));
        appendNotice(state, "第" + state.get("leg") + "赛段构筑开始，请所有玩家秘密选择竞赛卡牌。");
    }

    private Map<String, Object> buildSetupState(List<Map<String, Object>> players, long nextPlayerId) {
        int count = players.size();
        int dealCount = switch (count) {
            case 2 -> 7;
            case 3, 4 -> 6;
            default -> 4;
        };
        int discardCount = count <= 4 ? 3 : 2;
        int deckCount = count == 2 ? 3 : count <= 4 ? 2 : 1;
        List<Map<String, Object>> normalCards = normalRaceCards();
        shuffle(normalCards);
        Map<String, Object> selections = new LinkedHashMap<>();

        for (Map<String, Object> player : players) {
            List<Map<String, Object>> dealt = drawMany(normalCards, dealCount);
            selections.put(String.valueOf(player.get("userId")), new LinkedHashMap<>(Map.of(
                    "status", "discard",
                    "cards", dealt,
                    "discarded", new ArrayList<>(),
                    "deckCards", new ArrayList<>(),
                    "hand", new LinkedHashMap<>()
            )));
        }
        Map<String, Object> setup = new LinkedHashMap<>();
        setup.put("discardRequired", discardCount);
        setup.put("deckRequired", deckCount);
        setup.put("addBackCount", count == 5 ? 2 : count == 6 ? 3 : 0);
        setup.put("nextPlayerId", nextPlayerId);
        setup.put("remainingCards", normalCards);
        setup.put("selections", selections);
        return setup;
    }

    private Map<String, Object> submitSetupSelection(Map<String, Object> state, UserSummary actor, Map<String, Object> payload) {
        if (!"SETUP_SELECTION".equals(state.get("phase"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前不在赛段构筑阶段");
        }
        Map<String, Object> setup = mapOf(state.get("setup"));
        Map<String, Object> selections = mapOf(setup.get("selections"));
        Map<String, Object> selection = mapOf(selections.get(String.valueOf(actor.id())));
        if (selection.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "你不在本局游戏中");
        }

        String status = Objects.toString(selection.get("status"), "");
        List<String> cardIds = stringList(payload.get("cardIds"));
        int required = "discard".equals(status)
                ? parseInteger(setup.get("discardRequired"), 0)
                : parseInteger(setup.get("deckRequired"), 0);
        if (!List.of("discard", "deck").contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "你已经完成本赛段构筑");
        }
        if (cardIds.size() != required) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择" + required + "张卡牌");
        }

        List<Map<String, Object>> cards = listOf(selection.get("cards"));
        List<Map<String, Object>> picked = removeCardsByIds(cards, cardIds);
        if (picked.size() != required) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "选择的卡牌不合法");
        }

        if ("discard".equals(status)) {
            selection.put("discarded", picked);
            selection.put("cards", cards);
            selection.put("status", "deck");
            appendNotice(state, "玩家" + actor.username() + "完成秘密弃牌。");
        } else {
            selection.put("deckCards", picked);
            selection.put("cards", cards);
            selection.put("hand", cards.isEmpty() ? new LinkedHashMap<>() : cards.remove(0));
            selection.put("extraDiscarded", cards);
            selection.put("status", "done");
            appendNotice(state, "玩家" + actor.username() + "完成竞赛牌库选择。");
        }

        selections.put(String.valueOf(actor.id()), selection);
        setup.put("selections", selections);
        state.put("setup", setup);

        if (allSetupDone(selections)) {
            finishSetupSelection(state);
        }
        return state;
    }

    private void finishSetupSelection(Map<String, Object> state) {
        Map<String, Object> setup = mapOf(state.get("setup"));
        Map<String, Object> selections = mapOf(setup.get("selections"));
        List<Map<String, Object>> raceDiscard = listOf(setup.get("remainingCards"));
        List<Map<String, Object>> raceDeck = new ArrayList<>();
        Map<String, Object> hands = new LinkedHashMap<>();

        for (Map<String, Object> player : playerListOf(state)) {
            String playerId = String.valueOf(player.get("userId"));
            Map<String, Object> selection = mapOf(selections.get(playerId));
            raceDiscard.addAll(listOf(selection.get("discarded")));
            raceDiscard.addAll(listOf(selection.get("extraDiscarded")));
            raceDeck.addAll(listOf(selection.get("deckCards")));
            Map<String, Object> hand = mapOf(selection.get("hand"));
            hands.put(playerId, hand.isEmpty() ? null : hand);
        }

        int addBack = parseInteger(setup.get("addBackCount"), 0);
        if (addBack > 0) {
            shuffle(raceDiscard);
            raceDeck.addAll(drawMany(raceDiscard, addBack));
        }
        List<Map<String, Object>> crazyCards = crazyRaceCards();
        shuffle(crazyCards);
        raceDeck.addAll(drawMany(crazyCards, 2));
        shuffle(raceDeck);
        state.put("raceDeck", raceDeck);
        state.put("raceDiscard", raceDiscard);
        state.put("hands", hands);
        state.put("phase", "PLAYING");
        state.put("currentPlayerId", setup.get("nextPlayerId"));
        state.put("turn", newTurn());
        appendNotice(state, "第" + state.get("leg") + "赛段开始。");
    }

    private MoveResult moveCamel(Map<String, Object> state, Map<String, Object> card) {
        String color = Objects.toString(card.get("color"), "");
        int steps = parseInteger(card.get("steps"), 0);
        int direction = "black".equals(color) ? -1 : 1;
        Map<String, Object> stacks = stacksOf(state);
        List<Map<String, Object>> sandstormPairs = listOf(state.get("sandstormPairs"));
        CamelLocation location = findCamel(stacks, color);
        if (location == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "骆驼位置异常");
        }
        List<String> source = stringList(stacks.get(String.valueOf(location.position())));
        List<String> moving = new ArrayList<>(source.subList(location.index(), source.size()));
        source = new ArrayList<>(source.subList(0, location.index()));
        putStack(stacks, location.position(), source);

        if ("black".equals(color) && moving.size() > 1) {
            steps += 1;
        }
        int target = moveByTrack(state, location.position(), direction, steps, sandstormPairs);
        placeMovingStack(stacks, target, moving, direction < 0);
        state.put("stacks", stacks);

        target = applyTrackTokenIfNeeded(state, color, target);
        state.put("rankings", allRankings(state));
        return new MoveResult(target);
    }

    private int applyTrackTokenIfNeeded(Map<String, Object> state, String color, int position) {
        Map<String, Object> trackTokens = mapOf(state.get("trackTokens"));
        for (String tokenType : List.of("shortcut", "fennec")) {
            Map<String, Object> token = mapOf(trackTokens.get(tokenType));
            if (!"placed".equals(token.get("status")) || parseInteger(token.get("position"), -1) != position) {
                continue;
            }
            int direction = "shortcut".equals(tokenType)
                    ? ("black".equals(color) ? -1 : 1)
                    : ("black".equals(color) ? 1 : -1);
            moveCurrentGroupOneStep(state, color, direction, listOf(state.get("sandstormPairs")));
            resetTrackToken(token);
            trackTokens.put(tokenType, token);
            state.put("trackTokens", trackTokens);
            appendNotice(state, tokenName(tokenType) + "被触发并重置。");
            CamelLocation next = findCamel(stacksOf(state), color);
            return next == null ? position : next.position();
        }
        return position;
    }

    private void moveCurrentGroupOneStep(Map<String, Object> state, String color, int direction, List<Map<String, Object>> sandstormPairs) {
        Map<String, Object> stacks = stacksOf(state);
        CamelLocation location = findCamel(stacks, color);
        if (location == null) {
            return;
        }
        List<String> source = stringList(stacks.get(String.valueOf(location.position())));
        List<String> moving = new ArrayList<>(source.subList(location.index(), source.size()));
        source = new ArrayList<>(source.subList(0, location.index()));
        putStack(stacks, location.position(), source);
        int target = moveByTrack(state, location.position(), direction, 1, sandstormPairs);
        placeMovingStack(stacks, target, moving, direction < 0);
        state.put("stacks", stacks);
    }

    private int moveByTrack(Map<String, Object> state, int start, int direction, int steps, List<Map<String, Object>> sandstormPairs) {
        int current = start;
        int finish = parseInteger(state.get("finishPosition"), current);
        for (int step = 0; step < steps; step++) {
            int next = nextTrackPosition(current, direction, sandstormPairs);
            current = Math.max(0, Math.min(finish, next));
        }
        return current;
    }

    private int nextTrackPosition(int current, int direction, List<Map<String, Object>> sandstormPairs) {
        if (direction > 0) {
            for (Map<String, Object> pair : sandstormPairs) {
                int from = parseInteger(pair.get("from"), -1);
                int to = parseInteger(pair.get("to"), -1);
                if (current == from) {
                    return to + 1;
                }
            }
            return current + direction;
        }
        for (Map<String, Object> pair : sandstormPairs) {
            int from = parseInteger(pair.get("from"), -1);
            int to = parseInteger(pair.get("to"), -1);
            if (current == to + 1) {
                return from;
            }
        }
        return current + direction;
    }

    private void settleLeg(Map<String, Object> state) {
        List<Map<String, Object>> rankings = racingRankings(state);
        Map<String, Integer> rankByColor = rankByColor(rankings);
        Map<String, Object> money = mapOf(state.get("money"));
        Map<String, Object> bets = mapOf(state.get("bets"));
        for (Map<String, Object> player : playerListOf(state)) {
            String playerId = String.valueOf(player.get("userId"));
            int balance = parseInteger(money.get(playerId), 0);
            int income = 0;
            int loss = 0;
            Map<String, Object> playerBets = mapOf(bets.get(playerId));
            for (Map<String, Object> card : listOf(playerBets.get("leg"))) {
                int payout = payoutFor(card, rankByColor);
                if (payout >= 0) {
                    income += payout;
                } else {
                    loss += payout;
                }
            }
            balance += income;
            balance = Math.max(0, balance + loss);
            money.put(playerId, balance);
            playerBets.put("leg", new ArrayList<>());
            bets.put(playerId, playerBets);
        }
        applyPlacedTokenPenalty(state, money);
        state.put("money", money);
        state.put("bets", bets);
        state.put("leg", parseInteger(state.get("leg"), 1) + 1);
        appendNotice(state, "赛段结算完成。");
    }

    private void settleFinal(Map<String, Object> state) {
        List<Map<String, Object>> rankings = racingRankings(state);
        Map<String, Integer> rankByColor = rankByColor(rankings);
        Map<String, Object> money = mapOf(state.get("money"));
        Map<String, Object> bets = mapOf(state.get("bets"));
        for (Map<String, Object> player : playerListOf(state)) {
            String playerId = String.valueOf(player.get("userId"));
            int balance = parseInteger(money.get(playerId), 0);
            int income = 0;
            int loss = 0;
            Map<String, Object> playerBets = mapOf(bets.get(playerId));
            for (String type : List.of("final_winner", "final_loser")) {
                Map<String, Object> card = mapOf(playerBets.get(type));
                if (card.isEmpty()) {
                    continue;
                }
                int payout = payoutFor(card, rankByColor);
                if (payout >= 0) {
                    income += payout;
                } else {
                    loss += payout;
                }
            }
            balance += income;
            money.put(playerId, Math.max(0, balance + loss));
        }
        int best = money.values().stream().mapToInt(value -> parseInteger(value, 0)).max().orElse(0);
        List<Map<String, Object>> winners = playerListOf(state).stream()
                .filter(player -> parseInteger(money.get(String.valueOf(player.get("userId"))), 0) == best)
                .toList();
        state.put("money", money);
        state.put("phase", "FINISHED");
        state.put("winners", winners);
        appendNotice(state, "终局结算完成，胜者为" + winners.stream()
                .map(player -> String.valueOf(player.get("username")))
                .reduce((left, right) -> left + "、" + right)
                .orElse("") + "。");
    }

    private int payoutFor(Map<String, Object> card, Map<String, Integer> rankByColor) {
        String color = Objects.toString(card.get("color"), "");
        int rank = rankByColor.getOrDefault(color, 5);
        List<Integer> payouts = integerList(card.get("payouts"));
        return payouts.get(Math.max(0, Math.min(rank - 1, payouts.size() - 1)));
    }

    private void applyPlacedTokenPenalty(Map<String, Object> state, Map<String, Object> money) {
        Map<String, Object> tokens = mapOf(state.get("trackTokens"));
        for (String tokenType : List.of("shortcut", "fennec")) {
            Map<String, Object> token = mapOf(tokens.get(tokenType));
            if (!"placed".equals(token.get("status"))) {
                continue;
            }
            String ownerId = String.valueOf(token.get("ownerId"));
            money.put(ownerId, Math.max(0, parseInteger(money.get(ownerId), 0) - 1));
        }
    }

    private void maybeExtendTrack(Map<String, Object> state) {
        List<Map<String, Object>> rankings = racingRankings(state);
        int finish = parseInteger(state.get("finishPosition"), 0);
        if (rankings.isEmpty()) {
            return;
        }
        int leader = parseInteger(rankings.getFirst().get("position"), 0);
        if (finish - leader < 3 && finish < 24) {
            state.put("finishPosition", Math.min(leader + 4, 24));
            appendNotice(state, "赛道延长，终点变为" + state.get("finishPosition") + "格。");
        }
    }

    private void moveBlackIfLagging(Map<String, Object> state) {
        List<Map<String, Object>> rankings = allRankings(state);
        int blackRank = 1;
        for (int index = 0; index < rankings.size(); index++) {
            if ("black".equals(rankings.get(index).get("color"))) {
                blackRank = index + 1;
                break;
            }
        }
        if (blackRank < 5) {
            return;
        }
        int leaderPosition = parseInteger(rankings.getFirst().get("position"), 0);
        putCamelAtBottom(state, "black", Math.min(leaderPosition + 3, parseInteger(state.get("finishPosition"), leaderPosition + 3)));
        appendNotice(state, "疯狂骆驼移动到领先骆驼前方。");
    }

    private void resetTrackTokensForLeg(Map<String, Object> state) {
        Map<String, Object> tokens = mapOf(state.get("trackTokens"));
        for (String tokenType : List.of("shortcut", "fennec")) {
            Map<String, Object> token = mapOf(tokens.get(tokenType));
            if (Boolean.TRUE.equals(token.get("enabled"))) {
                resetTrackToken(token);
            }
            tokens.put(tokenType, token);
        }
        state.put("trackTokens", tokens);
    }

    private void resetTrackToken(Map<String, Object> token) {
        token.put("status", Boolean.TRUE.equals(token.get("enabled")) ? "unplaced" : "disabled");
        token.put("position", null);
        token.put("ownerId", null);
    }

    private Map<String, Object> buildLegBetMarketPreservingFinal(Map<String, Object> state) {
        Map<String, Object> market = emptyBetMarket();
        for (Map<String, Object> card : loadJsonArray("bet_cards.json")) {
            addBetCard(market, card);
        }
        Map<String, Object> bets = mapOf(state.get("bets"));
        for (Object raw : bets.values()) {
            Map<String, Object> playerBets = mapOf(raw);
            for (String type : List.of("final_winner", "final_loser")) {
                Map<String, Object> card = mapOf(playerBets.get(type));
                if (!card.isEmpty()) {
                    removeBetCard(market, Objects.toString(card.get("id"), ""));
                }
            }
        }
        return market;
    }

    private Map<String, Object> emptyBetMarket() {
        Map<String, Object> market = new LinkedHashMap<>();
        market.put("legWinner", new LinkedHashMap<>());
        market.put("legMiddle", new LinkedHashMap<>());
        market.put("finalWinner", new ArrayList<>());
        market.put("finalLoser", new ArrayList<>());
        return market;
    }

    private void addBetCard(Map<String, Object> market, Map<String, Object> card) {
        String type = Objects.toString(card.get("type"), "");
        String color = Objects.toString(card.get("color"), "");
        switch (type) {
            case "leg_winner" -> listAt(mapAt(market, "legWinner"), color).add(new LinkedHashMap<>(card));
            case "leg_middle" -> listAt(mapAt(market, "legMiddle"), color).add(new LinkedHashMap<>(card));
            case "final_winner" -> listAt(market, "finalWinner").add(new LinkedHashMap<>(card));
            case "final_loser" -> listAt(market, "finalLoser").add(new LinkedHashMap<>(card));
            default -> {
            }
        }
    }

    private Map<String, Object> removeBetCard(Map<String, Object> market, String cardId) {
        for (String group : List.of("finalWinner", "finalLoser")) {
            List<Map<String, Object>> cards = listAt(market, group);
            for (int index = 0; index < cards.size(); index++) {
                if (cardId.equals(cards.get(index).get("id"))) {
                    return cards.remove(index);
                }
            }
        }
        for (String group : List.of("legWinner", "legMiddle")) {
            Map<String, Object> byColor = mapAt(market, group);
            for (String color : RACING_COLORS) {
                List<Map<String, Object>> cards = listAt(byColor, color);
                if (!cards.isEmpty() && cardId.equals(cards.get(0).get("id"))) {
                    return cards.remove(0);
                }
                for (int index = 0; index < cards.size(); index++) {
                    if ("leg_middle".equals(cards.get(index).get("type")) && cardId.equals(cards.get(index).get("id"))) {
                        return cards.remove(index);
                    }
                }
            }
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> initialStacks() {
        Map<String, Object> stacks = new LinkedHashMap<>();
        stacks.put("0", new ArrayList<>(RACING_COLORS));
        return stacks;
    }

    private void putCamelAtBottom(Map<String, Object> state, String color, int position) {
        Map<String, Object> stacks = stacksOf(state);
        for (String key : new ArrayList<>(stacks.keySet())) {
            List<String> stack = stringList(stacks.get(key));
            stack.remove(color);
            if (stack.isEmpty()) {
                stacks.remove(key);
            } else {
                stacks.put(key, stack);
            }
        }
        List<String> target = stringList(stacks.get(String.valueOf(position)));
        target.add(0, color);
        stacks.put(String.valueOf(position), target);
        state.put("stacks", stacks);
    }

    private void placeMovingStack(Map<String, Object> stacks, int target, List<String> moving, boolean bottom) {
        List<String> targetStack = stringList(stacks.get(String.valueOf(target)));
        if (bottom) {
            List<String> combined = new ArrayList<>(moving);
            combined.addAll(targetStack);
            targetStack = combined;
        } else {
            targetStack.addAll(moving);
        }
        stacks.put(String.valueOf(target), targetStack);
    }

    private void putStack(Map<String, Object> stacks, int position, List<String> stack) {
        if (stack.isEmpty()) {
            stacks.remove(String.valueOf(position));
        } else {
            stacks.put(String.valueOf(position), stack);
        }
    }

    private List<String> stackAt(Map<String, Object> state, int position) {
        return stringList(stacksOf(state).get(String.valueOf(position)));
    }

    private CamelLocation findCamel(Map<String, Object> stacks, String color) {
        for (Map.Entry<String, Object> entry : stacks.entrySet()) {
            List<String> stack = stringList(entry.getValue());
            for (int index = 0; index < stack.size(); index++) {
                if (color.equals(stack.get(index))) {
                    return new CamelLocation(Integer.parseInt(entry.getKey()), index);
                }
            }
        }
        return null;
    }

    private List<Map<String, Object>> allRankings(Map<String, Object> state) {
        Map<String, Object> stacks = stacksOf(state);
        List<Map<String, Object>> rankings = new ArrayList<>();
        for (String color : ALL_COLORS) {
            CamelLocation location = findCamel(stacks, color);
            if (location == null) {
                continue;
            }
            Map<String, Object> rank = new LinkedHashMap<>();
            rank.put("color", color);
            rank.put("name", colorName(color));
            rank.put("position", location.position());
            rank.put("height", location.index());
            rankings.add(rank);
        }
        rankings.sort(Comparator
                .comparingInt((Map<String, Object> rank) -> parseInteger(rank.get("position"), 0)).reversed()
                .thenComparing(Comparator.comparingInt((Map<String, Object> rank) -> parseInteger(rank.get("height"), 0)).reversed()));
        for (int index = 0; index < rankings.size(); index++) {
            rankings.get(index).put("rank", index + 1);
        }
        return rankings;
    }

    private List<Map<String, Object>> racingRankings(Map<String, Object> state) {
        List<Map<String, Object>> rankings = allRankings(state).stream()
                .filter(rank -> RACING_COLORS.contains(String.valueOf(rank.get("color"))))
                .toList();
        List<Map<String, Object>> copied = new ArrayList<>();
        for (int index = 0; index < rankings.size(); index++) {
            Map<String, Object> rank = new LinkedHashMap<>(rankings.get(index));
            rank.put("rank", index + 1);
            copied.add(rank);
        }
        return copied;
    }

    private Map<String, Integer> rankByColor(List<Map<String, Object>> rankings) {
        Map<String, Integer> byColor = new LinkedHashMap<>();
        for (int index = 0; index < rankings.size(); index++) {
            byColor.put(String.valueOf(rankings.get(index).get("color")), index + 1);
        }
        return byColor;
    }

    private List<Map<String, Object>> sandstormPairs(Map<String, Object> state) {
        List<Map<String, Object>> rankings = allRankings(state);
        List<Integer> positions = rankings.stream().map(rank -> parseInteger(rank.get("position"), 0)).distinct().toList();
        List<Map<String, Object>> racing = racingRankings(state);
        List<Map<String, Object>> pairs = new ArrayList<>();
        if (racing.isEmpty()) {
            return pairs;
        }
        int leaderPosition = parseInteger(racing.getFirst().get("position"), 0);
        int lastPosition = parseInteger(racing.getLast().get("position"), 0);
        for (int odd = lastPosition + 1; odd < leaderPosition; odd++) {
            if (odd % 2 != 1) {
                continue;
            }
            if (!positions.contains(odd) && !positions.contains(odd + 1)) {
                pairs.add(new LinkedHashMap<>(Map.of("from", odd, "to", odd + 1)));
            }
        }
        return pairs;
    }

    private boolean isSandstormPosition(Map<String, Object> state, int position) {
        return listOf(state.get("sandstormPairs")).stream()
                .anyMatch(pair -> parseInteger(pair.get("to"), -1) == position);
    }

    private boolean hasTrackTokenAt(Map<String, Object> trackTokens, int position) {
        for (String tokenType : List.of("shortcut", "fennec")) {
            Map<String, Object> token = mapOf(trackTokens.get(tokenType));
            if ("placed".equals(token.get("status")) && parseInteger(token.get("position"), -1) == position) {
                return true;
            }
        }
        return false;
    }

    private void addTemporaryDiscard(Map<String, Object> state, Map<String, Object> card) {
        List<Map<String, Object>> discard = listOf(state.get("temporaryDiscard"));
        discard.add(card);
        state.put("temporaryDiscard", discard);
    }

    private List<Map<String, Object>> normalRaceCards() {
        return new ArrayList<>(loadJsonArray("race_cards.json").stream()
                .filter(card -> !"black".equals(card.get("color")))
                .map(card -> (Map<String, Object>) new LinkedHashMap<>(card))
                .toList());
    }

    private List<Map<String, Object>> crazyRaceCards() {
        return new ArrayList<>(loadJsonArray("race_cards.json").stream()
                .filter(card -> "black".equals(card.get("color")))
                .map(card -> (Map<String, Object>) new LinkedHashMap<>(card))
                .toList());
    }

    private List<Map<String, Object>> loadJsonArray(String fileName) {
        Path path = Path.of("src", "main", "java", "com", "fortell", "boardgame", "game_modules", "camel_up_cards", fileName);
        if (!Files.exists(path)) {
            path = Path.of("backend", "src", "main", "java", "com", "fortell", "boardgame", "game_modules", "camel_up_cards", fileName);
        }
        try {
            return objectMapper.readValue(Files.readString(path, StandardCharsets.UTF_8), new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "读取狂野骆驼数据失败");
        }
    }

    private Map<String, Object> initialMoney(List<Map<String, Object>> players) {
        Map<String, Object> money = new LinkedHashMap<>();
        for (Map<String, Object> player : players) {
            money.put(String.valueOf(player.get("userId")), 3);
        }
        return money;
    }

    private Map<String, Object> emptyPlayerBets(List<Map<String, Object>> players) {
        Map<String, Object> bets = new LinkedHashMap<>();
        for (Map<String, Object> player : players) {
            bets.put(String.valueOf(player.get("userId")), new LinkedHashMap<>(Map.of(
                    "leg", new ArrayList<>(),
                    "final_winner", new LinkedHashMap<>(),
                    "final_loser", new LinkedHashMap<>()
            )));
        }
        return bets;
    }

    private Map<String, Object> initialPlayerTokens(List<Map<String, Object>> players, Map<String, Object> config) {
        boolean shortcut = hasExpansion(config, "shortcut");
        boolean fennec = hasExpansion(config, "fennec");
        Map<String, Object> tokens = new LinkedHashMap<>();
        for (Map<String, Object> player : players) {
            tokens.put(String.valueOf(player.get("userId")), new LinkedHashMap<>(Map.of(
                    "shortcut", shortcut ? "available" : "disabled",
                    "fennec", fennec ? "available" : "disabled"
            )));
        }
        return tokens;
    }

    private Map<String, Object> initialTrackTokens(Map<String, Object> config) {
        return new LinkedHashMap<>(Map.of(
                "shortcut", trackToken(hasExpansion(config, "shortcut")),
                "fennec", trackToken(hasExpansion(config, "fennec"))
        ));
    }

    private Map<String, Object> trackToken(boolean enabled) {
        Map<String, Object> token = new LinkedHashMap<>();
        token.put("enabled", enabled);
        token.put("status", enabled ? "unplaced" : "disabled");
        token.put("position", null);
        token.put("ownerId", null);
        return token;
    }

    private boolean hasExpansion(Map<String, Object> config, String expansion) {
        String value = Objects.toString(config.get("expansion"), "shortcut_fennec");
        return "shortcut".equals(expansion)
                ? List.of("shortcut", "shortcut_fennec").contains(value)
                : List.of("fennec", "shortcut_fennec").contains(value);
    }

    private Map<String, Object> newTurn() {
        Map<String, Object> turn = new LinkedHashMap<>();
        turn.put("trackActionDone", false);
        turn.put("betActionDone", false);
        return turn;
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

    private void ensurePlaying(Map<String, Object> state) {
        if (!"PLAYING".equals(state.get("phase"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前游戏已结束");
        }
    }

    private void ensureCurrentPlayer(Map<String, Object> state, UserSummary actor) {
        if (!Objects.equals(asLong(state.get("currentPlayerId")), actor.id())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "还没轮到你行动");
        }
    }

    private void ensureTrackActionAvailable(Map<String, Object> state) {
        if (Boolean.TRUE.equals(mapOf(state.get("turn")).get("trackActionDone"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "本回合已经执行过赛道行动");
        }
    }

    private void ensureBetActionAvailable(Map<String, Object> state) {
        if (Boolean.TRUE.equals(mapOf(state.get("turn")).get("betActionDone"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "本回合已经执行过下注行动");
        }
    }

    private String colorName(String color) {
        return COLOR_NAMES.getOrDefault(color, color);
    }

    private String cardText(Map<String, Object> card) {
        return colorName(Objects.toString(card.get("color"), "")) + "骆驼 " + card.get("steps");
    }

    private String tokenName(String tokenType) {
        return "shortcut".equals(tokenType) ? "捷径" : "耳廓狐";
    }

    private String betCardName(Map<String, Object> card) {
        String type = Objects.toString(card.get("type"), "");
        if ("leg_middle".equals(type)) return "赛段下注中间位";
        if ("final_winner".equals(type)) return "最终下注大赢家";
        if ("final_loser".equals(type)) return "最终下注大输家";
        int best = integerList(card.get("payouts")).stream().mapToInt(Integer::intValue).max().orElse(0);
        return "最高收益为" + best + "的赛段下注赢家";
    }

    private String playerName(List<Map<String, Object>> players, long playerId) {
        return players.stream()
                .filter(player -> asLong(player.get("userId")) == playerId)
                .map(player -> String.valueOf(player.get("username")))
                .findFirst()
                .orElse("玩家" + playerId);
    }

    private int indexOfPlayer(List<Map<String, Object>> players, long playerId) {
        for (int index = 0; index < players.size(); index++) {
            if (asLong(players.get(index).get("userId")) == playerId) {
                return index;
            }
        }
        return 0;
    }

    private long nextPlayerId(Map<String, Object> state) {
        List<Map<String, Object>> players = playerListOf(state);
        long current = asLong(state.get("currentPlayerId"));
        int index = indexOfPlayer(players, current);
        return asLong(players.get((index + 1) % players.size()).get("userId"));
    }

    private List<Map<String, Object>> playerListOf(Map<String, Object> state) {
        return listOf(state.get("players"));
    }

    private Map<String, Object> stacksOf(Map<String, Object> state) {
        return mapOf(state.get("stacks"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOf(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> mapAt(Map<String, Object> map, String key) {
        Map<String, Object> value = mapOf(map.get(key));
        map.put(key, value);
        return value;
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

    private List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return new ArrayList<>();
        }
        return list.stream().map(String::valueOf).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    private List<Integer> integerList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(this::parseIntegerValue).toList();
    }

    private List<Map<String, Object>> drawMany(List<Map<String, Object>> cards, int count) {
        List<Map<String, Object>> drawn = new ArrayList<>();
        for (int index = 0; index < count && !cards.isEmpty(); index++) {
            drawn.add(cards.remove(0));
        }
        return drawn;
    }

    private List<Map<String, Object>> removeCardsByIds(List<Map<String, Object>> cards, List<String> cardIds) {
        List<Map<String, Object>> picked = new ArrayList<>();
        for (String cardId : cardIds) {
            for (int index = 0; index < cards.size(); index++) {
                if (cardId.equals(String.valueOf(cards.get(index).get("id")))) {
                    picked.add(cards.remove(index));
                    break;
                }
            }
        }
        return picked;
    }

    private boolean allSetupDone(Map<String, Object> selections) {
        for (Object rawSelection : selections.values()) {
            if (!"done".equals(mapOf(rawSelection).get("status"))) {
                return false;
            }
        }
        return true;
    }

    private void shuffle(List<Map<String, Object>> cards) {
        for (int index = cards.size() - 1; index > 0; index--) {
            int swap = random.nextInt(index + 1);
            Map<String, Object> value = cards.get(index);
            cards.set(index, cards.get(swap));
            cards.set(swap, value);
        }
    }

    private int parseInteger(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return parseIntegerValue(value);
    }

    private int parseIntegerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private void appendNotice(Map<String, Object> state, String notice) {
        List<String> notices = new ArrayList<>((List<String>) state.getOrDefault("notices", List.of()));
        notices.add(0, notice);
        state.put("notices", notices.stream().limit(50).toList());
    }

    private record CamelLocation(int position, int index) {
    }

    private record MoveResult(int position) {
    }
}
