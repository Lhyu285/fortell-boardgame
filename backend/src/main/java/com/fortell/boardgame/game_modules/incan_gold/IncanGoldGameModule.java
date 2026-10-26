package com.fortell.boardgame.game_modules.incan_gold;

import com.fortell.boardgame.game_modules.VersionedGameModule;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class IncanGoldGameModule implements VersionedGameModule {
    private static final List<Integer> TREASURE_VALUES = List.of(1, 2, 3, 4, 5, 5, 7, 7, 9, 11, 11, 13, 14, 15, 17);
    private static final List<String> DISASTERS = List.of("collapse", "trap", "serpent", "idol", "swarm");
    private static final Map<String, String> DISASTER_NAMES = Map.of(
            "collapse", "塌方",
            "trap", "陷阱",
            "serpent", "妖蛇",
            "idol", "傀儡",
            "swarm", "虫群"
    );
    private static final List<Integer> ARTIFACT_VALUES = List.of(5, 7, 8, 10, 12);
    private static final List<String> EVENT_IDS = List.of(
            "chupacabra", "double_challenge", "endless_wealth", "triple_challenge", "farewell"
    );

    private final SecureRandom random = new SecureRandom();

    @Override
    public GameType gameType() {
        return GameType.INCAN_GOLD;
    }

    @Override
    public GameDescriptor descriptor() {
        return new GameDescriptor(
                "incan_gold",
                "印加宝藏",
                "Incan Gold",
                "/incan_gold",
                "/incan_gold/rule",
                2,
                8,
                "6~8",
                "五轮探险、宝藏分配与可选扩展"
        );
    }

    @Override
    public Map<String, Object> defaultConfig() {
        return new LinkedHashMap<>(Map.of("thievesExpansion", false, "eventsExpansion", false));
    }

    @Override
    public Map<String, Object> initialState(RoomEntity room, List<RoomSeat> seats) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("phase", "waiting");
        state.put("round", 0);
        state.put("players", new ArrayList<>());
        state.put("deck", new ArrayList<>());
        state.put("currentCards", new ArrayList<>());
        state.put("roadTreasure", 0);
        state.put("roadArtifacts", new ArrayList<>());
        state.put("disasterSeen", initialDisasterMap(0));
        state.put("disasterRemaining", initialDisasterMap(3));
        state.put("activePlayers", new ArrayList<>());
        state.put("pendingDecisions", new LinkedHashMap<>());
        state.put("pendingTheftTargets", new LinkedHashMap<>());
        state.put("thieves", new ArrayList<>());
        state.put("targetCandidates", new ArrayList<>());
        state.put("lastRevealedCard", null);
        state.put("lastDecision", new LinkedHashMap<>());
        state.put("lastSettlement", new LinkedHashMap<>());
        state.put("lastDisaster", new LinkedHashMap<>());
        state.put("eventsUsed", new ArrayList<>());
        state.put("currentEvent", null);
        state.put("eventsExpansion", false);
        state.put("rankings", new ArrayList<>());
        state.put("winners", new ArrayList<>());
        state.put("notices", new ArrayList<>());
        state.put("version", 0L);
        state.put("actionLog", new ArrayList<>());
        return state;
    }

    @Override
    public Map<String, Object> sanitizeConfig(Map<String, Object> requested, RoomEntity room, List<RoomSeat> seats) {
        Map<String, Object> merged = new LinkedHashMap<>(defaultConfig());
        if (requested != null) {
            merged.putAll(requested);
        }
        Object value = merged.get("thievesExpansion");
        if (!(value instanceof Boolean)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "盗贼扩配置必须是布尔值");
        }
        merged.put("thievesExpansion", value);
        Object eventsValue = merged.get("eventsExpansion");
        if (!(eventsValue instanceof Boolean)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "事件扩配置必须是布尔值");
        }
        merged.put("eventsExpansion", eventsValue);
        return merged;
    }

    @Override
    public void validateCanStart(RoomEntity room, List<RoomSeat> seats) {
        int count = playersOf(seats).size();
        if (count < gameType().minPlayers()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "至少需要2名玩家");
        }
        if (count > gameType().maxPlayers()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "最多支持8名玩家");
        }
    }

    @Override
    public Map<String, Object> onStart(RoomEntity room, List<RoomSeat> seats, Map<String, Object> config) {
        List<Map<String, Object>> players = playersOf(seats);
        Map<String, Object> state = initialState(room, seats);
        state.put("phase", "EXPLORATION");
        state.put("round", 1);
        state.put("players", playerStates(players));
        state.put("deck", initialDeck());
        state.put("activePlayers", playerIds(players));
        state.put("thievesExpansion", Boolean.TRUE.equals(config.get("thievesExpansion")));
        state.put("eventsExpansion", Boolean.TRUE.equals(config.get("eventsExpansion")));
        if (Boolean.TRUE.equals(config.get("eventsExpansion"))) {
            drawEventForRound(state);
        }
        appendNotice(state, "印加宝藏开始，第1轮探险即将开始。");
        return state;
    }

    @Override
    public Map<String, Object> onAction(RoomEntity room, List<RoomSeat> seats, Map<String, Object> config,
                                        Map<String, Object> state, UserSummary actor, String actionType,
                                        Map<String, Object> payload, List<String> notices) {
        boolean expansion = Boolean.TRUE.equals(config.get("thievesExpansion"));
        return switch (actionType) {
            case "draw_exploration_card" -> drawExplorationCard(state, actor);
            case "choose_expedition_action" -> chooseExpeditionAction(state, actor, payload, expansion);
            case "submit_theft_target" -> submitTheftTarget(state, actor, payload, expansion);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的印加宝藏动作");
        };
    }

    @Override
    public Map<String, Object> stateForViewer(Map<String, Object> state, UserSummary viewer) {
        Map<String, Object> visible = new LinkedHashMap<>(state);
        visible.put("pendingDecisions", submittedMask(mapOf(state.get("pendingDecisions"))));
        visible.put("pendingTheftTargets", submittedMask(mapOf(state.get("pendingTheftTargets"))));
        return visible;
    }

    @Override
    public List<String> rules() {
        return List.of(
                "游戏共五轮；每轮加入一张神器卡并洗混探险牌堆。",
                "翻出宝藏时，当前仍在探险的玩家平均分配绿松石，余数留在道路上。",
                "同类灾难第二次出现时，仍在探险玩家本轮收益清零，并永久移除一张该灾难牌。",
                "唯一返回者可以获得道路上的全部神器。",
                "启用盗贼扩时，决策阶段增加每轮限一次的盗窃选项。",
                "启用事件扩时，每轮随机公开一张不重复的事件牌，事件持续到本轮结束。",
                "事件牌包括卓柏卡布拉、双重挑战、无尽财富、三重挑战和后会有期。"
        );
    }

    private Map<String, Object> drawExplorationCard(Map<String, Object> state, UserSummary actor) {
        ensurePhase(state, "EXPLORATION");
        ensureActive(state, actor.id());
        if (!mapOf(state.get("lastDisaster")).isEmpty()) {
            state.put("lastDisaster", new LinkedHashMap<>());
        }
        drawExplorationSequence(state);
        return state;
    }

    private void drawExplorationSequence(Map<String, Object> state) {
        int drawCount = eventIs(state, "double_challenge") ? 2 : 1;
        for (int index = 0; index < drawCount; index++) {
            boolean disasterEnded = revealExplorationCard(state, index == drawCount - 1);
            if (disasterEnded) {
                break;
            }
        }
    }

    private void continueExplorationAutomatically(Map<String, Object> state) {
        if (!"EXPLORATION".equals(state.get("phase")) || activeIds(state).isEmpty()) {
            return;
        }
        if (cardList(state.get("deck")).isEmpty()) {
            return;
        }
        drawExplorationSequence(state);
    }

    private boolean revealExplorationCard(Map<String, Object> state, boolean beginDecisionAfter) {
        List<Map<String, Object>> deck = cardList(state.get("deck"));
        if (deck.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "探险牌堆已为空");
        }

        Map<String, Object> card = deck.remove(0);
        state.put("deck", deck);
        List<Map<String, Object>> currentCards = cardList(state.get("currentCards"));
        currentCards.add(card);
        state.put("currentCards", currentCards);
        state.put("lastRevealedCard", card);

        String type = Objects.toString(card.get("type"), "");
        if ("treasure".equals(type)) {
            int value = integer(card.get("value"), 0);
            if (eventIs(state, "farewell")) {
                state.put("roadTreasure", integer(state.get("roadTreasure"), 0) + value);
                appendNotice(state, "翻出价值" + value + "的宝藏，绿松石留在道路上。");
            } else {
                distributeTreasure(state, value);
                appendNotice(state, "翻出价值" + value + "的宝藏，已按当前探险人数分配。");
            }
            if (beginDecisionAfter) {
                beginDecision(state);
            }
        } else if ("artifact".equals(type)) {
            List<Map<String, Object>> roadArtifacts = cardList(state.get("roadArtifacts"));
            roadArtifacts.add(card);
            state.put("roadArtifacts", roadArtifacts);
            appendNotice(state, "翻出价值" + card.get("value") + "的神器，神器留在道路上。");
            if (beginDecisionAfter) {
                beginDecision(state);
            }
        } else if ("disaster".equals(type)) {
            String disaster = Objects.toString(card.get("disaster"), "");
            Map<String, Object> seen = mapOf(state.get("disasterSeen"));
            int count = integer(seen.get(disaster), 0) + 1;
            seen.put(disaster, count);
            state.put("disasterSeen", seen);
            appendNotice(state, "遇到灾难：" + DISASTER_NAMES.getOrDefault(disaster, disaster) + "（本轮第" + count + "次）。");
            if (count == 1 && eventIs(state, "endless_wealth")) {
                state.put("roadTreasure", integer(state.get("roadTreasure"), 0) + 5);
                appendNotice(state, "无尽财富生效，道路上增加5颗绿松石。");
            }
            int disasterThreshold = eventIs(state, "triple_challenge") ? 3 : 2;
            if (count >= disasterThreshold) {
                resolveDisaster(state, disaster);
                return true;
            } else if (beginDecisionAfter) {
                beginDecision(state);
            }
        }
        return false;
    }

    private Map<String, Object> chooseExpeditionAction(Map<String, Object> state, UserSummary actor,
                                                       Map<String, Object> payload, boolean expansion) {
        ensurePhase(state, "DECISION");
        ensureActive(state, actor.id());
        String choice = Objects.toString(payload.get("choice"), "");
        if (!List.of("continue", "return", "steal").contains(choice)
                || (!expansion && "steal".equals(choice))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前决策无效");
        }

        Map<String, Object> players = playerMap(state);
        Map<String, Object> actorState = mapOf(players.get(String.valueOf(actor.id())));
        if ("steal".equals(choice) && Boolean.TRUE.equals(actorState.get("stealUsed"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "本轮已经使用过盗窃");
        }
        Map<String, Object> pending = mapOf(state.get("pendingDecisions"));
        String actorKey = String.valueOf(actor.id());
        if (pending.containsKey(actorKey)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "你已经完成本次决策");
        }
        if ("steal".equals(choice)) {
            actorState.put("stealUsed", true);
            players.put(actorKey, actorState);
            state.put("players", new ArrayList<>(players.values()));
        }
        pending.put(actorKey, choice);
        state.put("pendingDecisions", pending);
        appendNotice(state, "玩家" + actor.username() + "已完成决策。");

        if (allActiveSubmitted(state, pending)) {
            List<Long> thieves = pending.entrySet().stream()
                    .filter(entry -> "steal".equals(entry.getValue()))
                    .map(entry -> Long.valueOf(entry.getKey()))
                    .toList();
            if (expansion && !thieves.isEmpty()) {
                List<Long> candidates = activeIds(state).stream()
                        .filter(id -> !thieves.contains(id))
                        .toList();
                state.put("thieves", thieves);
                state.put("targetCandidates", candidates);
                state.put("phase", "THEFT_TARGETS");
                if (candidates.isEmpty()) {
                    resolveDecision(state, expansion);
                }
            } else {
                resolveDecision(state, expansion);
            }
        }
        return state;
    }

    private Map<String, Object> submitTheftTarget(Map<String, Object> state, UserSummary actor,
                                                   Map<String, Object> payload, boolean expansion) {
        ensurePhase(state, "THEFT_TARGETS");
        if (!expansion || !longList(state.get("thieves")).contains(actor.id())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "你当前不能选择盗窃目标");
        }
        long targetId = longValue(payload.get("targetUserId"), -1L);
        if (!longList(state.get("targetCandidates")).contains(targetId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "盗窃目标必须是当前未返回且未盗窃的玩家");
        }
        Map<String, Object> targets = mapOf(state.get("pendingTheftTargets"));
        String actorKey = String.valueOf(actor.id());
        if (targets.containsKey(actorKey)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "你已经提交了盗窃目标");
        }
        targets.put(actorKey, targetId);
        state.put("pendingTheftTargets", targets);
        if (targets.keySet().containsAll(longList(state.get("thieves")).stream().map(String::valueOf).toList())) {
            resolveDecision(state, true);
        }
        return state;
    }

    private void resolveDecision(Map<String, Object> state, boolean expansion) {
        Map<String, Object> decisions = mapOf(state.get("pendingDecisions"));
        Map<String, Object> targets = mapOf(state.get("pendingTheftTargets"));
        List<Long> thieves = longList(state.get("thieves"));
        Map<Long, List<Long>> targeters = new LinkedHashMap<>();
        for (Long thief : thieves) {
            Object target = targets.get(String.valueOf(thief));
            if (target != null) {
                long targetId = longValue(target, -1L);
                targeters.computeIfAbsent(targetId, ignored -> new ArrayList<>()).add(thief);
            }
        }

        List<String> resolution = new ArrayList<>();
        Map<String, Object> players = playerMap(state);
        for (Map.Entry<Long, List<Long>> entry : targeters.entrySet()) {
            Map<String, Object> victim = mapOf(players.get(String.valueOf(entry.getKey())));
            if (!"return".equals(decisions.get(String.valueOf(entry.getKey())))) {
                continue;
            }
            int stolen = Math.min(3, integer(victim.get("roundTreasure"), 0));
            int share = entry.getValue().isEmpty() ? 0 : stolen / entry.getValue().size();
            int remainder = stolen - share * entry.getValue().size();
            victim.put("roundTreasure", integer(victim.get("roundTreasure"), 0) - stolen + remainder);
            for (Long thiefId : entry.getValue()) {
                Map<String, Object> thief = mapOf(players.get(String.valueOf(thiefId)));
                thief.put("roundTreasure", integer(thief.get("roundTreasure"), 0) + share);
                players.put(String.valueOf(thiefId), thief);
            }
            players.put(String.valueOf(entry.getKey()), victim);
            resolution.add(playerName(players, entry.getKey()) + "被盗取" + stolen + "颗绿松石。");
        }
        state.put("players", new ArrayList<>(players.values()));

        List<Long> returning = decisions.entrySet().stream()
                .filter(entry -> "return".equals(entry.getValue()))
                .map(entry -> Long.valueOf(entry.getKey()))
                .toList();
        Set<Long> attacked = new LinkedHashSet<>();
        if (eventIs(state, "chupacabra") && returning.size() == 1) {
            attacked.add(returning.getFirst());
            announceAttack(state, returning.getFirst(), "独自返回营地");
        }
        settleReturns(state, returning, attacked);
        if (eventIs(state, "chupacabra") && activeIds(state).size() == 1) {
            Long lastExplorer = activeIds(state).getFirst();
            attacked.add(lastExplorer);
            attackPlayer(state, lastExplorer, "成为神庙中最后一名探险者");
        }
        for (Map.Entry<String, Object> entry : decisions.entrySet()) {
            resolution.add(playerName(players, Long.parseLong(entry.getKey())) + "：" + decisionName(String.valueOf(entry.getValue())));
        }
        state.put("lastDecision", new LinkedHashMap<>(Map.of(
                "choices", new LinkedHashMap<>(decisions),
                "thieves", new ArrayList<>(thieves),
                "targets", new LinkedHashMap<>(targets),
                "resolution", resolution
        )));
        state.put("pendingDecisions", new LinkedHashMap<>());
        state.put("pendingTheftTargets", new LinkedHashMap<>());
        state.put("thieves", new ArrayList<>());
        state.put("targetCandidates", new ArrayList<>());

        if (activeIds(state).isEmpty()) {
            finishRound(state, null);
        } else {
            state.put("phase", "EXPLORATION");
            appendNotice(state, "决策完成，仍在探险的玩家继续前进。");
            continueExplorationAutomatically(state);
        }
    }

    private void settleReturns(Map<String, Object> state, List<Long> returning, Set<Long> attacked) {
        if (returning.isEmpty()) {
            return;
        }
        Map<String, Object> players = playerMap(state);
        int roadTreasure = integer(state.get("roadTreasure"), 0);
        List<Long> eligible = returning.stream().filter(id -> !attacked.contains(id)).toList();
        int share = eligible.isEmpty() ? 0 : roadTreasure / eligible.size();
        int remainder = eligible.isEmpty() ? roadTreasure : roadTreasure % eligible.size();
        List<Map<String, Object>> settlementPlayers = new ArrayList<>();
        for (Long id : returning) {
            Map<String, Object> player = mapOf(players.get(String.valueOf(id)));
            int carriedTreasure = integer(player.get("roundTreasure"), 0);
            Map<String, Object> settlementPlayer = new LinkedHashMap<>();
            settlementPlayer.put("userId", id);
            settlementPlayer.put("carriedTreasure", carriedTreasure);
            settlementPlayer.put("roadTreasure", attacked.contains(id) ? 0 : share);
            settlementPlayer.put("attacked", attacked.contains(id));
            if (attacked.contains(id)) {
                player.put("roundTreasure", 0);
                settlementPlayer.put("lostTreasure", carriedTreasure);
            } else {
                player.put("totalTreasure", integer(player.get("totalTreasure"), 0) + carriedTreasure + share);
                player.put("roundTreasure", 0);
            }
            player.put("active", false);
            players.put(String.valueOf(id), player);
            settlementPlayers.add(settlementPlayer);
        }
        state.put("players", new ArrayList<>(players.values()));
        state.put("roadTreasure", remainder);
        List<Map<String, Object>> claimedArtifacts = new ArrayList<>();
        if (returning.size() == 1 && eligible.size() == 1) {
            Map<String, Object> player = mapOf(players.get(String.valueOf(returning.getFirst())));
            List<Map<String, Object>> artifacts = cardList(state.get("roadArtifacts"));
            List<Map<String, Object>> owned = cardList(player.get("artifacts"));
            owned.addAll(artifacts);
            claimedArtifacts.addAll(artifacts);
            player.put("artifacts", owned);
            players.put(String.valueOf(returning.getFirst()), player);
            state.put("players", new ArrayList<>(players.values()));
            state.put("roadArtifacts", new ArrayList<>());
            removeClaimedArtifacts(state, artifacts);
        }
        for (Map<String, Object> settlementPlayer : settlementPlayers) {
            settlementPlayer.put("artifacts", claimedArtifacts);
        }
        state.put("lastSettlement", new LinkedHashMap<>(Map.of(
                "players", settlementPlayers,
                "roadTreasureBefore", roadTreasure,
                "roadTreasurePerPlayer", share,
                "roadTreasureRemaining", remainder,
                "artifacts", claimedArtifacts
        )));
        state.put("activePlayers", activeIds(state));
    }

    private void attackPlayer(Map<String, Object> state, long playerId, String reason) {
        Map<String, Object> players = playerMap(state);
        Map<String, Object> player = mapOf(players.get(String.valueOf(playerId)));
        player.put("roundTreasure", 0);
        player.put("active", false);
        players.put(String.valueOf(playerId), player);
        state.put("players", new ArrayList<>(players.values()));
        state.put("activePlayers", activeIds(state));
        announceAttack(state, playerId, reason);
    }

    private void announceAttack(Map<String, Object> state, long playerId, String reason) {
        appendNotice(state, "卓柏卡布拉袭击了" + playerName(playerMap(state), playerId)
                + "（" + reason + "），其本轮收益全部失去，无法获得神器或道路上的绿松石。");
    }

    private void resolveDisaster(Map<String, Object> state, String disaster) {
        Map<String, Object> players = playerMap(state);
        List<Map<String, Object>> affectedPlayers = new ArrayList<>();
        for (Long id : activeIds(state)) {
            Map<String, Object> player = mapOf(players.get(String.valueOf(id)));
            affectedPlayers.add(new LinkedHashMap<>(Map.of(
                    "userId", id,
                    "treasure", integer(player.get("roundTreasure"), 0)
            )));
            player.put("roundTreasure", 0);
            player.put("active", false);
            players.put(String.valueOf(id), player);
        }
        state.put("players", new ArrayList<>(players.values()));
        state.put("roadTreasure", 0);
        state.put("roadArtifacts", new ArrayList<>());
        state.put("activePlayers", new ArrayList<>());
        state.put("phase", "DISASTER");
        int threshold = eventIs(state, "triple_challenge") ? 3 : 2;
        state.put("lastDisaster", new LinkedHashMap<>(Map.of(
                "disaster", disaster,
                "threshold", threshold,
                "players", affectedPlayers
        )));
        appendNotice(state, "" + DISASTER_NAMES.getOrDefault(disaster, disaster) + threshold + "次出现，本轮仍在探险玩家收益清零。");
        finishRound(state, disaster);
    }

    private void finishRound(Map<String, Object> state, String removedDisaster) {
        List<Map<String, Object>> cards = cardList(state.get("deck"));
        cards.addAll(cardList(state.get("currentCards")));
        if (removedDisaster != null) {
            for (int index = 0; index < cards.size(); index++) {
                Map<String, Object> card = cards.get(index);
                if ("disaster".equals(card.get("type")) && removedDisaster.equals(card.get("disaster"))) {
                    cards.remove(index);
                    break;
                }
            }
            Map<String, Object> remaining = mapOf(state.get("disasterRemaining"));
            remaining.put(removedDisaster, Math.max(0, integer(remaining.get(removedDisaster), 0) - 1));
            state.put("disasterRemaining", remaining);
        }
        state.put("currentCards", new ArrayList<>());
        state.put("roadTreasure", 0);
        state.put("roadArtifacts", new ArrayList<>());
        state.put("disasterSeen", initialDisasterMap(0));
        state.put("lastRevealedCard", null);
        state.put("lastDecision", new LinkedHashMap<>());
        state.put("pendingDecisions", new LinkedHashMap<>());
        state.put("pendingTheftTargets", new LinkedHashMap<>());
        state.put("thieves", new ArrayList<>());
        state.put("targetCandidates", new ArrayList<>());

        int round = integer(state.get("round"), 1);
        if (round >= 5) {
            state.put("phase", "FINISHED");
            state.put("deck", cards);
            state.put("currentEvent", null);
            calculateRankings(state);
            appendNotice(state, "五轮探险结束，游戏完成。");
            return;
        }

        int nextRound = round + 1;
        cards.add(artifactCard(ARTIFACT_VALUES.get(nextRound - 1), nextRound));
        shuffle(cards);
        state.put("deck", cards);
        state.put("round", nextRound);
        Map<String, Object> players = playerMap(state);
        for (Map.Entry<String, Object> entry : players.entrySet()) {
            Map<String, Object> player = mapOf(entry.getValue());
            player.put("active", true);
            player.put("roundTreasure", 0);
            player.put("stealUsed", false);
            players.put(entry.getKey(), player);
        }
        state.put("players", new ArrayList<>(players.values()));
        state.put("activePlayers", new ArrayList<>(players.keySet()).stream().map(Long::valueOf).toList());
        state.put("phase", "EXPLORATION");
        if (Boolean.TRUE.equals(state.get("eventsExpansion"))) {
            drawEventForRound(state);
        }
        appendNotice(state, "第" + nextRound + "轮开始，新的神器已加入牌堆。");
    }

    private void calculateRankings(Map<String, Object> state) {
        List<Map<String, Object>> players = playerList(state);
        players.sort(Comparator
                .comparingInt((Map<String, Object> player) -> integer(player.get("totalTreasure"), 0)).reversed()
                .thenComparing(Comparator.comparingInt((Map<String, Object> player) -> cardList(player.get("artifacts")).size()).reversed())
                .thenComparing(Comparator.comparingInt(this::highestArtifact).reversed()));
        List<Map<String, Object>> rankings = new ArrayList<>();
        for (int index = 0; index < players.size(); index++) {
            Map<String, Object> player = new LinkedHashMap<>();
            player.put("rank", index + 1);
            player.put("userId", longValue(players.get(index).get("userId"), -1L));
            player.put("username", players.get(index).get("username"));
            player.put("totalTreasure", integer(players.get(index).get("totalTreasure"), 0));
            player.put("artifactCount", cardList(players.get(index).get("artifacts")).size());
            player.put("highestArtifact", highestArtifact(players.get(index)));
            rankings.add(player);
        }
        state.put("rankings", rankings);
        if (rankings.isEmpty()) {
            state.put("winners", List.of());
            return;
        }
        Map<String, Object> top = rankings.getFirst();
        List<Long> winners = rankings.stream()
                .filter(player -> Objects.equals(player.get("totalTreasure"), top.get("totalTreasure")))
                .filter(player -> Objects.equals(player.get("artifactCount"), top.get("artifactCount")))
                .filter(player -> Objects.equals(player.get("highestArtifact"), top.get("highestArtifact")))
                .map(player -> longValue(player.get("userId"), -1L))
                .toList();
        state.put("winners", winners);
    }

    private void distributeTreasure(Map<String, Object> state, int value) {
        List<Long> active = activeIds(state);
        if (active.isEmpty()) {
            return;
        }
        int share = value / active.size();
        int remainder = value % active.size();
        Map<String, Object> players = playerMap(state);
        for (Long id : active) {
            Map<String, Object> player = mapOf(players.get(String.valueOf(id)));
            player.put("roundTreasure", integer(player.get("roundTreasure"), 0) + share);
            players.put(String.valueOf(id), player);
        }
        state.put("players", new ArrayList<>(players.values()));
        state.put("roadTreasure", integer(state.get("roadTreasure"), 0) + remainder);
    }

    private void beginDecision(Map<String, Object> state) {
        state.put("phase", "DECISION");
        state.put("pendingDecisions", new LinkedHashMap<>());
        state.put("pendingTheftTargets", new LinkedHashMap<>());
        state.put("thieves", new ArrayList<>());
        state.put("targetCandidates", new ArrayList<>());
    }

    private boolean eventIs(Map<String, Object> state, String eventId) {
        return eventId.equals(Objects.toString(mapOf(state.get("currentEvent")).get("id"), ""));
    }

    private void drawEventForRound(Map<String, Object> state) {
        List<String> used = stringList(state.get("eventsUsed"));
        List<String> available = EVENT_IDS.stream().filter(eventId -> !used.contains(eventId)).toList();
        if (available.isEmpty()) {
            state.put("currentEvent", null);
            return;
        }
        String eventId = available.get(random.nextInt(available.size()));
        used.add(eventId);
        state.put("eventsUsed", used);
        Map<String, Object> event = eventDefinition(eventId);
        state.put("currentEvent", event);
        appendNotice(state, "本轮事件：" + event.get("name") + "。" + event.get("description"));
    }

    private Map<String, Object> eventDefinition(String eventId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", eventId);
        switch (eventId) {
            case "chupacabra" -> {
                event.put("name", "卓柏卡布拉");
                event.put("description", "独自返回或成为最后一名探险者的玩家会被攻击，失去本轮绿松石且无法获得神器。");
            }
            case "double_challenge" -> {
                event.put("name", "双重挑战");
                event.put("description", "每次探险连续翻开两张探险卡，翻完后统一进入决策。");
            }
            case "endless_wealth" -> {
                event.put("name", "无尽财富");
                event.put("description", "每种灾难首次出现时，道路上增加5颗绿松石。");
            }
            case "triple_challenge" -> {
                event.put("name", "三重挑战");
                event.put("description", "同种灾难第三次出现时才触发灾难阶段。");
            }
            case "farewell" -> {
                event.put("name", "后会有期");
                event.put("description", "宝藏卡上的绿松石全部留在道路上，只能通过返回营地获得。");
            }
            default -> throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "未知事件牌");
        }
        return event;
    }

    private List<Map<String, Object>> initialDeck() {
        List<Map<String, Object>> cards = new ArrayList<>();
        for (int index = 0; index < TREASURE_VALUES.size(); index++) {
            cards.add(treasureCard(index + 1, TREASURE_VALUES.get(index)));
        }
        for (String disaster : DISASTERS) {
            for (int copy = 1; copy <= 3; copy++) {
                cards.add(disasterCard(disaster, copy));
            }
        }
        cards.add(artifactCard(ARTIFACT_VALUES.getFirst(), 1));
        shuffle(cards);
        return cards;
    }

    private Map<String, Object> treasureCard(int id, int value) {
        return new LinkedHashMap<>(Map.of("id", "treasure-" + id, "type", "treasure", "value", value));
    }

    private Map<String, Object> disasterCard(String disaster, int copy) {
        return new LinkedHashMap<>(Map.of("id", "disaster-" + disaster + "-" + copy, "type", "disaster", "disaster", disaster));
    }

    private Map<String, Object> artifactCard(int value, int round) {
        return new LinkedHashMap<>(Map.of("id", "artifact-" + value, "type", "artifact", "value", value, "round", round));
    }

    private void removeClaimedArtifacts(Map<String, Object> state, List<Map<String, Object>> claimed) {
        List<Map<String, Object>> current = cardList(state.get("currentCards"));
        Set<String> ids = claimed.stream().map(card -> String.valueOf(card.get("id"))).collect(Collectors.toSet());
        current.removeIf(card -> ids.contains(String.valueOf(card.get("id"))));
        state.put("currentCards", current);
    }

    private boolean allActiveSubmitted(Map<String, Object> state, Map<String, Object> pending) {
        return activeIds(state).stream().map(String::valueOf).allMatch(pending::containsKey);
    }

    private void ensurePhase(Map<String, Object> state, String expected) {
        if (!expected.equals(state.get("phase"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前不在" + expected + "阶段");
        }
    }

    private void ensureActive(Map<String, Object> state, long userId) {
        if (!activeIds(state).contains(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "你已经返回营地或不在探险队中");
        }
    }

    private List<Map<String, Object>> playersOf(List<RoomSeat> seats) {
        return seats.stream().filter(RoomSeat::occupied).filter(seat -> !seat.bot()).map(seat -> {
            Map<String, Object> player = new LinkedHashMap<>();
            player.put("userId", seat.userId());
            player.put("username", seat.username());
            player.put("seatIndex", seat.seatIndex());
            return player;
        }).toList();
    }

    private List<Map<String, Object>> playerStates(List<Map<String, Object>> players) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> player : players) {
            Map<String, Object> value = new LinkedHashMap<>(player);
            value.put("active", true);
            value.put("roundTreasure", 0);
            value.put("totalTreasure", 0);
            value.put("artifacts", new ArrayList<>());
            value.put("stealUsed", false);
            result.add(value);
        }
        return result;
    }

    private List<Long> playerIds(List<Map<String, Object>> players) {
        return players.stream().map(player -> longValue(player.get("userId"), -1L)).toList();
    }

    private Map<String, Object> playerMap(Map<String, Object> state) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<String, Object> player : playerList(state)) {
            result.put(String.valueOf(player.get("userId")), player);
        }
        return result;
    }

    private List<Map<String, Object>> playerList(Map<String, Object> state) {
        List<Map<String, Object>> players = new ArrayList<>();
        Object raw = state.get("players");
        if (raw instanceof List<?> list) {
            for (Object value : list) {
                if (value instanceof Map<?, ?> map) {
                    players.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
        }
        return players;
    }

    private List<Long> activeIds(Map<String, Object> state) {
        return playerList(state).stream()
                .filter(player -> Boolean.TRUE.equals(player.get("active")))
                .map(player -> longValue(player.get("userId"), -1L))
                .toList();
    }

    private String playerName(Map<String, Object> players, long id) {
        Map<String, Object> player = mapOf(players.get(String.valueOf(id)));
        return Objects.toString(player.get("username"), "玩家" + id);
    }

    private int highestArtifact(Map<String, Object> player) {
        return cardList(player.get("artifacts")).stream()
                .mapToInt(card -> integer(card.get("value"), 0))
                .max().orElse(0);
    }

    private String decisionName(String value) {
        return switch (value) {
            case "continue" -> "继续探险";
            case "return" -> "结束探险";
            case "steal" -> "盗窃";
            default -> value;
        };
    }

    private Map<String, Object> initialDisasterMap(int value) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String disaster : DISASTERS) {
            result.put(disaster, value);
        }
        return result;
    }

    private void shuffle(List<Map<String, Object>> cards) {
        for (int index = cards.size() - 1; index > 0; index--) {
            int swap = random.nextInt(index + 1);
            Map<String, Object> value = cards.get(index);
            cards.set(index, cards.get(swap));
            cards.set(swap, value);
        }
    }

    private Map<String, Object> submittedMask(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.keySet().forEach(key -> result.put(key, true));
        return result;
    }

    private Map<String, Object> mapOf(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    private List<Map<String, Object>> cardList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
        }
        return result;
    }

    private List<Long> longList(Object value) {
        List<Long> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                result.add(longValue(item, -1L));
            }
        }
        return result;
    }

    private List<String> stringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private void appendNotice(Map<String, Object> state, String notice) {
        List<String> notices = new ArrayList<>();
        Object raw = state.get("notices");
        if (raw instanceof List<?> list) {
            list.forEach(item -> notices.add(String.valueOf(item)));
        }
        notices.add(0, notice);
        state.put("notices", notices.stream().limit(50).toList());
        List<Map<String, Object>> log = cardList(state.get("actionLog"));
        log.add(0, new LinkedHashMap<>(Map.of("summary", notice)));
        state.put("actionLog", log.stream().limit(50).toList());
    }
}
