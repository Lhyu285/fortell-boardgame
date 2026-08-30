package com.fortell.boardgame.game_modules.brass;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortell.boardgame.game_modules.GameModule;
import com.fortell.boardgame.game_modules.VersionedGameModule;
import com.fortell.boardgame.models.ApiException;
import com.fortell.boardgame.models.GameDescriptor;
import com.fortell.boardgame.models.GameType;
import com.fortell.boardgame.models.RoomEntity;
import com.fortell.boardgame.models.RoomSeat;
import com.fortell.boardgame.models.UserSummary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class BrassGameModule implements VersionedGameModule {
    private static final int STARTING_MONEY = 17;
    private static final int STARTING_INCOME_LEVEL = 10;
    private static final int MAX_INCOME_LEVEL = 99;
    private static final int STARTING_HAND_SIZE = 8;
    private static final int MAX_PLAYER_LINKS = 14;
    private static final int MAX_COAL_MARKET_SIZE = 14;
    private static final int MAX_IRON_MARKET_SIZE = 10;
    private static final int DISTANT_COAL_PRICE = 8;
    private static final int DISTANT_IRON_PRICE = 6;
    private static final int MAX_NOTICE_COUNT = 16;
    private static final List<Integer> FULL_COAL_MARKET = List.of(1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7);
    private static final List<Integer> INITIAL_COAL_MARKET = List.of(1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7);
    private static final List<Integer> FULL_IRON_MARKET = List.of(1, 1, 2, 2, 3, 3, 4, 4, 5, 5);
    private static final List<Integer> INITIAL_IRON_MARKET = List.of(2, 2, 3, 3, 4, 4, 5, 5);
    private static final List<String> PLAYER_COLORS = List.of("red", "yellow", "blue", "purple");

    private static final List<String> CITY_CARDS = List.of(
            "Warrington", "Stoke-on-Trent", "Stone", "Stafford", "Cannock", "Wolverhampton",
            "Coalbrookdale", "Dudley", "Kidderminster", "Worcester", "Birmingham", "Coventry",
            "Nuneaton", "Tamworth", "Walsall", "Burton-on-Trent", "Derby", "Nottingham",
            "Leek", "Belper", "Redditch", "Gloucester", "Oxford", "Shrewsbury"
    );

    private static final List<String> INDUSTRY_TYPES = List.of(
            "cotton_mill", "manufacturer", "brewery", "pottery", "iron_works", "coal_mine"
    );
    private static final List<String> SELLABLE_INDUSTRIES = List.of("cotton_mill", "manufacturer", "pottery");
    private static final List<String> CANAL_ERA_REMOVED_LEVEL_ONE_INDUSTRIES = List.of(
            "coal_mine", "iron_works", "cotton_mill", "manufacturer", "brewery", "pottery"
    );

    private static final Map<String, String> INDUSTRY_NAMES = Map.of(
            "cotton_mill", "\u68c9\u7eba\u5382",
            "manufacturer", "\u52a0\u5de5\u5382",
            "brewery", "\u917f\u9152\u5382",
            "pottery", "\u9676\u74f7\u5382",
            "iron_works", "\u94c1\u5382",
            "coal_mine", "\u7164\u77ff"
    );
    private static final Map<String, Integer> INDUSTRY_COSTS = Map.of(
            "cotton_mill", 12,
            "manufacturer", 8,
            "brewery", 5,
            "pottery", 17,
            "iron_works", 5,
            "coal_mine", 5
    );
    private static final Map<String, Integer> INDUSTRY_INCOME_REWARDS = Map.of(
            "cotton_mill", 5,
            "manufacturer", 3,
            "brewery", 4,
            "pottery", 5,
            "iron_works", 3,
            "coal_mine", 4
    );
    private static final Map<String, Integer> INDUSTRY_VP_REWARDS = Map.of(
            "cotton_mill", 5,
            "manufacturer", 3,
            "brewery", 4,
            "pottery", 10,
            "iron_works", 3,
            "coal_mine", 3
    );
    private static final Map<String, List<String>> CITY_SLOTS = Map.ofEntries(
            Map.entry("Warrington", List.of("cotton_mill", "manufacturer", "brewery")),
            Map.entry("Stoke-on-Trent", List.of("pottery", "coal_mine", "iron_works")),
            Map.entry("Stone", List.of("coal_mine", "brewery")),
            Map.entry("Stafford", List.of("pottery", "manufacturer")),
            Map.entry("Cannock", List.of("coal_mine", "iron_works")),
            Map.entry("Wolverhampton", List.of("manufacturer", "iron_works")),
            Map.entry("Coalbrookdale", List.of("coal_mine", "iron_works")),
            Map.entry("Dudley", List.of("coal_mine", "iron_works")),
            Map.entry("Kidderminster", List.of("cotton_mill", "manufacturer")),
            Map.entry("Worcester", List.of("cotton_mill", "brewery")),
            Map.entry("Birmingham", List.of("cotton_mill", "manufacturer", "iron_works", "brewery")),
            Map.entry("Coventry", List.of("manufacturer", "pottery")),
            Map.entry("Nuneaton", List.of("cotton_mill", "coal_mine")),
            Map.entry("Tamworth", List.of("cotton_mill", "manufacturer")),
            Map.entry("Walsall", List.of("manufacturer", "iron_works")),
            Map.entry("Burton-on-Trent", List.of("brewery", "manufacturer")),
            Map.entry("Derby", List.of("cotton_mill", "manufacturer", "iron_works")),
            Map.entry("Nottingham", List.of("cotton_mill", "manufacturer", "brewery")),
            Map.entry("Leek", List.of("cotton_mill", "manufacturer")),
            Map.entry("Belper", List.of("cotton_mill", "coal_mine", "iron_works")),
            Map.entry("Redditch", List.of("manufacturer", "iron_works")),
            Map.entry("Gloucester", List.of("brewery", "manufacturer")),
            Map.entry("Oxford", List.of("cotton_mill", "manufacturer")),
            Map.entry("Shrewsbury", List.of("cotton_mill", "manufacturer"))
    );
    private static final List<Map<String, Object>> ROUTES = List.of(
            route("Warrington", "Wigan", false, true),
            route("Warrington", "Stoke-on-Trent", true, true),
            route("Stoke-on-Trent", "Stone", true, true),
            route("Stone", "Stafford", true, true),
            route("Stafford", "Cannock", true, true),
            route("Cannock", "Wolverhampton", true, true),
            route("Wolverhampton", "Birmingham", true, true),
            route("Birmingham", "Walsall", true, true),
            route("Birmingham", "Coventry", true, true),
            route("Birmingham", "Kidderminster", true, true),
            route("Birmingham", "Redditch", true, true),
            route("Coventry", "Nuneaton", true, true),
            route("Nuneaton", "Tamworth", true, true),
            route("Tamworth", "Burton-on-Trent", true, true),
            route("Burton-on-Trent", "Derby", true, true),
            route("Derby", "Nottingham", true, true),
            route("Derby", "Belper", true, true),
            route("Belper", "Leek", true, true),
            route("Dudley", "Walsall", true, true),
            route("Dudley", "Coalbrookdale", true, true),
            route("Coalbrookdale", "Wolverhampton", true, true),
            route("Kidderminster", "Worcester", true, true),
            route("Worcester", "Gloucester", true, true),
            route("Redditch", "Oxford", true, true),
            route("Gloucester", "Oxford", true, true),
            route("Shrewsbury", "Coalbrookdale", true, true)
    );
    private static final Map<String, Object> BIRMINGHAM_MAP = loadBirminghamMap();
    private static final Map<String, String> CITY_NAME_MAPPING = loadCityNameMapping();
    private static final Map<String, Map<String, Object>> MAP_CITY_METADATA = loadCityMetadata(BIRMINGHAM_MAP);
    private static final Map<String, List<List<String>>> MAP_CITY_SLOT_OPTIONS = loadCitySlotOptions(BIRMINGHAM_MAP);
    private static final List<Map<String, Object>> MAP_ROUTES = loadRoutes(BIRMINGHAM_MAP);
    private static final List<String> MAP_CITY_CARDS = loadCityCards(BIRMINGHAM_MAP);
    private static final List<Map<String, Object>> MAP_MARKETS = loadMarkets(BIRMINGHAM_MAP);
    private static final List<Integer> INCOME_BY_LEVEL = loadIncomeConfig();
    private static final Map<Integer, Integer> INCOME_LEVEL_AFTER_LOAN = loadIncomeLevelAfterLoan();
    private static final Map<String, List<Map<String, Object>>> BUILDING_TILES = loadBuildingConfig();
    private static final Map<String, Object> CARD_CONFIG = loadJsonObject("Birmingham_cards.json");
    private static final List<Map<String, Object>> MERCHANT_TILE_CONFIG = loadJsonList("Birmingham_merchant_tiles.json");

    @Override
    public GameType gameType() {
        return GameType.BRASS;
    }

    @Override
    public GameDescriptor descriptor() {
        return new GameDescriptor(
                "brass",
                "\u5de5\u4e1a\u9769\u547d\uff1a\u4f2f\u660e\u7ff0",
                "/brass",
                "/brass/rule",
                2,
                4,
                "BGG \u9ad8\u5206\u5fb7\u5f0f\u6e38\u620f"
        );
    }

    @Override
    public Map<String, Object> defaultConfig() {
        return new LinkedHashMap<>(Map.of("variant", "standard"));
    }

    @Override
    public Map<String, Object> initialState(RoomEntity room, List<RoomSeat> seats) {
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

    @Override
    public Map<String, Object> sanitizeConfig(Map<String, Object> requested, RoomEntity room, List<RoomSeat> seats) {
        Map<String, Object> merged = new LinkedHashMap<>(defaultConfig());
        if (requested != null) {
            merged.putAll(requested);
        }
        return merged;
    }

    @Override
    public void validateCanStart(RoomEntity room, List<RoomSeat> seats) {
        int seated = playersOf(seats).size();
        if (seated < gameType().minPlayers()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u4eba\u6570\u4e0d\u8db3");
        }
        if (seated > gameType().maxPlayers()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u4eba\u6570\u8fc7\u591a");
        }
    }

    @Override
    public Map<String, Object> onStart(RoomEntity room, List<RoomSeat> seats, Map<String, Object> config) {
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

        updateMaintenanceFlag(state);
        return state;
    }

    @Override
    public Map<String, Object> onAction(RoomEntity room, List<RoomSeat> seats, Map<String, Object> config,
                                        Map<String, Object> state, UserSummary actor, String actionType,
                                        Map<String, Object> payload, List<String> notices) {
        Map<String, Object> workingState = mapOf(deepCopy(state));
        ensurePlaying(workingState);
        ensureCurrentPlayer(workingState, actor);
        ensureActionAvailable(workingState, actionType);

        Map<String, Object> nextState = switch (actionType) {
            case "restart_turn" -> restartTurn(workingState, actor);
            case "end_turn" -> endTurnAction(workingState, actor);
            case "skip" -> skipAction(workingState, actor, payload);
            case "loan" -> loanAction(workingState, actor, payload);
            case "scout" -> scoutAction(workingState, actor, payload);
            case "build" -> buildAction(workingState, actor, payload);
            case "sell" -> sellAction(workingState, actor, payload);
            case "end_sell" -> endSellAction(workingState, actor);
            case "network" -> networkAction(workingState, actor, payload);
            case "develop" -> developAction(workingState, actor, payload);
            case "resolve_income_debt" -> resolveIncomeDebtAction(workingState, actor, payload);
            case "maintain_era" -> maintainEraAction(workingState, actor);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "\u672a\u77e5\u7684\u884c\u52a8");
        };
        updateMaintenanceFlag(nextState);
        return nextState;
    }

    private void ensureActionAvailable(Map<String, Object> state, String actionType) {
        Map<String, Object> available = availableActions(state);
        List<String> actions = stringList(available.get("actions"));
        if (!actions.contains(actionType)) {
            String message = switch (actionType) {
                case "build" -> "\u5f53\u524d\u6ca1\u6709\u53ef\u5b8c\u6210\u7684\u5efa\u9020\u65b9\u6848\uff0c\u8bf7\u68c0\u67e5\u91d1\u94b1\u3001\u7164\u94c1\u8d44\u6e90\u4e0e\u7f51\u7edc\u8fde\u63a5";
                case "network" -> "\u5f53\u524d\u6ca1\u6709\u53ef\u5b8c\u6210\u7684\u8fd0\u8f93\u7f51\u65b9\u6848\uff0c\u8bf7\u68c0\u67e5\u91d1\u94b1\u3001\u7164\u9152\u8d44\u6e90\u4e0e\u7f51\u7edc\u8fde\u63a5";
                case "sell" -> "\u5f53\u524d\u6ca1\u6709\u53ef\u5b8c\u6210\u7684\u552e\u5356\u65b9\u6848\uff0c\u8bf7\u68c0\u67e5\u8d38\u6613\u5546\u4e0e\u5564\u9152\u6765\u6e90";
                case "develop" -> "\u5f53\u524d\u6ca1\u6709\u53ef\u5b8c\u6210\u7684\u7814\u53d1\u65b9\u6848\uff0c\u8bf7\u68c0\u67e5\u94c1\u8d44\u6e90";
                default -> "\u5f53\u524d\u4e0d\u80fd\u6267\u884c\u8be5\u884c\u52a8";
            };
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
    }

    @Override
    public List<String> rules() {
        return List.of(
                "\u6bcf\u56de\u5408\u6267\u884c\u4e24\u6b21\u4e3b\u8981\u884c\u52a8\uff0c\u8fd0\u6cb3\u65f6\u4ee3\u7b2c\u4e00\u56de\u5408\u4ec5\u6267\u884c\u4e00\u6b21\u884c\u52a8\u3002",
                "\u5efa\u9020\u3001\u51fa\u552e\u3001\u7814\u53d1\u3001\u8fd0\u8f93\u7f51\u3001\u8d37\u6b3e\u548c\u4fa6\u67e5\u90fd\u7531\u540e\u7aef\u6821\u9a8c\u89c4\u5219\u3002",
                "\u73a9\u5bb6\u53ef\u4ee5\u5728\u56de\u5408\u7ed3\u675f\u62bd\u724c\u524d\u91cd\u65b0\u5f00\u59cb\u672c\u56de\u5408\u3002"
        );
    }

    private Map<String, Object> restartTurn(Map<String, Object> state, UserSummary actor) {
        Map<String, Object> snapshot = mapOf(state.get("turnStartSnapshot"));
        if (snapshot.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u5f53\u524d\u6ca1\u6709\u53ef\u6062\u590d\u7684\u56de\u5408\u5feb\u7167");
        }
        if (parseInteger(mapOf(state.get("turn")).get("actionsTaken"), 0) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u5c1a\u672a\u6267\u884c\u884c\u52a8\uff0c\u4e0d\u9700\u8981\u91cd\u65b0\u5f00\u59cb");
        }
        Map<String, Object> restored = mapOf(deepCopy(snapshot));
        restored.put("turnStartSnapshot", deepCopy(snapshot));
        appendNotice(restored, "\u73a9\u5bb6 " + actor.username() + " \u91cd\u65b0\u5f00\u59cb\u672c\u56de\u5408");
        appendActionLog(restored, actor, "restart_turn", "\u91cd\u65b0\u5f00\u59cb\u672c\u56de\u5408");
        updateMaintenanceFlag(restored);
        return restored;
    }

    private Map<String, Object> endTurnAction(Map<String, Object> state, UserSummary actor) {
        Map<String, Object> turn = mapOf(state.get("turn"));
        if (!Boolean.TRUE.equals(turn.get("awaitingEndTurn"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u5f53\u524d\u8fd8\u4e0d\u80fd\u7ed3\u675f\u56de\u5408");
        }
        drawHandToLimit(state, actor.id(), STARTING_HAND_SIZE);
        appendNotice(state, "\u73a9\u5bb6 " + actor.username() + " \u7ed3\u675f\u4e86\u56de\u5408");
        advanceTurn(state);
        return state;
    }

    private Map<String, Object> resolveIncomeDebtAction(Map<String, Object> state, UserSummary actor,
                                                        Map<String, Object> payload) {
        Map<String, Object> debt = mapOf(state.get("incomeDebt"));
        if (asLong(debt.get("playerId")) != actor.id()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前不需要该玩家处理收入欠款");
        }
        int amount = parseInteger(debt.get("amount"), 0);
        String tileId = Objects.toString(payload.get("tileId"), "");
        Map<String, Object> board = mapOf(state.get("board"));
        List<Map<String, Object>> industries = listOf(board.get("industries"));
        Map<String, Object> tile = findTile(industries, tileId);
        if (tile.isEmpty() || asLong(tile.get("ownerId")) != actor.id()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择地图上自己的产业板块");
        }

        int proceeds = Math.max(0, parseInteger(tile.get("cost"), 0) / 2);
        industries.removeIf(item -> tileId.equals(Objects.toString(item.get("id"), "")));
        board.put("industries", industries);
        state.put("board", board);

        Map<String, Object> statsByPlayer = mapOf(state.get("playerStats"));
        Map<String, Object> stats = mapOf(statsByPlayer.get(String.valueOf(actor.id())));
        int availableMoney = parseInteger(stats.get("money"), 0) + proceeds;
        int paid = Math.min(amount, availableMoney);
        stats.put("money", availableMoney - paid);
        statsByPlayer.put(String.valueOf(actor.id()), stats);
        state.put("playerStats", statsByPlayer);
        debt.put("amount", amount - paid);
        state.put("incomeDebt", debt);
        appendNotice(state, "玩家" + actor.username() + " 移除了 "
                + cityCnName(Objects.toString(tile.get("city"), "")) + " 的 "
                + parseInteger(tile.get("level"), 0) + "级"
                + industryNameCn(Objects.toString(tile.get("industryType"), ""))
                + "，获得" + proceeds + "英镑用于支付收入欠款");
        continueIncomeDebtResolution(state);
        if (!Boolean.TRUE.equals(state.get("eraEnding")) && mapOf(state.get("incomeDebt")).isEmpty()) {
            long nextPlayerId = asLong(state.get("currentPlayerId"));
            beginTurn(state, "轮到 " + playerName(listOf(state.get("players")), nextPlayerId) + " 行动");
        }
        return state;
    }

    private Map<String, Object> skipAction(Map<String, Object> state, UserSummary actor, Map<String, Object> payload) {
        ensureTurnAcceptsAction(state);
        discardActionCard(state, actor.id(), payload);
        appendNotice(state, "玩家" + actor.username() + " 跳过了一个行动");
        consumeAction(state, actor);
        return state;
    }

    private Map<String, Object> maintainEraAction(Map<String, Object> state, UserSummary actor) {
        if (!canMaintainEra(state)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u5f53\u524d\u4e0d\u80fd\u6267\u884c\u65f6\u4ee3\u7ef4\u62a4");
        }
        scoreCurrentEra(state);
        if ("canal".equals(state.get("era"))) {
            transitionToRailEra(state);
            appendActionLog(state, actor, "maintain_era", "\u8fd0\u6cb3\u65f6\u4ee3\u7ed3\u675f\uff0c\u8fdb\u5165\u94c1\u8def\u65f6\u4ee3");
            return state;
        }

        finishGame(state);
        appendActionLog(state, actor, "maintain_era", "\u94c1\u8def\u65f6\u4ee3\u7ed3\u675f\uff0c\u6e38\u620f\u7ed3\u675f");
        return state;
    }

    private Map<String, Object> buildAction(Map<String, Object> state, UserSummary actor, Map<String, Object> payload) {
        ensureTurnAcceptsAction(state);
        String city = Objects.toString(payload.get("city"), "");
        String industryType = Objects.toString(payload.get("industryType"), "");
        String cardId = Objects.toString(payload.get("cardId"), "");
        if (!citySlotOptions().containsKey(city)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u57ce\u5e02\u4e0d\u5408\u6cd5");
        }
        if (!INDUSTRY_TYPES.contains(industryType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u4ea7\u4e1a\u4e0d\u5408\u6cd5");
        }
        if (!cityCanBuild(city, industryType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8be5\u57ce\u5e02\u4e0d\u80fd\u5efa\u9020\u8be5\u4ea7\u4e1a");
        }

        Map<String, Object> hands = mapOf(state.get("hands"));
        List<Map<String, Object>> hand = listOf(hands.get(String.valueOf(actor.id())));
        Map<String, Object> card = findCard(hand, cardId);
        if (card == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u627e\u4e0d\u5230\u8981\u4f7f\u7528\u7684\u624b\u724c");
        }
        Map<String, Object> board = mapOf(state.get("board"));
        List<Map<String, Object>> industries = listOf(board.get("industries"));
        int requestedSlotIndex = parseInteger(payload.get("slotIndex"), -1);
        if (!cardCanBuild(card, city, industryType, board, actor.id())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8fd9\u5f20\u724c\u4e0d\u80fd\u7528\u4e8e\u672c\u6b21\u5efa\u9020");
        }
        if (List.of("Personal_Brewery", "Rural_Brewery").contains(city) && !cardCanBuildAnonymousBrewery(card)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u65e0\u540d\u917f\u9152\u5382\u53ea\u80fd\u901a\u8fc7\u917f\u9152\u5382\u4ea7\u4e1a\u724c\u6216\u4e07\u80fd\u4ea7\u4e1a\u724c\u5efa\u9020");
        }

        Map<String, Object> nextBoardTile = nextAvailableBoardTile(state, actor.id(), industryType);
        int nextLevel = parseInteger(nextBoardTile.get("level"), 0);
        Map<String, Object> ownCoveredTile = requestedSlotIndex >= 0
                ? coverableOwnFlippedTile(industries, actor.id(), city, industryType, requestedSlotIndex, nextLevel)
                : coverableOwnFlippedTile(industries, actor.id(), city, industryType, nextLevel);
        if ("canal".equals(state.get("era"))
                && playerAlreadyBuiltInCity(industries, actor.id(), city)
                && ownCoveredTile.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8fd0\u6cb3\u65f6\u4ee3\u540c\u4e00\u73a9\u5bb6\u4e0d\u80fd\u5728\u540c\u4e00\u57ce\u5e02\u5efa\u9020\u591a\u4e2a\u4ea7\u4e1a");
        }
        int slotIndex = requestedSlotIndex >= 0
                ? buildSlotIndexForOption(state, industries, actor.id(), city, industryType, requestedSlotIndex)
                : firstAvailableSlot(industries, city, industryType);
        Map<String, Object> coveredTile = new LinkedHashMap<>();
        if (slotIndex >= 0 && !ownCoveredTile.isEmpty()
                && parseInteger(ownCoveredTile.get("slotIndex"), -1) == slotIndex) {
            coveredTile = ownCoveredTile;
            String coveredTileId = Objects.toString(coveredTile.get("id"), "");
            industries.removeIf(tile -> Objects.equals(tile.get("id"), coveredTileId));
            board.put("industries", industries);
            state.put("board", board);
        }
        if (slotIndex < 0 && List.of("coal_mine", "iron_works").contains(industryType)) {
            coveredTile = requestedSlotIndex >= 0
                    ? coverableOpponentResourceTile(state, actor.id(), city, industryType, requestedSlotIndex)
                    : coverableOpponentResourceTile(state, actor.id(), city, industryType);
            if (!coveredTile.isEmpty()) {
                slotIndex = parseInteger(coveredTile.get("slotIndex"), 0);
                String coveredTileId = Objects.toString(coveredTile.get("id"), "");
                industries.removeIf(tile -> Objects.equals(tile.get("id"), coveredTileId));
                board.put("industries", industries);
                state.put("board", board);
            }
        }
        if (slotIndex < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u6ca1\u6709\u53ef\u7528\u7684\u57ce\u5e02\u69fd\u4f4d");
        }

        Map<String, Object> boardTile = nextBoardTile;
        int cost = parseInteger(boardTile.get("cost"), INDUSTRY_COSTS.get(industryType));
        payMoney(state, actor.id(), cost);
        List<String> resourceNotices = new ArrayList<>();
        for (int count = 0; count < parseInteger(boardTile.get("coalCost"), 0); count++) {
            resourceNotices.add(consumeCoal(state, actor.id(), preferredResourceId(new LinkedHashMap<>(), payload, "coalSourceTileId", "coalSourceTileIds", count), List.of(city)));
        }
        for (int count = 0; count < parseInteger(boardTile.get("ironCost"), 0); count++) {
            resourceNotices.add(consumeIron(state, actor.id(), preferredResourceId(new LinkedHashMap<>(), payload, "ironSourceTileId", "ironSourceTileIds", count)));
        }
        removeBoardTile(state, actor.id(), industryType, Objects.toString(boardTile.get("id"), ""));
        discardCard(state, actor.id(), cardId);
        board = mapOf(state.get("board"));
        industries = listOf(board.get("industries"));

        Map<String, Object> tile = new LinkedHashMap<>();
        tile.put("id", "tile_" + actor.id() + "_" + System.nanoTime());
        tile.put("sourceTileId", boardTile.get("id"));
        tile.put("ownerId", actor.id());
        tile.put("ownerName", actor.username());
        tile.put("city", city);
        tile.put("slotIndex", slotIndex);
        tile.put("industryType", industryType);
        tile.put("industryName", industryNameCn(industryType));
        tile.put("level", boardTile.get("level"));
        tile.put("era", state.get("era"));
        tile.put("flipped", false);
        tile.put("cost", cost);
        int builtResourceAmount = "deplete".equals(boardTile.get("flipType"))
                ? resourceAmountForEra(boardTile, Objects.toString(state.get("era"), "canal"))
                : 0;
        tile.put("coal", "coal_mine".equals(industryType) ? builtResourceAmount : 0);
        tile.put("iron", "iron_works".equals(industryType) ? builtResourceAmount : 0);
        tile.put("beer", "brewery".equals(industryType) ? builtResourceAmount : 0);
        tile.put("coalCost", boardTile.get("coalCost"));
        tile.put("ironCost", boardTile.get("ironCost"));
        tile.put("saleBeerCost", boardTile.get("saleBeerCost"));
        tile.put("roadPoints", boardTile.get("roadPoints"));
        tile.put("flipType", boardTile.get("flipType"));
        tile.put("incomeReward", boardTile.get("incomeReward"));
        tile.put("victoryPoints", boardTile.get("victoryPoints"));
        industries.add(tile);
        board.put("industries", industries);
        state.put("board", board);
        List<String> marketNotices = sellNewResourceToMarket(state, tile);

        appendActionLog(state, actor, "build", "建造" + cityCnName(city) + " " + industryNameCn(industryType));
        for (int index = marketNotices.size() - 1; index >= 0; index--) {
            appendNotice(state, marketNotices.get(index));
        }
        appendNotice(state, "玩家" + actor.username() + " 在 " + cityCnName(city) + " 建造了 " + industryNameCn(industryType));
        consumeAction(state, actor);
        return state;
    }

    private Map<String, Object> developAction(Map<String, Object> state, UserSummary actor, Map<String, Object> payload) {
        ensureTurnAcceptsAction(state);
        String cardId = Objects.toString(payload.get("cardId"), "");
        List<String> industryTypes = stringList(payload.get("industryTypes")).stream()
                .filter(value -> !value.isBlank())
                .toList();
        if (cardId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8bf7\u9009\u62e9\u8981\u5f03\u7f6e\u7684\u624b\u724c");
        }
        if (findCard(currentHand(state, actor.id()), cardId) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u6240\u9009\u624b\u724c\u4e0d\u5728\u4f60\u7684\u624b\u724c\u4e2d");
        }
        if (industryTypes.isEmpty() || industryTypes.size() > 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u7814\u53d1\u884c\u52a8\u9700\u8981\u9009\u62e91\u52302\u4e2a\u677f\u5757");
        }
        for (String industryType : industryTypes) {
            if (!INDUSTRY_TYPES.contains(industryType)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "\u4ea7\u4e1a\u4e0d\u5408\u6cd5");
            }
        }

        int moneyBeforeDevelop = playerMoney(state, actor.id());
        discardCard(state, actor.id(), cardId);
        List<String> notices = new ArrayList<>();
        List<String> developedItems = new ArrayList<>();
        for (int index = 0; index < industryTypes.size(); index++) {
            String industryType = industryTypes.get(index);
            Map<String, Object> removedTile = lowestDevelopableBoardTileOrThrow(state, actor.id(), industryType);
            if (removedTile.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, industryNameCn(industryType) + "\u6ca1\u6709\u53ef\u7814\u53d1\u7684\u677f\u5757");
            }
            developedItems.add(removedTile.get("level") + "级" + industryNameCn(industryType));
            notices.add(consumeIron(state, actor.id(), preferredResourceId(new LinkedHashMap<>(), payload, "ironSourceTileId", "ironSourceTileIds", index)));
            removeBoardTile(state, actor.id(), industryType, Objects.toString(removedTile.get("id"), ""));
            incrementDevelopment(state, actor.id(), industryType);
        }
        String developedNames = developedItems.stream().reduce((left, right) -> left + " 和 " + right).orElse("");
        int developCost = Math.max(0, moneyBeforeDevelop - playerMoney(state, actor.id()));
        appendNotice(state, "玩家" + actor.username() + " 研发了 " + developedNames + "，花费 " + developCost + " 英镑");
        consumeAction(state, actor);
        return state;
    }

    private Map<String, Object> sellAction(Map<String, Object> state, UserSummary actor, Map<String, Object> payload) {
        ensureTurnAcceptsAction(state);
        Map<String, Object> turn = mapOf(state.get("turn"));
        boolean sellInProgress = Boolean.TRUE.equals(turn.get("sellInProgress"));
        String cardId = Objects.toString(payload.get("cardId"), "");
        if (payload.containsKey("tileIds")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "每次只能出售一个产业");
        }
        String tileId = Objects.toString(payload.get("tileId"), "");
        if (!sellInProgress && cardId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8bf7\u9009\u62e9\u8981\u5f03\u7f6e\u7684\u624b\u724c");
        }
        if (tileId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8bf7\u9009\u62e9\u8981\u51fa\u552e\u7684\u4ea7\u4e1a");
        }
        if (!sellInProgress && findCard(currentHand(state, actor.id()), cardId) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u6240\u9009\u624b\u724c\u4e0d\u5728\u4f60\u7684\u624b\u724c\u4e2d");
        }

        Map<String, Object> board = mapOf(state.get("board"));
        List<Map<String, Object>> industries = listOf(board.get("industries"));
        Map<String, Object> tile = findTile(industries, tileId);
        if (tile == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u4ea7\u4e1a\u4e0d\u5408\u6cd5");
        }
        if (asLong(tile.get("ownerId")) != actor.id()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u627e\u4e0d\u5230\u8981\u51fa\u552e\u7684\u4ea7\u4e1a");
        }
        if (Boolean.TRUE.equals(tile.get("flipped"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8be5\u4ea7\u4e1a\u4e0d\u80fd\u51fa\u552e");
        }
        String industryType = Objects.toString(tile.get("industryType"), "");
        if (!"sell".equals(tile.get("flipType"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8be5\u4ea7\u4e1a\u5df2\u7ffb\u9762");
        }

        if (!sellInProgress) {
            discardCard(state, actor.id(), cardId);
            turn.put("sellInProgress", true);
            turn.put("sellCount", 0);
        }
        String merchantId = Objects.toString(payload.get("merchantId"), "");
        int beerRequired = saleBeerRequired(tile);
        if (merchantId.isBlank() && beerRequired > 0) {
            consumeBeer(state, industries, actor.id(), Objects.toString(payload.get("beerSourceTileId"), ""),
                    Objects.toString(tile.get("city"), ""), beerRequired);
        } else {
            if (merchantId.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "出售产业必须选择接受该产业的贸易商");
            }
            validateMerchantBeerForSale(state, actor.id(), merchantId, Objects.toString(tile.get("city"), ""),
                    Objects.toString(tile.get("industryType"), ""), beerRequired);
            if (beerRequired > 0) {
                consumeMerchantBeer(state, actor.id(), merchantId, Objects.toString(tile.get("city"), ""),
                        Objects.toString(tile.get("industryType"), ""), beerRequired,
                        Objects.toString(payload.get("freeDevelopIndustryType"), ""));
            }
        }
        flipTile(state, tile);
        board.put("industries", industries);
        state.put("board", board);
        int sellCount = parseInteger(turn.get("sellCount"), 0) + 1;
        turn.put("sellCount", sellCount);
        state.put("turn", turn);
        String soldText = cityCnName(Objects.toString(tile.get("city"), "")) + "的"
                + tile.get("level") + "级" + industryNameCn(Objects.toString(tile.get("industryType"), ""));
        appendActionLog(state, actor, "sell", "出售 " + soldText);
        String merchantCityText = merchantCityText(state, merchantId, Objects.toString(tile.get("city"), ""));
        appendNotice(state, "玩家" + actor.username() + " 在 " + merchantCityText + "市场 出售了 " + soldText);
        if (!merchantId.isBlank() && beerRequired > 0) {
            appendNotice(state, "玩家" + actor.username() + " 因消耗 " + merchantCityText + "市场 的酒获得奖励");
        }
        return state;
    }

    private Map<String, Object> endSellAction(Map<String, Object> state, UserSummary actor) {
        ensureTurnAcceptsAction(state);
        Map<String, Object> turn = mapOf(state.get("turn"));
        if (!Boolean.TRUE.equals(turn.get("sellInProgress")) || parseInteger(turn.get("sellCount"), 0) < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "尚未完成任何产业售卖，不能结束出售行动");
        }
        turn.remove("sellInProgress");
        turn.remove("sellCount");
        state.put("turn", turn);
        appendNotice(state, "玩家" + actor.username() + " 结束了出售行动");
        consumeAction(state, actor);
        return state;
    }

    private Map<String, Object> networkAction(Map<String, Object> state, UserSummary actor, Map<String, Object> payload) {
        ensureTurnAcceptsAction(state);
        String cardId = Objects.toString(payload.get("cardId"), "");
        if (cardId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8bf7\u9009\u62e9\u8981\u5f03\u7f6e\u7684\u624b\u724c");
        }
        if (findCard(currentHand(state, actor.id()), cardId) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u6240\u9009\u624b\u724c\u4e0d\u5728\u4f60\u7684\u624b\u724c\u4e2d");
        }
        String linkType = "canal".equals(state.get("era")) ? "canal" : "rail";
        List<Map<String, Object>> routeSpecs = networkRouteSpecs(payload);
        if (routeSpecs.isEmpty() || routeSpecs.size() > ("rail".equals(linkType) ? 2 : 1)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8def\u7ebf\u6570\u91cf\u4e0d\u5408\u6cd5");
        }

        Map<String, Object> board = mapOf(state.get("board"));
        List<Map<String, Object>> links = listOf(board.get("links"));
        long existingLinkCount = links.stream().filter(link -> asLong(link.get("ownerId")) == actor.id()).count();
        List<Map<String, Object>> builtLinks = new ArrayList<>();
        List<String> resourceNotices = new ArrayList<>();
        if (existingLinkCount + routeSpecs.size() > MAX_PLAYER_LINKS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u6bcf\u540d\u73a9\u5bb6\u6700\u591a\u4fee\u5efa14\u6761\u8def");
        }
        if ("rail".equals(linkType)) {
            payMoney(state, actor.id(), routeSpecs.size() == 2 ? 15 : 5);
        } else {
            payMoney(state, actor.id(), 3);
        }
        for (Map<String, Object> routeSpec : routeSpecs) {
            String from = Objects.toString(routeSpec.get("from"), "");
            String to = Objects.toString(routeSpec.get("to"), "");
            Map<String, Object> route = findRoute(from, to);
            if (route == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "\u8def\u7ebf\u4e0d\u5408\u6cd5");
            }
            if (!Boolean.TRUE.equals(route.get(linkType))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "\u8fd9\u6761\u8def\u5df2\u7ecf\u88ab\u5efa\u9020");
            }
            if (routeOccupied(links, from, to, linkType)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "这条路已经被修建");
            }
            if (!touchesPlayerNetwork(board, actor.id(), from, to)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "\u4fee\u8def\u5fc5\u987b\u8fde\u63a5\u5230\u81ea\u5df1\u7684\u7f51\u7edc");
            }

            Map<String, Object> link = new LinkedHashMap<>();
            link.put("id", "link_" + actor.id() + "_" + System.nanoTime() + "_" + builtLinks.size());
            link.put("ownerId", actor.id());
            link.put("ownerName", actor.username());
            link.put("from", normalizedRouteEnd(from, to).get(0));
            link.put("to", normalizedRouteEnd(from, to).get(1));
            link.put("type", linkType);
            link.put("color", playerColor(state, actor.id()));
            links.add(link);
            builtLinks.add(link);
            board.put("links", links);
            state.put("board", board);
            if ("rail".equals(linkType)) {
                int index = builtLinks.size() - 1;
                resourceNotices.add(consumeCoal(
                        state,
                        actor.id(),
                        preferredResourceId(routeSpec, payload, "coalSourceTileId", "coalSourceTileIds", index),
                        List.of(Objects.toString(link.get("from"), ""), Objects.toString(link.get("to"), ""))
                ));
                board = mapOf(state.get("board"));
                links = listOf(board.get("links"));
            }
        }

        if ("rail".equals(linkType) && builtLinks.size() == 2) {
            board = mapOf(state.get("board"));
            resourceNotices.add(consumeNetworkBeer(
                    state,
                    listOf(board.get("industries")),
                    actor.id(),
                    preferredResourceId(new LinkedHashMap<>(), payload, "beerSourceTileId", "beerSourceTileIds", 0),
                    networkDestinations(builtLinks),
                    1
            ));
        }
        discardCard(state, actor.id(), cardId);
        board = mapOf(state.get("board"));
        state.put("board", board);

        String routeText = builtLinks.stream()
                .map(link -> cityCnName(Objects.toString(link.get("from"), "")) + " - " + cityCnName(Objects.toString(link.get("to"), "")))
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
        appendActionLog(state, actor, "network", "修建" + ("rail".equals(linkType) ? "铁路" : "运河") + " " + routeText);
        for (Map<String, Object> link : builtLinks) {
        appendNotice(state, "玩家" + actor.username() + " 建造了连接 "
                    + cityCnName(Objects.toString(link.get("from"), "")) + " 和 "
                    + cityCnName(Objects.toString(link.get("to"), "")) + " 的"
                    + ("rail".equals(linkType) ? "铁路" : "运河"));
        }
        consumeAction(state, actor);
        return state;
    }

    private Map<String, Object> loanAction(Map<String, Object> state, UserSummary actor, Map<String, Object> payload) {
        ensureTurnAcceptsAction(state);
        String cardId = Objects.toString(payload.get("cardId"), "");
        if (cardId.isBlank() || findCard(currentHand(state, actor.id()), cardId) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8bf7\u9009\u62e9\u6709\u6548\u624b\u724c");
        }
        Map<String, Object> statsByPlayer = mapOf(state.get("playerStats"));
        Map<String, Object> stats = mapOf(statsByPlayer.get(String.valueOf(actor.id())));
        int incomeLevel = parseInteger(stats.get("incomeLevel"), STARTING_INCOME_LEVEL);
        if (incomeLevel < 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "收入等级为0、1或2时不能进行贷款");
        }
        Integer incomeLevelAfterLoan = INCOME_LEVEL_AFTER_LOAN.get(incomeLevel);
        if (incomeLevelAfterLoan == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前收入等级不能进行贷款");
        }
        discardCard(state, actor.id(), cardId);
        stats.put("money", parseInteger(stats.get("money"), 0) + 30);
        stats.put("incomeLevel", incomeLevelAfterLoan);
        stats.put("income", incomeForLevel(incomeLevelAfterLoan));
        statsByPlayer.put(String.valueOf(actor.id()), stats);
        state.put("playerStats", statsByPlayer);
        appendActionLog(state, actor, "loan", "\u83b7\u5f9730\u82f1\u9551\uff0c\u964d\u4f4e3\u7ea7\u6536\u5165");
        appendNotice(state, "玩家" + actor.username() + " 进行了贷款");
        consumeAction(state, actor);
        return state;
    }

    private Map<String, Object> scoutAction(Map<String, Object> state, UserSummary actor, Map<String, Object> payload) {
        ensureTurnAcceptsAction(state);
        List<String> cardIds = stringList(payload.get("cardIds"));
        if (cardIds.size() != 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u4fa6\u67e5\u5fc5\u987b\u5f03\u7f6e3\u5f20\u724c");
        }
        if (cardIds.stream().distinct().count() != cardIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u4e0d\u80fd\u91cd\u590d\u9009\u62e9\u540c\u4e00\u5f20\u724c");
        }
        if (!canScout(state, currentHand(state, actor.id()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u5f53\u524d\u4e0d\u80fd\u4fa6\u67e5");
        }

        Map<String, Object> hands = mapOf(state.get("hands"));
        List<Map<String, Object>> hand = listOf(hands.get(String.valueOf(actor.id())));
        List<Map<String, Object>> selected = new ArrayList<>();
        for (String cardId : cardIds) {
            Map<String, Object> card = findCard(hand, cardId);
            if (card == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "\u627e\u4e0d\u5230\u8981\u4e22\u5f03\u7684\u724c");
            }
            if (isWildCard(card)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "\u4e07\u80fd\u724c\u4e0d\u80fd\u7528\u4e8e\u4fa6\u67e5");
            }
            selected.add(card);
        }
        hand.removeIf(card -> cardIds.contains(Objects.toString(card.get("id"), "")));
        List<Map<String, Object>> discardPile = listOf(state.get("discardPile"));
        discardPile.addAll(selected);
        hand.add(takeScoutCard(state, "wild_industry", "\u4e07\u80fd\u4ea7\u4e1a\u724c"));
        hand.add(takeScoutCard(state, "wild_location", "\u4e07\u80fd\u5730\u70b9\u724c"));
        hands.put(String.valueOf(actor.id()), hand);
        state.put("hands", hands);
        state.put("discardPile", discardPile);

        appendNotice(state, "玩家" + actor.username() + " 进行了侦查");
        consumeAction(state, actor);
        return state;
    }

    private void consumeAction(Map<String, Object> state, UserSummary actor) {
        Map<String, Object> turn = mapOf(state.get("turn"));
        int actionsRemaining = parseInteger(turn.get("actionsRemaining"), 0) - 1;
        int actionsTaken = parseInteger(turn.get("actionsTaken"), 0) + 1;
        turn.put("actionsRemaining", Math.max(0, actionsRemaining));
        turn.put("actionsTaken", actionsTaken);
        if (actionsRemaining <= 0) {
            turn.put("awaitingEndTurn", true);
        }
        state.put("turn", turn);
        updateMaintenanceFlag(state);
    }

    private void advanceTurn(Map<String, Object> state) {
        List<Object> order = objectList(state.get("turnOrder"));
        int turnIndex = parseInteger(state.get("turnIndex"), 0);
        int nextIndex = (turnIndex + 1) % order.size();
        if (nextIndex == 0) {
            finishRound(state);
            if (Boolean.TRUE.equals(state.get("eraEnding")) || !mapOf(state.get("incomeDebt")).isEmpty()) {
                return;
            }
            order = objectList(state.get("turnOrder"));
            nextIndex = 0;
        }
        long nextPlayerId = asLong(order.get(nextIndex));
        state.put("turnIndex", nextIndex);
        state.put("currentPlayerId", nextPlayerId);
        beginTurn(state, "\u8f6e\u5230 " + playerName(listOf(state.get("players")), nextPlayerId) + " \u884c\u52a8");
    }

    private void enterEraEnding(Map<String, Object> state) {
        Map<String, Object> turn = new LinkedHashMap<>();
        turn.put("actionsAllowed", 0);
        turn.put("actionsRemaining", 0);
        turn.put("actionsTaken", 0);
        turn.put("awaitingEndTurn", false);
        turn.put("eraEnding", true);
        turn.put("startedAtRound", state.get("round"));
        state.put("turn", turn);
        state.put("turnStartSnapshot", null);
        state.put("eraEnding", true);
        appendNotice(state, eraName(Objects.toString(state.get("era"), "")) + "\u7ed3\u675f\uff0c\u8fdb\u5165\u65f6\u4ee3\u7ef4\u62a4");
        updateMaintenanceFlag(state);
    }

    private void finishRound(Map<String, Object> state) {
        int finishedRound = parseInteger(state.get("round"), 1);
        reorderTurnOrderBySpending(state);
        appendNotice(state, "第" + finishedRound + "轮结束，下轮行动顺位为：" + turnOrderNotice(state));
        collectIncome(state);
        state.put("pendingFinishedRound", finishedRound);
        continueIncomeDebtResolution(state);
    }

    private void finishRoundLegacy(Map<String, Object> state) {
        int finishedRound = parseInteger(state.get("round"), 1);
        reorderTurnOrderBySpending(state);
        collectIncome(state);
        appendNotice(state, "第" + finishedRound + "轮结束，下轮行动顺位为：" + turnOrderNotice(state));
        state.put("pendingFinishedRound", finishedRound);
        continueIncomeDebtResolution(state);
    }

    private void beginTurn(Map<String, Object> state, String notice) {
        Map<String, Object> previousTurn = mapOf(state.get("turn"));
        Object previousStartedRound = previousTurn.get("startedAtRound");
        if (!Objects.equals(previousStartedRound, state.get("round"))) {
            appendNotice(state, "第" + parseInteger(state.get("round"), 1) + "轮开始");
        }
        int actions = actionsPerTurn(state);
        Map<String, Object> turn = new LinkedHashMap<>();
        turn.put("actionsAllowed", actions);
        turn.put("actionsRemaining", actions);
        turn.put("actionsTaken", 0);
        turn.put("awaitingEndTurn", false);
        turn.put("startedAtRound", state.get("round"));
        state.put("turn", turn);
        state.put("turnStartSnapshot", snapshotOf(state));
        appendNotice(state, notice);
        updateMaintenanceFlag(state);
    }

    private void beginTurnLegacy(Map<String, Object> state, String notice) {
        Map<String, Object> previousTurn = mapOf(state.get("turn"));
        Object previousStartedRound = previousTurn.get("startedAtRound");
        if (!Objects.equals(previousStartedRound, state.get("round"))) {
            appendNotice(state, "第" + parseInteger(state.get("round"), 1) + "轮开始");
        }
        int actions = actionsPerTurn(state);
        Map<String, Object> turn = new LinkedHashMap<>();
        turn.put("actionsAllowed", actions);
        turn.put("actionsRemaining", actions);
        turn.put("actionsTaken", 0);
        turn.put("awaitingEndTurn", false);
        turn.put("startedAtRound", state.get("round"));
        state.put("turn", turn);
        state.put("turnStartSnapshot", snapshotOf(state));
        appendNotice(state, notice);
        updateMaintenanceFlag(state);
    }

    private String turnOrderNotice(Map<String, Object> state) {
        List<Map<String, Object>> players = listOf(state.get("players"));
        return objectList(state.get("turnOrder")).stream()
                .map(id -> "玩家" + playerName(players, asLong(id)))
                .reduce((left, right) -> left + "、" + right)
                .orElse("");
    }

    private String turnOrderNoticeLegacy(Map<String, Object> state) {
        List<Map<String, Object>> players = listOf(state.get("players"));
        return objectList(state.get("turnOrder")).stream()
                .map(id -> "玩家" + playerName(players, asLong(id)))
                .reduce((left, right) -> left + "、" + right)
                .orElse("");
    }

    private int actionsPerTurn(Map<String, Object> state) {
        return "canal".equals(state.get("era")) && parseInteger(state.get("round"), 1) == 1 ? 1 : 2;
    }

    private Map<String, Object> snapshotOf(Map<String, Object> state) {
        Map<String, Object> snapshot = mapOf(deepCopy(state));
        snapshot.remove("turnStartSnapshot");
        return snapshot;
    }

    private List<Map<String, Object>> playersOf(List<RoomSeat> seats) {
        List<Map<String, Object>> players = new ArrayList<>();
        for (RoomSeat seat : seats) {
            if (!seat.occupied()) {
                continue;
            }
            Map<String, Object> player = new LinkedHashMap<>();
            player.put("seatIndex", seat.seatIndex());
            player.put("userId", seat.userId());
            player.put("username", seat.username());
            player.put("color", "");
            players.add(player);
        }
        return players;
    }

    private void assignRandomPlayerColors(List<Map<String, Object>> players) {
        List<String> colors = new ArrayList<>(PLAYER_COLORS);
        Collections.shuffle(colors);
        for (int index = 0; index < players.size(); index++) {
            players.get(index).put("color", colors.get(index % colors.size()));
        }
    }

    private Map<String, Object> initialPlayerStats(List<Map<String, Object>> players) {
        Map<String, Object> stats = new LinkedHashMap<>();
        for (Map<String, Object> player : players) {
            Map<String, Object> playerStats = new LinkedHashMap<>();
            playerStats.put("money", STARTING_MONEY);
            playerStats.put("incomeLevel", STARTING_INCOME_LEVEL);
            playerStats.put("income", incomeForLevel(STARTING_INCOME_LEVEL));
            playerStats.put("victoryPoints", 0);
            playerStats.put("spentThisRound", 0);
            playerStats.put("estimatedEraEndScore", 0);
            stats.put(String.valueOf(player.get("userId")), playerStats);
        }
        return stats;
    }

    private Map<String, Object> initialDevelopments(List<Map<String, Object>> players) {
        Map<String, Object> developments = new LinkedHashMap<>();
        for (Map<String, Object> player : players) {
            Map<String, Object> playerDevelopments = new LinkedHashMap<>();
            for (String industryType : INDUSTRY_TYPES) {
                playerDevelopments.put(industryType, 0);
            }
            developments.put(String.valueOf(player.get("userId")), playerDevelopments);
        }
        return developments;
    }

    private Map<String, Object> initialPlayerBoards(List<Map<String, Object>> players) {
        Map<String, Object> playerBoards = new LinkedHashMap<>();
        for (Map<String, Object> player : players) {
            Map<String, Object> board = new LinkedHashMap<>();
            for (String industryType : INDUSTRY_TYPES) {
                board.put(industryType, industryBoardTiles(industryType));
            }
            playerBoards.put(String.valueOf(player.get("userId")), board);
        }
        return playerBoards;
    }

    private List<Map<String, Object>> industryBoardTiles(String industryType) {
        if (!BUILDING_TILES.getOrDefault(industryType, List.of()).isEmpty()) {
            return listCopy(BUILDING_TILES.get(industryType));
        }
        List<Map<String, Object>> tiles = new ArrayList<>();
        for (int level = 1; level <= 4; level++) {
            Map<String, Object> tile = new LinkedHashMap<>();
            tile.put("id", industryType + "_level_" + level);
            tile.put("industryType", industryType);
            tile.put("industryName", industryNameCn(industryType));
            tile.put("level", level);
            tile.put("era", level <= 1 ? "canal" : "rail");
            tile.put("cost", INDUSTRY_COSTS.get(industryType));
            tile.put("coal", "coal_mine".equals(industryType) ? Math.min(5, level + 1) : 0);
            tile.put("iron", "iron_works".equals(industryType) ? 4 : 0);
            tile.put("beer", "brewery".equals(industryType) ? (level >= 3 ? 2 : 1) : 0);
            tile.put("coalCost", 0);
            tile.put("ironCost", 0);
            tile.put("saleBeerCost", SELLABLE_INDUSTRIES.contains(industryType) ? 1 : 0);
            tile.put("roadPoints", 1);
            tile.put("canDevelop", true);
            tile.put("flipType", SELLABLE_INDUSTRIES.contains(industryType) ? "sell" : "deplete");
            tile.put("incomeReward", INDUSTRY_INCOME_REWARDS.get(industryType));
            tile.put("victoryPoints", INDUSTRY_VP_REWARDS.get(industryType));
            tiles.add(tile);
        }
        return tiles;
    }

    private Map<String, Object> initialMarket(int playerCount) {
        Map<String, Object> market = new LinkedHashMap<>();
        market.put("coal", new ArrayList<>(INITIAL_COAL_MARKET));
        market.put("iron", new ArrayList<>(INITIAL_IRON_MARKET));
        market.put("beerMerchants", initialBeerMerchants(playerCount));
        return market;
    }

    private Map<String, Object> initialBoard() {
        Map<String, Object> board = new LinkedHashMap<>();
        board.put("industries", new ArrayList<>());
        board.put("links", new ArrayList<>());
        board.put("availableRoutes", routes());
        board.put("buildableCities", new ArrayList<>(citySlotOptions().keySet()));
        board.put("citySlots", citySlotOptions());
        board.put("map", mapSummary());
        board.put("merchantBeer", new LinkedHashMap<>());
        board.put("eraScored", false);
        return board;
    }

    private List<Map<String, Object>> initialBeerMerchants(int playerCount) {
        List<Map<String, Object>> merchants = new ArrayList<>();
        List<Map<String, Object>> source = MAP_MARKETS.isEmpty() ? fallbackMarkets() : MAP_MARKETS;
        List<Map<String, Object>> merchantTiles = merchantTilesForPlayerCount(playerCount);
        Collections.shuffle(merchantTiles);
        int tileIndex = 0;
        for (Map<String, Object> market : source) {
            int availablePlayers = parseInteger(market.get("availablePlayers"), 0);
            String city = Objects.toString(market.get("city"), "");
            int marketCount = Math.max(1, parseInteger(market.get("marketCount"), 1));
            for (int slotIndex = 0; slotIndex < marketCount; slotIndex++) {
                Map<String, Object> merchant = new LinkedHashMap<>();
                merchant.put("id", "merchant_" + city.toLowerCase().replaceAll("[^a-z0-9]+", "_") + "_" + slotIndex);
                merchant.put("city", city);
                merchant.put("slotIndex", slotIndex);
                merchant.put("reward", Objects.toString(market.get("reward"), ""));
                merchant.put("marketOpen", !(playerCount > 0 && availablePlayers > 0 && playerCount < availablePlayers));
                if (Boolean.TRUE.equals(merchant.get("marketOpen"))) {
                    Map<String, Object> merchantTile = tileIndex < merchantTiles.size() ? merchantTiles.get(tileIndex) : new LinkedHashMap<>();
                    tileIndex++;
                    merchant.put("merchantTileId", Objects.toString(merchantTile.get("id"), ""));
                    merchant.put("acceptedIndustryTypes", stringList(merchantTile.get("industryTypes")));
                    merchant.put("wild", Boolean.TRUE.equals(merchantTile.get("wild")));
                    merchant.put("blank", Boolean.TRUE.equals(merchantTile.get("blank")));
                    merchant.put("providesBeer", !Boolean.FALSE.equals(merchantTile.getOrDefault("providesBeer", true)));
                    merchant.put("beer", Boolean.TRUE.equals(merchant.get("providesBeer")) ? 1 : 0);
                    merchant.put("used", false);
                } else {
                    merchant.put("merchantTileId", "");
                    merchant.put("acceptedIndustryTypes", List.of());
                    merchant.put("wild", false);
                    merchant.put("blank", true);
                    merchant.put("providesBeer", false);
                    merchant.put("beer", 0);
                    merchant.put("used", true);
                }
                merchants.add(merchant);
            }
        }
        return merchants;
    }

    private List<Map<String, Object>> initialScoutPool() {
        List<Map<String, Object>> scoutPool = new ArrayList<>();
        for (Map<String, Object> entry : staticListOf(CARD_CONFIG.get("scout_pool"))) {
            String type = Objects.toString(entry.get("type"), "");
            int amount = Math.max(0, parseInteger(entry.get("amount"), 0));
            for (int copy = 0; copy < amount; copy++) {
                if ("location".equals(type) || "place".equals(type)) {
                    scoutPool.add(wildCard("wild_location", "\u4e07\u80fd\u5730\u70b9\u724c"));
                } else if ("industry".equals(type)) {
                    scoutPool.add(wildCard("wild_industry", "\u4e07\u80fd\u4ea7\u4e1a\u724c"));
                }
            }
        }
        if (scoutPool.isEmpty()) {
            for (int copy = 0; copy < 4; copy++) {
                scoutPool.add(wildCard("wild_location", "\u4e07\u80fd\u5730\u70b9\u724c"));
                scoutPool.add(wildCard("wild_industry", "\u4e07\u80fd\u4ea7\u4e1a\u724c"));
            }
        }
        return scoutPool;
    }

    private List<Map<String, Object>> merchantTilesForPlayerCount(int playerCount) {
        List<Map<String, Object>> tiles = new ArrayList<>();
        for (Map<String, Object> rawTile : MERCHANT_TILE_CONFIG) {
            Map<String, Object> info = staticMapOf(rawTile.get("info"));
            List<Integer> availablePlayers = staticObjectList(info.get("available_players")).stream()
                    .map(value -> parseStaticInteger(value, 0))
                    .filter(value -> value > 0)
                    .toList();
            if (!availablePlayers.isEmpty() && !availablePlayers.contains(playerCount)) {
                continue;
            }
            Map<String, Object> tile = new LinkedHashMap<>();
            tile.put("id", Objects.toString(rawTile.get("id"), ""));
            tile.put("industryTypes", configuredIndustryTypes(info.get("factory")));
            tile.put("wild", Boolean.TRUE.equals(info.get("is_wild")));
            tile.put("blank", Boolean.TRUE.equals(info.get("is_blank")));
            tile.put("providesBeer", !Boolean.FALSE.equals(info.get("provides_beer")));
            tiles.add(tile);
        }
        return tiles;
    }

    private Map<String, Object> mapSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("loadedFromFile", !BIRMINGHAM_MAP.isEmpty());
        summary.put("markets", MAP_MARKETS.isEmpty() ? fallbackMarkets() : MAP_MARKETS);
        summary.put("cityNames", CITY_NAME_MAPPING);
        summary.put("industryNames", industryNameSummary());
        summary.put("cities", new ArrayList<>(MAP_CITY_METADATA.values()));
        return summary;
    }

    private Map<String, String> industryNameSummary() {
        Map<String, String> names = new LinkedHashMap<>();
        for (String industryType : INDUSTRY_TYPES) {
            names.put(industryType, industryNameCn(industryType));
        }
        return names;
    }

    private boolean canMaintainEra(Map<String, Object> state) {
        return Boolean.TRUE.equals(state.get("eraEnding")) && mapOf(state.get("incomeDebt")).isEmpty();
    }

    private void updateMaintenanceFlag(Map<String, Object> state) {
        if (Boolean.TRUE.equals(state.get("_candidateSimulation"))) {
            return;
        }
        refreshBrassDerivedState(state);
        boolean canMaintain = canMaintainEra(state);
        state.put("canMaintainEra", canMaintain);
        state.put("eraEnding", canMaintain && "playing".equals(state.get("phase")));
        state.put("availableActions", availableActions(state));
    }

    private void refreshBrassDerivedState(Map<String, Object> state) {
        Map<String, Object> board = mapOf(state.get("board"));
        if (board.isEmpty()) {
            return;
        }
        List<Map<String, Object>> industries = listOf(board.get("industries"));
        List<Map<String, Object>> links = listOf(board.get("links"));
        refreshIndustryDisplayFields(industries);
        for (Map<String, Object> link : links) {
            int score = routeVictoryPoints(
                    industries,
                    Objects.toString(link.get("from"), ""),
                    Objects.toString(link.get("to"), "")
            );
            link.put("builtLinkId", link.get("id"));
            link.put("linkId", routeKey(Objects.toString(link.get("from"), ""), Objects.toString(link.get("to"), "")));
            link.put("ownerPlayerId", link.get("ownerId"));
            link.put("src", link.get("from"));
            link.put("dst", link.get("to"));
            link.put("eraBuilt", link.get("type"));
            link.put("currentScore", score);
            link.put("score", score);
        }
        board.put("industries", industries);
        board.put("links", links);
        state.put("board", board);
        refreshEstimatedEraEndScores(state, industries, links);
    }

    private void refreshIndustryDisplayFields(List<Map<String, Object>> industries) {
        for (Map<String, Object> tile : industries) {
            String industryType = Objects.toString(tile.get("industryType"), "");
            tile.put("builtIndustryId", tile.get("id"));
            tile.put("ownerPlayerId", tile.get("ownerId"));
            tile.put("cityId", tile.get("city"));
            tile.put("industryId", industryType);
            tile.put("industryName", industryNameCn(industryType));
            tile.put("vp", parseInteger(tile.get("victoryPoints"), 0));
            tile.put("roadPoint", parseInteger(tile.get("roadPoints"), 0));
            tile.put("saleWine", parseInteger(tile.get("saleBeerCost"), 0));
            tile.put("remainingResource", remainingIndustryResource(tile));
        }
    }

    private int remainingIndustryResource(Map<String, Object> tile) {
        String industryType = Objects.toString(tile.get("industryType"), "");
        if ("coal_mine".equals(industryType)) {
            return parseInteger(tile.get("coal"), 0);
        }
        if ("iron_works".equals(industryType)) {
            return parseInteger(tile.get("iron"), 0);
        }
        if ("brewery".equals(industryType)) {
            return parseInteger(tile.get("beer"), 0);
        }
        return 0;
    }

    private void refreshEstimatedEraEndScores(Map<String, Object> state, List<Map<String, Object>> industries,
                                              List<Map<String, Object>> links) {
        String era = Objects.toString(state.get("era"), "");
        Map<String, Object> statsByPlayer = mapOf(state.get("playerStats"));
        for (Map<String, Object> player : listOf(state.get("players"))) {
            long playerId = asLong(player.get("userId"));
            int score = industries.stream()
                    .filter(tile -> asLong(tile.get("ownerId")) == playerId)
                    .filter(tile -> era.equals(Objects.toString(tile.get("era"), "")))
                    .filter(tile -> Boolean.TRUE.equals(tile.get("flipped")))
                    .mapToInt(tile -> parseInteger(tile.get("victoryPoints"), 0))
                    .sum();
            score += links.stream()
                    .filter(link -> asLong(link.get("ownerId")) == playerId)
                    .filter(link -> eraLinkType(era).equals(Objects.toString(link.get("type"), "")))
                    .mapToInt(link -> parseInteger(link.get("currentScore"), 0))
                    .sum();
            Map<String, Object> stats = mapOf(statsByPlayer.get(String.valueOf(playerId)));
            if (!stats.isEmpty()) {
                stats.put("estimatedEraEndScore", score);
                statsByPlayer.put(String.valueOf(playerId), stats);
            }
        }
        state.put("playerStats", statsByPlayer);
    }

    private Map<String, Object> availableActions(Map<String, Object> state) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!"playing".equals(state.get("phase"))) {
            result.put("currentPlayerId", state.get("currentPlayerId"));
            result.put("actions", List.of());
            return result;
        }

        long playerId = asLong(state.get("currentPlayerId"));
        List<Map<String, Object>> hand = currentHand(state, playerId);
        boolean hasOneCard = !hand.isEmpty();
        boolean maintenanceReady = canMaintainEra(state);
        List<String> actions = new ArrayList<>();
        Map<String, Object> turn = mapOf(state.get("turn"));
        Map<String, Object> incomeDebt = mapOf(state.get("incomeDebt"));
        if (!incomeDebt.isEmpty()) {
            actions.add("resolve_income_debt");
            result.put("currentPlayerId", playerId);
            result.put("actions", actions);
            result.put("incomeDebt", incomeDebt);
            result.put("incomeDebtTiles", ownedMapIndustries(state, playerId));
            return result;
        }
        if (Boolean.TRUE.equals(turn.get("awaitingEndTurn"))) {
            actions.add("restart_turn");
            actions.add("end_turn");
            result.put("currentPlayerId", playerId);
            result.put("actions", actions);
            result.put("handSize", hand.size());
            result.put("buildOptions", List.of());
            result.put("sellTiles", List.of());
            result.put("networkRoutes", List.of());
            result.put("developIndustries", List.of());
            result.put("resourceSources", new LinkedHashMap<>());
            return result;
        }

        if (!mapOf(state.get("turnStartSnapshot")).isEmpty()
                && parseInteger(turn.get("actionsTaken"), 0) > 0) {
            actions.add("restart_turn");
        }
        boolean sellInProgress = Boolean.TRUE.equals(turn.get("sellInProgress"));
        if (sellInProgress) {
            List<Map<String, Object>> sellTiles = availableSellTiles(state, playerId);
            if (!sellTiles.isEmpty()) {
                actions.add("sell");
            }
            if (parseInteger(turn.get("sellCount"), 0) > 0) {
                actions.add("end_sell");
            }
            result.put("currentPlayerId", playerId);
            result.put("actions", actions);
            result.put("requiresCard", List.of("build", "network", "develop", "loan", "skip"));
            result.put("maintenanceReady", false);
            result.put("handSize", hand.size());
            result.put("sellInProgress", true);
            result.put("sellCount", parseInteger(turn.get("sellCount"), 0));
            result.put("buildOptions", List.of());
            result.put("sellTiles", sellTiles);
            result.put("networkRoutes", List.of());
            result.put("networkRoutePairs", List.of());
            result.put("remainingLinks", Math.max(0, MAX_PLAYER_LINKS - playerLinkCount(listOf(mapOf(state.get("board")).get("links")), playerId)));
            result.put("developIndustries", List.of());
            result.put("resourceSources", availableResourceSources(state));
            return result;
        }
        List<Map<String, Object>> buildOptions = maintenanceReady ? List.of() : availableBuildOptions(state, playerId, hand);
        List<Map<String, Object>> sellTiles = maintenanceReady ? List.of() : availableSellTiles(state, playerId);
        List<Map<String, Object>> networkRoutes = maintenanceReady ? List.of() : availableNetworkRoutes(state, playerId);
        List<Map<String, Object>> networkRoutePairs = maintenanceReady ? List.of() : availableNetworkRoutePairs(state, playerId);
        List<Map<String, Object>> developIndustries = maintenanceReady ? List.of() : availableDevelopIndustries(state, playerId);
        if (maintenanceReady) {
            actions.add("maintain_era");
        } else {
            if (hasOneCard) {
                actions.add("skip");
                if (canLoan(state, playerId)) {
                    actions.add("loan");
                }
            }
            if (!buildOptions.isEmpty()) {
                actions.add("build");
            }
            if (hasOneCard && !sellTiles.isEmpty()) {
                actions.add("sell");
            }
            if (hasOneCard && !networkRoutes.isEmpty()) {
                actions.add("network");
            }
            if (hasOneCard && !developIndustries.isEmpty()) {
                actions.add("develop");
            }
            if (canScout(state, hand)) {
                actions.add("scout");
            }
        }

        result.put("currentPlayerId", playerId);
        result.put("actions", actions);
        result.put("requiresCard", List.of("build", "sell", "network", "develop", "loan", "skip"));
        result.put("maintenanceReady", maintenanceReady);
        result.put("handSize", hand.size());
        result.put("buildOptions", buildOptions);
        result.put("sellTiles", sellTiles);
        result.put("networkRoutes", networkRoutes);
        result.put("networkRoutePairs", networkRoutePairs);
        result.put("remainingLinks", Math.max(0, MAX_PLAYER_LINKS - playerLinkCount(listOf(mapOf(state.get("board")).get("links")), playerId)));
        result.put("developIndustries", developIndustries);
        result.put("resourceSources", availableResourceSources(state));
        return result;
    }

    private List<Map<String, Object>> currentHand(Map<String, Object> state, long playerId) {
        return listOf(mapOf(state.get("hands")).get(String.valueOf(playerId)));
    }

    private boolean canBuildAnything(Map<String, Object> state, long playerId, List<Map<String, Object>> hand) {
        return !availableBuildOptions(state, playerId, hand).isEmpty();
    }

    private Map<String, Object> nextBuildableBoardTile(Map<String, Object> state, long playerId, String industryType) {
        try {
            return nextAvailableBoardTile(state, playerId, industryType);
        } catch (ApiException ignored) {
            return new LinkedHashMap<>();
        }
    }

    private boolean canSellAnything(Map<String, Object> state, long playerId, boolean hasOneCard) {
        if (!hasOneCard) {
            return false;
        }
        return !availableSellTiles(state, playerId).isEmpty();
    }

    private boolean canNetworkAnything(Map<String, Object> state, long playerId, boolean hasOneCard) {
        if (!hasOneCard) {
            return false;
        }
        return !availableNetworkRoutes(state, playerId).isEmpty();
    }

    private boolean canDevelopAnything(Map<String, Object> state, long playerId, boolean hasOneCard) {
        if (!hasOneCard) {
            return false;
        }
        for (String industryType : INDUSTRY_TYPES) {
            if (!lowestDevelopableBoardTile(state, playerId, industryType).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean canLoan(Map<String, Object> state, long playerId) {
        Map<String, Object> stats = mapOf(mapOf(state.get("playerStats")).get(String.valueOf(playerId)));
        int incomeLevel = parseInteger(stats.get("incomeLevel"), STARTING_INCOME_LEVEL);
        return incomeLevel >= 3 && INCOME_LEVEL_AFTER_LOAN.containsKey(incomeLevel);
    }

    private boolean canScout(Map<String, Object> state, List<Map<String, Object>> hand) {
        long nonWildCards = hand.stream().filter(card -> !isWildCard(card)).count();
        if (nonWildCards < 3) {
            return false;
        }
        List<Map<String, Object>> scoutPool = listOf(state.get("scoutPool"));
        return scoutPool.stream().anyMatch(card -> "wild_industry".equals(card.get("key")))
                && scoutPool.stream().anyMatch(card -> "wild_location".equals(card.get("key")));
    }

    private List<Map<String, Object>> availableBuildOptions(Map<String, Object> state, long playerId,
                                                            List<Map<String, Object>> hand) {
        if (hand.isEmpty()) {
            return List.of();
        }
        Map<String, Object> board = mapOf(state.get("board"));
        List<Map<String, Object>> industries = listOf(board.get("industries"));
        List<Map<String, Object>> options = new ArrayList<>();
        for (Map.Entry<String, List<List<String>>> entry : citySlotOptions().entrySet()) {
            String city = entry.getKey();
            for (int slotIndex = 0; slotIndex < entry.getValue().size(); slotIndex++) {
                List<String> slot = entry.getValue().get(slotIndex);
                for (String industryType : slot) {
                    int availableSlotIndex = buildSlotIndexForOption(state, industries, playerId, city, industryType, slotIndex);
                    if (!INDUSTRY_TYPES.contains(industryType) || availableSlotIndex < 0) {
                        continue;
                    }
                    Map<String, Object> boardTile = nextBuildableBoardTile(state, playerId, industryType);
                    if (boardTile.isEmpty()) {
                        continue;
                    }
                    Map<String, Object> ownCoverable = coverableOwnFlippedTile(
                            industries, playerId, city, industryType, availableSlotIndex,
                            parseInteger(boardTile.get("level"), 0)
                    );
                    if ("canal".equals(state.get("era"))
                            && playerAlreadyBuiltInCity(industries, playerId, city)
                            && ownCoverable.isEmpty()) {
                        continue;
                    }
                    List<String> cardIds = hand.stream()
                            .filter(card -> cardCanBuild(card, city, industryType, board, playerId))
                            .map(card -> Objects.toString(card.get("id"), ""))
                            .filter(id -> !id.isBlank())
                            .toList();
                    if (!cardIds.isEmpty()
                            && !buildOptionExecutable(state, playerId, cardIds.getFirst(), city, industryType, availableSlotIndex)) {
                        cardIds = List.of();
                    }
                    if (!cardIds.isEmpty()) {
                        Map<String, Object> option = new LinkedHashMap<>();
                        option.put("city", city);
                        option.put("industryType", industryType);
                option.put("industryName", industryNameCn(industryType));
                        option.put("slotIndex", availableSlotIndex);
                        option.put("coversOpponent", !slotAvailable(industries, city, industryType, availableSlotIndex)
                                && ownCoverable.isEmpty());
                        option.put("coversOwn", !ownCoverable.isEmpty());
                        option.put("level", boardTile.get("level"));
                        option.put("cost", boardTile.get("cost"));
                        option.put("coalCost", boardTile.get("coalCost"));
                        option.put("ironCost", boardTile.get("ironCost"));
                        option.put("coalSources", coalSourceItems(board, List.of(city)));
                        option.put("ironSources", ironSourceItems(industries));
                        option.put("cardIds", cardIds);
                        options.add(option);
                    }
                }
            }
        }
        return options;
    }

    private int buildSlotIndexForOption(Map<String, Object> state, List<Map<String, Object>> industries, long playerId,
                                        String city, String industryType) {
        int nextLevel = parseInteger(nextBuildableBoardTile(state, playerId, industryType).get("level"), 0);
        int dedicatedSlotIndex = firstBuildableDedicatedSlot(state, industries, playerId, city, industryType);
        if (dedicatedSlotIndex >= 0) {
            return dedicatedSlotIndex;
        }
        int slotIndex = firstAvailableSlot(industries, city, industryType);
        if (slotIndex >= 0) {
            return slotIndex;
        }
        Map<String, Object> ownCoverable = coverableOwnFlippedTile(industries, playerId, city, industryType, nextLevel);
        if (!ownCoverable.isEmpty()) {
            return parseInteger(ownCoverable.get("slotIndex"), -1);
        }
        if (!List.of("coal_mine", "iron_works").contains(industryType)) {
            return -1;
        }
        Map<String, Object> coverable = coverableOpponentResourceTile(state, playerId, city, industryType);
        return coverable.isEmpty() ? -1 : parseInteger(coverable.get("slotIndex"), -1);
    }

    private int buildSlotIndexForOption(Map<String, Object> state, List<Map<String, Object>> industries, long playerId,
                                        String city, String industryType, int slotIndex) {
        int nextLevel = parseInteger(nextBuildableBoardTile(state, playerId, industryType).get("level"), 0);
        if (!slotHasDedicatedIndustry(city, industryType, slotIndex)
                && firstBuildableDedicatedSlot(state, industries, playerId, city, industryType) >= 0) {
            return -1;
        }
        if (slotAvailable(industries, city, industryType, slotIndex)) {
            return slotIndex;
        }
        if (!coverableOwnFlippedTile(industries, playerId, city, industryType, slotIndex, nextLevel).isEmpty()) {
            return slotIndex;
        }
        if (!List.of("coal_mine", "iron_works").contains(industryType)) {
            return -1;
        }
        Map<String, Object> coverable = coverableOpponentResourceTile(state, playerId, city, industryType, slotIndex);
        return coverable.isEmpty() ? -1 : slotIndex;
    }

    private List<Map<String, Object>> availableSellTiles(Map<String, Object> state, long playerId) {
        return listOf(mapOf(state.get("board")).get("industries")).stream()
                .filter(tile -> asLong(tile.get("ownerId")) == playerId
                        && !Boolean.TRUE.equals(tile.get("flipped"))
                        && "sell".equals(tile.get("flipType"))
                        && sellTileExecutable(state, playerId, tile))
                .map(tile -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    int beerRequired = saleBeerRequired(tile);
                    item.put("id", tile.get("id"));
                    item.put("city", tile.get("city"));
                    item.put("industryType", tile.get("industryType"));
                    item.put("industryName", tile.get("industryName"));
                    item.put("saleBeerCost", tile.get("saleBeerCost"));
                    item.put("beerSources", beerRequired == 0 ? List.of() : beerSourceItems(mapOf(state.get("board")), Objects.toString(tile.get("city"), ""), playerId).stream()
                            .filter(source -> parseInteger(source.get("amount"), 0) >= beerRequired)
                            .toList());
                    item.put("merchantSources", merchantSourceItems(state, tile));
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> availableNetworkRoutes(Map<String, Object> state, long playerId) {
        Map<String, Object> board = mapOf(state.get("board"));
        List<Map<String, Object>> links = listOf(board.get("links"));
        if (playerLinkCount(links, playerId) >= MAX_PLAYER_LINKS) {
            return List.of();
        }
        String linkType = eraLinkType(Objects.toString(state.get("era"), "canal"));
        List<Map<String, Object>> options = new ArrayList<>();
        int money = playerMoney(state, playerId);
        for (Map<String, Object> route : routes()) {
            String from = Objects.toString(route.get("from"), "");
            String to = Objects.toString(route.get("to"), "");
            Map<String, Object> hypotheticalBoard = boardWithHypotheticalLinks(board, List.of(routeLink(from, to, linkType)));
            int minimumCost = "rail".equals(linkType)
                    ? 5 + minimumCoalCost(state, hypotheticalBoard, List.of(from, to), 1)
                    : 3;
            if (Boolean.TRUE.equals(route.get(linkType))
                    && !routeOccupied(links, from, to, linkType)
                    && touchesPlayerNetwork(board, playerId, from, to)
                    && money >= minimumCost
                    && networkRoutesExecutable(state, playerId, List.of(route))) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("from", from);
                item.put("to", to);
                item.put("type", linkType);
                item.put("coalCost", "rail".equals(linkType) ? 1 : 0);
                item.put("moneyCost", "rail".equals(linkType) ? 5 : 3);
                if ("rail".equals(linkType)) {
                    item.put("coalSources", coalSourceItems(hypotheticalBoard, List.of(from, to)));
                }
                options.add(item);
            }
        }
        return options;
    }

    private List<Map<String, Object>> availableNetworkRoutePairs(Map<String, Object> state, long playerId) {
        if (!"rail".equals(Objects.toString(state.get("era"), ""))) {
            return List.of();
        }
        Map<String, Object> board = mapOf(state.get("board"));
        List<Map<String, Object>> links = listOf(board.get("links"));
        if (playerLinkCount(links, playerId) + 2 > MAX_PLAYER_LINKS) {
            return List.of();
        }
        List<Map<String, Object>> pairs = new ArrayList<>();
        List<Map<String, Object>> railRoutes = routes().stream()
                .filter(route -> Boolean.TRUE.equals(route.get("rail")))
                .toList();
        for (Map<String, Object> firstRoute : railRoutes) {
            String firstFrom = Objects.toString(firstRoute.get("from"), "");
            String firstTo = Objects.toString(firstRoute.get("to"), "");
            if (routeOccupied(links, firstFrom, firstTo, "rail") || !touchesPlayerNetwork(board, playerId, firstFrom, firstTo)) {
                continue;
            }
            Map<String, Object> afterFirst = boardWithHypotheticalLinks(board, List.of(routeLink(firstFrom, firstTo, "rail")));
            for (Map<String, Object> secondRoute : railRoutes) {
                String secondFrom = Objects.toString(secondRoute.get("from"), "");
                String secondTo = Objects.toString(secondRoute.get("to"), "");
                if (sameRoute(firstFrom, firstTo, secondFrom, secondTo)
                        || routeOccupied(listOf(afterFirst.get("links")), secondFrom, secondTo, "rail")
                        || !touchesPlayerNetwork(afterFirst, playerId, secondFrom, secondTo)) {
                    continue;
                }
                Map<String, Object> afterBoth = boardWithHypotheticalLinks(board, List.of(
                        routeLink(firstFrom, firstTo, "rail"),
                        routeLink(secondFrom, secondTo, "rail")
                ));
                List<String> destinations = List.of(firstFrom, firstTo, secondFrom, secondTo);
                if ((coalSourceItems(afterFirst, List.of(firstFrom, firstTo)).isEmpty()
                        && !coalMarketReachable(afterFirst, List.of(firstFrom, firstTo)))
                        || playerMoney(state, playerId) < 15
                        || beerSourceItemsToAny(afterBoth, destinations, playerId).isEmpty()) {
                    continue;
                }
                List<Map<String, Object>> routeItems = new ArrayList<>();
                routeItems.add(networkPairRouteItem(afterFirst, firstFrom, firstTo));
                routeItems.add(networkPairRouteItem(afterBoth, secondFrom, secondTo));
                Map<String, Object> pair = new LinkedHashMap<>();
                pair.put("routes", routeItems);
                pair.put("type", "rail");
                pair.put("moneyCost", 15);
                pair.put("coalCost", 2);
                pair.put("beerCost", 1);
                pair.put("beerSources", beerSourceItemsToAny(afterBoth, destinations, playerId));
                if (networkRoutesExecutable(state, playerId, List.of(firstRoute, secondRoute))) {
                    pairs.add(pair);
                }
            }
        }
        return pairs;
    }

    private Map<String, Object> networkPairRouteItem(Map<String, Object> board, String from, String to) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("from", from);
        item.put("to", to);
        item.put("coalSources", coalSourceItems(board, List.of(from, to)));
        return item;
    }

    private Map<String, Object> boardWithHypotheticalLinks(Map<String, Object> board, List<Map<String, Object>> newLinks) {
        Map<String, Object> copy = mapOf(deepCopy(board));
        List<Map<String, Object>> links = listOf(copy.get("links"));
        links.addAll(newLinks);
        copy.put("links", links);
        return copy;
    }

    private boolean buildOptionExecutable(Map<String, Object> state, long playerId, String cardId, String city,
                                          String industryType, int slotIndex) {
        Map<String, Object> boardTile = nextBuildableBoardTile(state, playerId, industryType);
        if (boardTile.isEmpty() || playerMoney(state, playerId) < parseInteger(boardTile.get("cost"), 0)) {
            return false;
        }
        Map<String, Object> copy = mapOf(deepCopy(state));
        copy.put("_candidateSimulation", true);
        try {
            buildAction(copy, simulationActor(copy, playerId), Map.of(
                    "cardId", cardId,
                    "city", city,
                    "industryType", industryType,
                    "slotIndex", slotIndex
            ));
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean sellTileExecutable(Map<String, Object> state, long playerId, Map<String, Object> tile) {
        Map<String, Object> basePayload = new LinkedHashMap<>();
        basePayload.put("tileId", Objects.toString(tile.get("id"), ""));
        if (!Boolean.TRUE.equals(mapOf(state.get("turn")).get("sellInProgress"))) {
            List<Map<String, Object>> hand = currentHand(state, playerId);
            if (hand.isEmpty()) {
                return false;
            }
            basePayload.put("cardId", Objects.toString(hand.getFirst().get("id"), ""));
        }
        if (sellPayloadExecutable(state, playerId, basePayload)) {
            return true;
        }
        for (Map<String, Object> merchant : merchantSourceItems(state, tile)) {
            Map<String, Object> payload = new LinkedHashMap<>(basePayload);
            payload.put("merchantId", Objects.toString(merchant.get("id"), ""));
            if (sellPayloadExecutable(state, playerId, payload)) {
                return true;
            }
        }
        return false;
    }

    private boolean sellPayloadExecutable(Map<String, Object> state, long playerId, Map<String, Object> payload) {
        Map<String, Object> copy = mapOf(deepCopy(state));
        copy.put("_candidateSimulation", true);
        try {
            sellAction(copy, simulationActor(copy, playerId), payload);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean networkRoutesExecutable(Map<String, Object> state, long playerId, List<Map<String, Object>> routes) {
        List<Map<String, Object>> hand = currentHand(state, playerId);
        if (hand.isEmpty()) {
            return false;
        }
        List<Map<String, Object>> routeSpecs = routes.stream()
                .map(route -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("from", Objects.toString(route.get("from"), ""));
                    item.put("to", Objects.toString(route.get("to"), ""));
                    return item;
                })
                .toList();
        Map<String, Object> copy = mapOf(deepCopy(state));
        copy.put("_candidateSimulation", true);
        try {
            networkAction(copy, simulationActor(copy, playerId), Map.of(
                    "cardId", Objects.toString(hand.getFirst().get("id"), ""),
                    "routes", routeSpecs
            ));
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private int minimumCoalCost(Map<String, Object> state, Map<String, Object> board, List<String> destinations, int amount) {
        int freeCoal = coalSourceItems(board, destinations).stream()
                .mapToInt(source -> parseInteger(source.get("amount"), 0))
                .sum();
        int remaining = Math.max(0, amount - freeCoal);
        if (remaining == 0) {
            return 0;
        }
        List<Object> marketCoal = objectList(mapOf(state.get("market")).get("coal"));
        int cost = 0;
        for (int index = 0; index < remaining; index++) {
            cost += index < marketCoal.size() ? parseInteger(marketCoal.get(index), 0) : DISTANT_COAL_PRICE;
        }
        return cost;
    }

    private UserSummary simulationActor(Map<String, Object> state, long playerId) {
        return new UserSummary(playerId, playerName(listOf(state.get("players")), playerId));
    }

    private Map<String, Object> routeLink(String from, String to, String type) {
        Map<String, Object> link = new LinkedHashMap<>();
        link.put("from", normalizedRouteEnd(from, to).get(0));
        link.put("to", normalizedRouteEnd(from, to).get(1));
        link.put("type", type);
        return link;
    }

    private long playerLinkCount(List<Map<String, Object>> links, long playerId) {
        return links.stream().filter(link -> asLong(link.get("ownerId")) == playerId).count();
    }

    private List<Map<String, Object>> availableDevelopIndustries(Map<String, Object> state, long playerId) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (String industryType : INDUSTRY_TYPES) {
            Map<String, Object> tile = lowestDevelopableBoardTile(state, playerId, industryType);
            if (!tile.isEmpty()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("industryType", industryType);
                item.put("industryName", industryNameCn(industryType));
                item.put("nextRemovedLevel", tile.get("level"));
                item.put("ironCost", 1);
                item.put("ironSources", ironSourceItems(listOf(mapOf(state.get("board")).get("industries"))));
                options.add(item);
            }
        }
        return options;
    }

    private Map<String, Object> availableResourceSources(Map<String, Object> state) {
        List<Map<String, Object>> industries = listOf(mapOf(state.get("board")).get("industries"));
        Map<String, Object> sources = new LinkedHashMap<>();
        sources.put("coal", resourceSourceItems(industries, "coal_mine", "coal"));
        sources.put("iron", resourceSourceItems(industries, "iron_works", "iron"));
        sources.put("beer", resourceSourceItems(industries, "brewery", "beer"));
        sources.put("marketCoal", objectList(mapOf(state.get("market")).get("coal")).size());
        sources.put("marketIron", objectList(mapOf(state.get("market")).get("iron")).size());
        return sources;
    }

    private List<Map<String, Object>> resourceSourceItems(List<Map<String, Object>> industries, String industryType,
                                                          String resourceField) {
        return industries.stream()
                .filter(tile -> industryType.equals(tile.get("industryType"))
                        && isDepleteTile(tile)
                        && parseInteger(tile.get(resourceField), 0) > 0)
                .map(tile -> resourceSourceItem(tile, resourceField))
                .toList();
    }

    private List<Map<String, Object>> coalSourceItems(Map<String, Object> board, List<String> destinations) {
        return listOf(board.get("industries")).stream()
                .filter(tile -> "coal_mine".equals(tile.get("industryType"))
                        && isDepleteTile(tile)
                        && parseInteger(tile.get("coal"), 0) > 0
                        && coalSourceReachable(board, tile, destinations))
                .map(tile -> resourceSourceItem(tile, "coal"))
                .toList();
    }

    private List<Map<String, Object>> ironSourceItems(List<Map<String, Object>> industries) {
        return resourceSourceItems(industries, "iron_works", "iron");
    }

    private List<Map<String, Object>> beerSourceItems(Map<String, Object> board, String saleCity, long actorId) {
        return listOf(board.get("industries")).stream()
                .filter(tile -> "brewery".equals(tile.get("industryType"))
                        && isDepleteTile(tile)
                        && parseInteger(tile.get("beer"), 0) > 0
                        && beerSourceReachable(board, tile, saleCity, actorId))
                .map(tile -> resourceSourceItem(tile, "beer"))
                .toList();
    }

    private List<Map<String, Object>> beerSourceItemsToAny(Map<String, Object> board, List<String> destinations, long actorId) {
        return listOf(board.get("industries")).stream()
                .filter(tile -> "brewery".equals(tile.get("industryType"))
                        && isDepleteTile(tile)
                        && parseInteger(tile.get("beer"), 0) > 0
                        && beerSourceReachableToAny(board, tile, destinations, actorId))
                .map(tile -> resourceSourceItem(tile, "beer"))
                .toList();
    }

    private List<Map<String, Object>> merchantSourceItems(Map<String, Object> state, Map<String, Object> saleTile) {
        Map<String, Object> board = mapOf(state.get("board"));
        String saleCity = Objects.toString(saleTile.get("city"), "");
        String industryType = Objects.toString(saleTile.get("industryType"), "");
        int beerRequired = saleBeerRequired(saleTile);
        return listOf(mapOf(state.get("market")).get("beerMerchants")).stream()
                .filter(merchant -> (beerRequired == 0 || parseInteger(merchant.get("beer"), 0) >= beerRequired)
                        && merchantAcceptsIndustry(merchant, industryType)
                        && citiesConnected(board, saleCity, Objects.toString(merchant.get("city"), "")))
                .map(merchant -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", merchant.get("id"));
                    item.put("city", merchant.get("city"));
                    item.put("reward", merchant.get("reward"));
                    item.put("beer", merchant.get("beer"));
                    item.put("acceptedIndustryTypes", merchant.get("acceptedIndustryTypes"));
                    return item;
                })
                .toList();
    }

    private int saleBeerRequired(Map<String, Object> tile) {
        return tile.containsKey("saleBeerCost")
                ? Math.max(0, parseInteger(tile.get("saleBeerCost"), 0))
                : 1;
    }

    private boolean isDepleteTile(Map<String, Object> tile) {
        if (tile.containsKey("flipType")) {
            return "deplete".equals(tile.get("flipType"));
        }
        return List.of("coal_mine", "iron_works", "brewery").contains(Objects.toString(tile.get("industryType"), ""));
    }

    private Map<String, Object> resourceSourceItem(Map<String, Object> tile, String resourceField) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", tile.get("id"));
        item.put("industryType", tile.get("industryType"));
        item.put("resourceType", resourceField);
        item.put("ownerId", tile.get("ownerId"));
        item.put("ownerName", tile.get("ownerName"));
        item.put("city", tile.get("city"));
        item.put("amount", tile.get(resourceField));
        return item;
    }

    private void scoreCurrentEra(Map<String, Object> state) {
        String era = Objects.toString(state.get("era"), "");
        Map<String, Object> board = mapOf(state.get("board"));
        List<Map<String, Object>> links = listOf(board.get("links"));
        List<Map<String, Object>> industries = listOf(board.get("industries"));
        Map<String, Object> statsByPlayer = mapOf(state.get("playerStats"));
        List<Map<String, Object>> eraScores = listOf(state.get("eraScores"));
        List<Map<String, Object>> currentEraScores = new ArrayList<>();

        for (Map<String, Object> link : links) {
            if (!eraLinkType(era).equals(link.get("type")) || Boolean.TRUE.equals(link.get("scored"))) {
                continue;
            }
            int score = routeVictoryPoints(industries, Objects.toString(link.get("from"), ""), Objects.toString(link.get("to"), ""));
            long ownerId = asLong(link.get("ownerId"));
            Map<String, Object> stats = mapOf(statsByPlayer.get(String.valueOf(ownerId)));
            stats.put("victoryPoints", parseInteger(stats.get("victoryPoints"), 0) + score);
            statsByPlayer.put(String.valueOf(ownerId), stats);
            link.put("scored", true);
            link.put("score", score);
            Map<String, Object> scoreItem = new LinkedHashMap<>();
            scoreItem.put("era", era);
            scoreItem.put("ownerId", ownerId);
            scoreItem.put("ownerName", link.get("ownerName"));
            scoreItem.put("type", "link");
            scoreItem.put("summary", link.get("from") + " - " + link.get("to"));
            scoreItem.put("points", score);
            currentEraScores.add(scoreItem);
        }

        for (Map<String, Object> tile : industries) {
            if (!Boolean.TRUE.equals(tile.get("flipped")) || Boolean.TRUE.equals(tile.get("industryVpScored"))) {
                continue;
            }
            int score = parseInteger(tile.get("victoryPoints"), 0);
            long ownerId = asLong(tile.get("ownerId"));
            Map<String, Object> stats = mapOf(statsByPlayer.get(String.valueOf(ownerId)));
            stats.put("victoryPoints", parseInteger(stats.get("victoryPoints"), 0) + score);
            statsByPlayer.put(String.valueOf(ownerId), stats);
            tile.put("industryVpScored", true);
            Map<String, Object> scoreItem = new LinkedHashMap<>();
            scoreItem.put("era", era);
            scoreItem.put("ownerId", ownerId);
            scoreItem.put("ownerName", tile.get("ownerName"));
            scoreItem.put("type", "industry");
            scoreItem.put("summary", cityCnName(Objects.toString(tile.get("city"), "")) + " " + industryNameCn(Objects.toString(tile.get("industryType"), "")));
            scoreItem.put("points", score);
            currentEraScores.add(scoreItem);
        }
        eraScores.addAll(0, currentEraScores);

        board.put("links", links);
        board.put("industries", industries);
        state.put("board", board);
        state.put("playerStats", statsByPlayer);
        state.put("eraScores", eraScores.stream().limit(80).toList());
        appendNotice(state, eraName(era) + "\u8ba1\u5206\u5b8c\u6210");
    }

    private void transitionToRailEra(Map<String, Object> state) {
        Map<String, Object> board = mapOf(state.get("board"));
        List<Map<String, Object>> remainingIndustries = listOf(board.get("industries")).stream()
                .filter(tile -> !removedAfterCanalEra(tile))
                .toList();
        board.put("industries", new ArrayList<>(remainingIndustries));
        board.put("links", new ArrayList<>());
        board.put("availableRoutes", routes());
        board.put("buildableCities", new ArrayList<>(citySlotOptions().keySet()));
        board.put("citySlots", citySlotOptions());
        board.put("map", mapSummary());
        board.put("eraScored", false);
        state.put("board", board);

        List<Map<String, Object>> players = listOf(state.get("players"));
        List<Map<String, Object>> deck = buildEraDeck(players.size());
        Collections.shuffle(deck);
        Map<String, Object> hands = new LinkedHashMap<>();
        for (Map<String, Object> player : players) {
            List<Map<String, Object>> hand = new ArrayList<>();
            for (int index = 0; index < STARTING_HAND_SIZE && !deck.isEmpty(); index++) {
                hand.add(deck.remove(0));
            }
            hands.put(String.valueOf(player.get("userId")), hand);
        }

        resetRoundSpending(state);
        state.put("era", "rail");
        state.put("round", 1);
        state.put("turnIndex", 0);
        state.put("currentPlayerId", asLong(players.getFirst().get("userId")));
        state.put("hands", hands);
        state.put("deck", deck);
        state.put("scoutPool", initialScoutPool());
        state.put("discardPile", new ArrayList<>());
        Map<String, Object> market = initialMarket(players.size());
        List<Map<String, Object>> merchants = listOf(mapOf(state.get("market")).get("beerMerchants"));
        if (!merchants.isEmpty()) {
            for (Map<String, Object> merchant : merchants) {
                boolean providesBeer = Boolean.TRUE.equals(merchant.get("providesBeer"))
                        && Boolean.TRUE.equals(merchant.get("marketOpen"))
                        && !Boolean.TRUE.equals(merchant.get("blank"));
                merchant.put("beer", providesBeer ? 1 : 0);
                merchant.put("used", !providesBeer);
            }
            market.put("beerMerchants", merchants);
        }
        state.put("market", market);
        state.put("canMaintainEra", false);
        state.put("eraEnding", false);
        beginTurn(state, "\u94c1\u8def\u65f6\u4ee3\u5f00\u59cb\uff0c\u8f6e\u5230 " + playerName(players, asLong(players.getFirst().get("userId"))) + " \u884c\u52a8");
    }

    private boolean removedAfterCanalEra(Map<String, Object> tile) {
        return "canal".equals(tile.get("era"))
                && parseInteger(tile.get("level"), 1) == 1
                && CANAL_ERA_REMOVED_LEVEL_ONE_INDUSTRIES.contains(Objects.toString(tile.get("industryType"), ""));
    }

    private void finishGame(Map<String, Object> state) {
        state.put("phase", "finished");
        state.put("canMaintainEra", false);
        state.put("availableActions", new LinkedHashMap<>());
        state.put("turn", new LinkedHashMap<>());
        state.put("turnStartSnapshot", null);
        state.put("winners", winnersOf(state));
        appendNotice(state, "\u6e38\u620f\u7ed3\u675f\uff0c\u80dc\u8005\uff1a" + winnersText(listOf(state.get("winners"))));
    }

    private void resetRoundSpending(Map<String, Object> state) {
        Map<String, Object> statsByPlayer = mapOf(state.get("playerStats"));
        for (String playerId : new ArrayList<>(statsByPlayer.keySet())) {
            Map<String, Object> stats = mapOf(statsByPlayer.get(playerId));
            stats.put("spentThisRound", 0);
            statsByPlayer.put(playerId, stats);
        }
        state.put("playerStats", statsByPlayer);
    }

    private void collectIncome(Map<String, Object> state) {
        Map<String, Object> statsByPlayer = mapOf(state.get("playerStats"));
        List<Map<String, Object>> debts = new ArrayList<>();
        for (String playerId : new ArrayList<>(statsByPlayer.keySet())) {
            Map<String, Object> stats = mapOf(statsByPlayer.get(playerId));
            int income = incomeForLevel(parseInteger(stats.get("incomeLevel"), 0));
            int money = parseInteger(stats.get("money"), 0);
            if (income >= 0 || money + income >= 0) {
                stats.put("money", money + income);
            } else {
                stats.put("money", 0);
                Map<String, Object> debt = new LinkedHashMap<>();
                debt.put("playerId", Long.parseLong(playerId));
                debt.put("amount", -(money + income));
                debts.add(debt);
            }
            stats.put("lastIncome", income);
            stats.put("income", income);
            statsByPlayer.put(playerId, stats);
        }
        state.put("playerStats", statsByPlayer);
        state.put("incomeDebtQueue", debts);
    }

    private void continueIncomeDebtResolution(Map<String, Object> state) {
        Map<String, Object> currentDebt = mapOf(state.get("incomeDebt"));
        if (!currentDebt.isEmpty() && parseInteger(currentDebt.get("amount"), 0) > 0) {
            long playerId = asLong(currentDebt.get("playerId"));
            if (ownedMapIndustries(state, playerId).isEmpty()) {
                deductVictoryPointsForDebt(state, playerId, parseInteger(currentDebt.get("amount"), 0));
                state.put("incomeDebt", new LinkedHashMap<>());
            } else {
                prepareIncomeDebtTurn(state, currentDebt);
                return;
            }
        }

        List<Map<String, Object>> queue = listOf(state.get("incomeDebtQueue"));
        while (!queue.isEmpty()) {
            Map<String, Object> debt = queue.remove(0);
            state.put("incomeDebtQueue", queue);
            long playerId = asLong(debt.get("playerId"));
            if (ownedMapIndustries(state, playerId).isEmpty()) {
                deductVictoryPointsForDebt(state, playerId, parseInteger(debt.get("amount"), 0));
                continue;
            }
            state.put("incomeDebt", debt);
            prepareIncomeDebtTurn(state, debt);
            return;
        }
        state.put("incomeDebt", new LinkedHashMap<>());
        completeRoundAfterIncome(state);
    }

    private void prepareIncomeDebtTurn(Map<String, Object> state, Map<String, Object> debt) {
        long playerId = asLong(debt.get("playerId"));
        state.put("currentPlayerId", playerId);
        Map<String, Object> turn = new LinkedHashMap<>();
        turn.put("incomeDebtResolution", true);
        turn.put("actionsAllowed", 0);
        turn.put("actionsRemaining", 0);
        turn.put("actionsTaken", 0);
        turn.put("awaitingEndTurn", false);
        state.put("turn", turn);
        state.put("turnStartSnapshot", null);
        appendNotice(state, "玩家" + playerName(listOf(state.get("players")), playerId)
                + " 需要支付" + parseInteger(debt.get("amount"), 0) + "英镑收入欠款");
    }

    private void deductVictoryPointsForDebt(Map<String, Object> state, long playerId, int amount) {
        Map<String, Object> statsByPlayer = mapOf(state.get("playerStats"));
        Map<String, Object> stats = mapOf(statsByPlayer.get(String.valueOf(playerId)));
        stats.put("victoryPoints", parseInteger(stats.get("victoryPoints"), 0) - amount);
        statsByPlayer.put(String.valueOf(playerId), stats);
        state.put("playerStats", statsByPlayer);
        appendNotice(state, "玩家" + playerName(listOf(state.get("players")), playerId)
                + " 无产业可移除，未支付的" + amount + "英镑转为扣除" + amount + " VP");
    }

    private List<Map<String, Object>> ownedMapIndustries(Map<String, Object> state, long playerId) {
        return listOf(mapOf(state.get("board")).get("industries")).stream()
                .filter(tile -> asLong(tile.get("ownerId")) == playerId)
                .toList();
    }

    private void completeRoundAfterIncome(Map<String, Object> state) {
        int finishedRound = parseInteger(state.get("pendingFinishedRound"), parseInteger(state.get("round"), 1));
        state.remove("pendingFinishedRound");
        resetRoundSpending(state);
        if (allHandsEmpty(state) || finishedRound >= roundsPerEra(state)) {
            enterEraEnding(state);
            return;
        }
        state.put("round", finishedRound + 1);
        state.put("turnIndex", 0);
        long nextPlayerId = asLong(objectList(state.get("turnOrder")).getFirst());
        state.put("currentPlayerId", nextPlayerId);
    }

    private int roundsPerEra(Map<String, Object> state) {
        return 12 - listOf(state.get("players")).size();
    }

    private boolean allHandsEmpty(Map<String, Object> state) {
        return mapOf(state.get("hands")).values().stream().allMatch(hand -> listOf(hand).isEmpty());
    }

    private int incomeForLevel(int incomeLevel) {
        if (INCOME_BY_LEVEL.isEmpty()) {
            return incomeLevel < 0 ? incomeLevel : incomeLevel / 5;
        }
        int index = Math.max(0, Math.min(incomeLevel, INCOME_BY_LEVEL.size() - 1));
        return INCOME_BY_LEVEL.get(index);
    }

    private void reorderTurnOrderBySpending(Map<String, Object> state) {
        Map<String, Object> statsByPlayer = mapOf(state.get("playerStats"));
        List<Object> previousOrder = objectList(state.get("turnOrder"));
        List<Object> sorted = new ArrayList<>(previousOrder);
        sorted.sort((left, right) -> {
            int leftSpent = parseInteger(mapOf(statsByPlayer.get(String.valueOf(asLong(left)))).get("spentThisRound"), 0);
            int rightSpent = parseInteger(mapOf(statsByPlayer.get(String.valueOf(asLong(right)))).get("spentThisRound"), 0);
            int comparison = Integer.compare(leftSpent, rightSpent);
            if (comparison != 0) {
                return comparison;
            }
            return Integer.compare(previousOrder.indexOf(left), previousOrder.indexOf(right));
        });
        state.put("turnOrder", sorted);
    }

    private int routeVictoryPoints(List<Map<String, Object>> industries, String from, String to) {
        return endpointRoadPoints(industries, from) + endpointRoadPoints(industries, to);
    }

    private int endpointRoadPoints(List<Map<String, Object>> industries, String city) {
        int points = marketRoadPoints(city);
        for (Map<String, Object> tile : industries) {
            if (Boolean.TRUE.equals(tile.get("flipped"))
                    && city.equals(tile.get("city"))) {
                points += Math.max(0, parseInteger(tile.get("roadPoints"), 0));
            }
        }
        return points;
    }

    private int marketRoadPoints(String city) {
        List<Map<String, Object>> markets = MAP_MARKETS.isEmpty() ? fallbackMarkets() : MAP_MARKETS;
        return markets.stream()
                .filter(market -> city.equals(Objects.toString(market.get("city"), "")))
                .mapToInt(market -> parseInteger(market.get("roadPoint"), 0))
                .findFirst()
                .orElse(0);
    }

    private List<Map<String, Object>> winnersOf(Map<String, Object> state) {
        List<Map<String, Object>> players = listOf(state.get("players"));
        Map<String, Object> statsByPlayer = mapOf(state.get("playerStats"));
        int bestVp = players.stream()
                .mapToInt(player -> parseInteger(mapOf(statsByPlayer.get(String.valueOf(player.get("userId")))).get("victoryPoints"), 0))
                .max()
                .orElse(0);
        List<Map<String, Object>> winners = new ArrayList<>();
        for (Map<String, Object> player : players) {
            Map<String, Object> stats = mapOf(statsByPlayer.get(String.valueOf(player.get("userId"))));
            if (parseInteger(stats.get("victoryPoints"), 0) == bestVp) {
                Map<String, Object> winner = new LinkedHashMap<>();
                winner.put("userId", player.get("userId"));
                winner.put("username", player.get("username"));
                winner.put("victoryPoints", bestVp);
                winners.add(winner);
            }
        }
        return winners;
    }

    private String winnersText(List<Map<String, Object>> winners) {
        return winners.stream()
                .map(winner -> Objects.toString(winner.get("username"), ""))
                .reduce((left, right) -> left + "\u3001" + right)
                .orElse("");
    }

    private String eraName(String era) {
        return "rail".equals(era) ? "\u94c1\u8def\u65f6\u4ee3" : "\u8fd0\u6cb3\u65f6\u4ee3";
    }

    private String eraLinkType(String era) {
        return "rail".equals(era) ? "rail" : "canal";
    }

    private String industryNameCn(String industryType) {
        return switch (industryType) {
            case "cotton_mill" -> CITY_NAME_MAPPING.getOrDefault("Cotton", "\u68c9\u7eba\u5382");
            case "manufacturer" -> CITY_NAME_MAPPING.getOrDefault("Manufacture", "\u52a0\u5de5\u5382");
            case "brewery" -> CITY_NAME_MAPPING.getOrDefault("Brewery", "\u917f\u9152\u5382");
            case "pottery" -> CITY_NAME_MAPPING.getOrDefault("Pottery", "\u9676\u74f7\u5382");
            case "iron_works" -> CITY_NAME_MAPPING.getOrDefault("Iron", "\u94a2\u94c1\u5382");
            case "coal_mine" -> CITY_NAME_MAPPING.getOrDefault("Coal", "\u7164\u77ff\u573a");
            default -> INDUSTRY_NAMES.getOrDefault(industryType, industryType);
        };
    }

    private List<Map<String, Object>> buildEraDeck(int playerCount) {
        List<Map<String, Object>> configuredDeck = buildConfiguredEraDeck(playerCount);
        if (!configuredDeck.isEmpty()) {
            return configuredDeck;
        }

        List<Map<String, Object>> deck = new ArrayList<>();
        for (String city : cityCards()) {
            Map<String, Object> card = card("location", city, cityCnName(city));
            card.put("city", city);
            card.put("displayName", cityCnName(city));
            deck.add(card);
        }
        for (String industry : INDUSTRY_TYPES) {
            for (int copy = 1; copy <= 4; copy++) {
                Map<String, Object> card = card("industry", industry + "_" + copy, industryNameCn(industry));
                card.put("displayName", industryNameCn(industry));
                card.put("industryTypes", List.of(industry));
                deck.add(card);
            }
        }
        int needed = playerCount * STARTING_HAND_SIZE + 8;
        int extra = 1;
        while (deck.size() < needed) {
            deck.add(card("location", "extra_" + extra, "\u989d\u5916\u5730\u70b9\u724c" + extra));
            extra++;
        }
        return deck;
    }

    private Map<String, Object> card(String type, String key, String name) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", type + "_" + key + "_" + System.nanoTime());
        card.put("type", type);
        card.put("key", key);
        card.put("name", name);
        return card;
    }

    private Map<String, Object> initialCardHints(List<Map<String, Object>> deck) {
        Map<String, Map<String, Object>> hints = new LinkedHashMap<>();
        for (Map<String, Object> card : deck) {
            if (isWildCard(card)) {
                continue;
            }
            String key = cardHintKey(card);
            if (key.isBlank()) {
                continue;
            }
            Map<String, Object> hint = hints.computeIfAbsent(key, ignored -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("key", key);
                value.put("name", cardHintName(card));
                value.put("total", 0);
                value.put("remaining", 0);
                return value;
            });
            hint.put("total", parseInteger(hint.get("total"), 0) + 1);
            hint.put("remaining", parseInteger(hint.get("remaining"), 0) + 1);
        }
        return new LinkedHashMap<>(Map.of("cards", new ArrayList<>(hints.values())));
    }

    private String cardHintKey(Map<String, Object> card) {
        String type = Objects.toString(card.get("type"), "");
        if ("location".equals(type)) {
            return "location:" + Objects.toString(card.get("key"), "");
        }
        if ("industry".equals(type)) {
            return "industry:" + String.join("/", stringList(card.get("industryTypes")));
        }
        return "";
    }

    private String cardHintName(Map<String, Object> card) {
        return Objects.toString(card.get("displayName"), Objects.toString(card.get("name"), ""));
    }

    private List<Map<String, Object>> buildConfiguredEraDeck(int playerCount) {
        List<Map<String, Object>> deck = new ArrayList<>();
        for (Map<String, Object> entry : staticListOf(CARD_CONFIG.get("draw_deck"))) {
            int amount = amountForPlayerCount(entry.get("amount_by_player_count"), playerCount);
            for (int copy = 0; copy < amount; copy++) {
                Map<String, Object> card = configuredDeckCard(entry);
                if (!card.isEmpty()) {
                    deck.add(card);
                }
            }
        }
        return deck;
    }

    private Map<String, Object> configuredDeckCard(Map<String, Object> entry) {
        String type = Objects.toString(entry.get("type"), "");
        Map<String, Object> info = staticMapOf(entry.get("info"));
        if ("place".equals(type)) {
            String city = displayCityName(Objects.toString(info.get("city"), ""));
            if (city.isBlank() || "all".equalsIgnoreCase(city)) {
                return wildCard("wild_location", "\u4e07\u80fd\u5730\u70b9\u724c");
            }
            Map<String, Object> card = card("location", city, cityCnName(city));
            card.put("sourceType", "place");
            card.put("city", city);
            card.put("displayName", cityCnName(city));
            return card;
        }
        if ("industry".equals(type)) {
            List<String> industryTypes = configuredIndustryTypes(info.get("factory"));
            if (industryTypes.isEmpty()) {
                return wildCard("wild_industry", "\u4e07\u80fd\u4ea7\u4e1a\u724c");
            }
            String key = industryTypes.size() == 1 ? industryTypes.getFirst() : String.join("_", industryTypes);
            String name = industryTypes.stream()
                    .map(this::industryNameCn)
                    .reduce((left, right) -> left + "/" + right)
                    .orElse("");
            Map<String, Object> card = card("industry", key, name);
            card.put("sourceType", "industry");
            card.put("industryTypes", industryTypes);
            card.put("displayName", name);
            return card;
        }
        return new LinkedHashMap<>();
    }

    private int amountForPlayerCount(Object rawAmounts, int playerCount) {
        Map<String, Object> amounts = staticMapOf(rawAmounts);
        if (amounts.isEmpty()) {
            return 0;
        }
        String key = String.valueOf(Math.max(2, Math.min(4, playerCount)));
        return parseInteger(amounts.get(key), 0);
    }

    private static Map<String, Object> route(String from, String to, boolean canal, boolean rail) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("from", from);
        route.put("to", to);
        route.put("canal", canal);
        route.put("rail", rail);
        return route;
    }

    private static Map<String, Object> loadBirminghamMap() {
        ObjectMapper mapper = new ObjectMapper();
        for (Path path : brassSourcePaths("Birmingham_map.json")) {
            if (!Files.exists(path)) {
                continue;
            }
            try (InputStream input = Files.newInputStream(path)) {
                return mapper.readValue(input, new TypeReference<Map<String, Object>>() {});
            } catch (IOException ignored) {
                return new LinkedHashMap<>();
            }
        }
        try (InputStream input = BrassGameModule.class.getResourceAsStream("Birmingham_map.json")) {
            if (input != null) {
                return mapper.readValue(input, new TypeReference<Map<String, Object>>() {});
            }
        } catch (IOException ignored) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, String> loadCityNameMapping() {
        ObjectMapper mapper = new ObjectMapper();
        for (Path path : brassSourcePaths("Birmingham_city.json")) {
            if (!Files.exists(path)) {
                continue;
            }
            try (InputStream input = Files.newInputStream(path)) {
                return mapper.readValue(input, new TypeReference<Map<String, String>>() {});
            } catch (IOException ignored) {
                return new LinkedHashMap<>();
            }
        }
        try (InputStream input = BrassGameModule.class.getResourceAsStream("Birmingham_city.json")) {
            if (input != null) {
                return mapper.readValue(input, new TypeReference<Map<String, String>>() {});
            }
        } catch (IOException ignored) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, List<List<String>>> loadCitySlotOptions(Map<String, Object> map) {
        Map<String, List<List<String>>> slotsByCity = new LinkedHashMap<>();
        for (Map<String, Object> city : staticListOf(map.get("city"))) {
            if (!"Industry".equals(Objects.toString(city.get("type"), ""))) {
                continue;
            }
            String cityName = displayCityName(Objects.toString(city.get("name"), ""));
            List<List<String>> slots = new ArrayList<>();
            Map<String, Object> detail = staticMapOf(city.get("detail"));
            for (Object rawSlot : staticObjectList(detail.get("factory_type"))) {
                List<String> options = new ArrayList<>();
                for (Object rawIndustry : staticObjectList(rawSlot)) {
                    String industryType = normalizeIndustryType(Objects.toString(rawIndustry, ""));
                    if (!industryType.isBlank()) {
                        options.add(industryType);
                    }
                }
                if (!options.isEmpty()) {
                    slots.add(options.stream().distinct().toList());
                }
            }
            if (!slots.isEmpty()) {
                slotsByCity.put(cityName, slots);
            }
        }
        return slotsByCity;
    }

    private static List<Map<String, Object>> loadRoutes(Map<String, Object> map) {
        List<Map<String, Object>> routes = new ArrayList<>();
        for (Map<String, Object> road : staticListOf(map.get("road"))) {
            List<String> types = staticObjectList(road.get("type")).stream()
                    .map(String::valueOf)
                    .toList();
            Map<String, Object> route = route(
                    displayCityName(Objects.toString(road.get("src"), "")),
                    displayCityName(Objects.toString(road.get("dst"), "")),
                    types.contains("canal"),
                    types.contains("railway") || types.contains("rail")
            );
            route.put("hasBrewery", Boolean.TRUE.equals(road.get("hasBrewery")));
            routes.add(route);
        }
        return routes;
    }

    private static List<String> loadCityCards(Map<String, Object> map) {
        List<String> cities = new ArrayList<>();
        for (Map<String, Object> city : staticListOf(map.get("city"))) {
            String name = displayCityName(Objects.toString(city.get("name"), ""));
            if (!List.of("Personal_Brewery", "Rural_Brewery").contains(name) && !cities.contains(name)) {
                cities.add(name);
            }
        }
        return cities;
    }

    private static List<Map<String, Object>> loadMarkets(Map<String, Object> map) {
        List<Map<String, Object>> markets = new ArrayList<>();
        for (Map<String, Object> city : staticListOf(map.get("city"))) {
            if (!"Market".equals(Objects.toString(city.get("type"), ""))) {
                continue;
            }
            Map<String, Object> detail = staticMapOf(city.get("detail"));
            Map<String, Object> market = new LinkedHashMap<>();
            market.put("city", displayCityName(Objects.toString(city.get("name"), "")));
            market.put("availablePlayers", parseStaticInteger(detail.get("available_players"), 0));
            market.put("roadPoint", parseStaticInteger(detail.get("road_point"), 0));
            market.put("marketCount", parseStaticInteger(detail.get("market_num"), 1));
            market.put("reward", Objects.toString(detail.get("sale_reward"), ""));
            markets.add(market);
        }
        return markets;
    }

    private static List<Integer> loadIncomeConfig() {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> config = new LinkedHashMap<>();
        for (Path path : brassSourcePaths("Income_config.json")) {
            if (!Files.exists(path)) {
                continue;
            }
            try (InputStream input = Files.newInputStream(path)) {
                config = mapper.readValue(input, new TypeReference<Map<String, Object>>() {});
                break;
            } catch (IOException ignored) {
                config = new LinkedHashMap<>();
            }
        }
        if (config.isEmpty()) {
            try (InputStream input = BrassGameModule.class.getResourceAsStream("Income_config.json")) {
                if (input != null) {
                    config = mapper.readValue(input, new TypeReference<Map<String, Object>>() {});
                }
            } catch (IOException ignored) {
                config = new LinkedHashMap<>();
            }
        }

        List<Integer> incomeByLevel = new ArrayList<>();
        for (Object rawValue : staticObjectList(config.get("incomeByLevel"))) {
            incomeByLevel.add(parseStaticInteger(rawValue, 0));
        }
        return incomeByLevel;
    }

    private static Map<Integer, Integer> loadIncomeLevelAfterLoan() {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : loadJsonObject("IncomeLevel_after_Loan.json").entrySet()) {
            try {
                result.put(Integer.parseInt(entry.getKey()), parseStaticInteger(entry.getValue(), 0));
            } catch (NumberFormatException ignored) {
                // Ignore malformed configuration keys.
            }
        }
        return result;
    }

    private static Map<String, List<Map<String, Object>>> loadBuildingConfig() {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> config = new LinkedHashMap<>();
        for (Path path : brassSourcePaths("Birmingham_buildings.json")) {
            if (!Files.exists(path)) {
                continue;
            }
            try (InputStream input = Files.newInputStream(path)) {
                config = mapper.readValue(input, new TypeReference<Map<String, Object>>() {});
                break;
            } catch (IOException ignored) {
                config = new LinkedHashMap<>();
            }
        }
        if (config.isEmpty()) {
            try (InputStream input = BrassGameModule.class.getResourceAsStream("Birmingham_buildings.json")) {
                if (input != null) {
                    config = mapper.readValue(input, new TypeReference<Map<String, Object>>() {});
                }
            } catch (IOException ignored) {
                config = new LinkedHashMap<>();
            }
        }

        Map<String, List<Map<String, Object>>> buildings = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String industryType = normalizeIndustryType(entry.getKey());
            if (industryType.isBlank()) {
                continue;
            }
            List<Map<String, Object>> tiles = new ArrayList<>();
            int copyIndex = 1;
            for (Map<String, Object> rawTile : staticListOf(entry.getValue())) {
                int amount = Math.max(1, parseStaticInteger(rawTile.get("amount"), 1));
                for (int copy = 1; copy <= amount; copy++) {
                    tiles.add(buildingTile(industryType, rawTile, copyIndex));
                    copyIndex++;
                }
            }
            if (!tiles.isEmpty()) {
                buildings.put(industryType, tiles);
            }
        }
        return buildings;
    }

    private static Map<String, Object> loadJsonObject(String fileName) {
        ObjectMapper mapper = new ObjectMapper();
        for (Path path : brassSourcePaths(fileName)) {
            if (!Files.exists(path)) {
                continue;
            }
            try (InputStream input = Files.newInputStream(path)) {
                return mapper.readValue(input, new TypeReference<Map<String, Object>>() {});
            } catch (IOException ignored) {
                return new LinkedHashMap<>();
            }
        }
        try (InputStream input = BrassGameModule.class.getResourceAsStream(fileName)) {
            if (input != null) {
                return mapper.readValue(input, new TypeReference<Map<String, Object>>() {});
            }
        } catch (IOException ignored) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>();
    }

    private static List<Map<String, Object>> loadJsonList(String fileName) {
        ObjectMapper mapper = new ObjectMapper();
        for (Path path : brassSourcePaths(fileName)) {
            if (!Files.exists(path)) {
                continue;
            }
            try (InputStream input = Files.newInputStream(path)) {
                return mapper.readValue(input, new TypeReference<List<Map<String, Object>>>() {});
            } catch (IOException ignored) {
                return new ArrayList<>();
            }
        }
        try (InputStream input = BrassGameModule.class.getResourceAsStream(fileName)) {
            if (input != null) {
                return mapper.readValue(input, new TypeReference<List<Map<String, Object>>>() {});
            }
        } catch (IOException ignored) {
            return new ArrayList<>();
        }
        return new ArrayList<>();
    }

    private static List<Path> brassSourcePaths(String fileName) {
        return List.of(
                Path.of("src/main/java/com/fortell/boardgame/game_modules/brass/" + fileName),
                Path.of("backend/src/main/java/com/fortell/boardgame/game_modules/brass/" + fileName)
        );
    }

    private static Map<String, Object> buildingTile(String industryType, Map<String, Object> rawTile, int copyIndex) {
        int level = parseStaticInteger(rawTile.get("level"), 1);
        int period = parseStaticInteger(rawTile.get("period"), 0);
        List<Integer> resourceAmounts = resourceAmounts(rawTile.get("resource_amount"));
        Map<String, Object> tile = new LinkedHashMap<>();
        tile.put("id", industryType + "_level_" + level + "_" + copyIndex);
        tile.put("industryType", industryType);
        tile.put("industryName", INDUSTRY_NAMES.get(industryType));
        tile.put("level", level);
        tile.put("era", period == 1 ? "canal" : "rail");
        tile.put("period", period);
        tile.put("cost", parseStaticInteger(rawTile.get("price"), INDUSTRY_COSTS.get(industryType)));
        tile.put("coalCost", parseStaticInteger(rawTile.get("coal_cost"), 0));
        tile.put("ironCost", parseStaticInteger(rawTile.get("iron_cost"), 0));
        tile.put("saleBeerCost", parseStaticInteger(rawTile.get("sale_wine"), SELLABLE_INDUSTRIES.contains(industryType) ? 1 : 0));
        tile.put("resourceAmounts", resourceAmounts);
        tile.put("coal", 0);
        tile.put("iron", 0);
        tile.put("beer", 0);
        tile.put("incomeReward", parseStaticInteger(rawTile.get("incomeLevel_increase"), INDUSTRY_INCOME_REWARDS.get(industryType)));
        tile.put("victoryPoints", parseStaticInteger(rawTile.get("VP"), INDUSTRY_VP_REWARDS.get(industryType)));
        tile.put("roadPoints", parseStaticInteger(rawTile.get("road_point"), 1));
        tile.put("canDevelop", !Boolean.FALSE.equals(rawTile.get("can_develop")));
        tile.put("flipType", Objects.toString(rawTile.get("flip_type"), SELLABLE_INDUSTRIES.contains(industryType) ? "sell" : "deplete"));
        return tile;
    }

    private static List<Integer> resourceAmounts(Object rawValue) {
        if (rawValue instanceof List<?> values) {
            int canal = values.isEmpty() ? 0 : parseStaticInteger(values.get(0), 0);
            int rail = values.size() < 2 ? canal : parseStaticInteger(values.get(1), canal);
            return List.of(canal, rail);
        }
        int amount = parseStaticInteger(rawValue, 0);
        return List.of(amount, amount);
    }

    private int resourceAmountForEra(Map<String, Object> boardTile, String era) {
        List<Object> amounts = objectList(boardTile.get("resourceAmounts"));
        if (amounts.isEmpty()) {
            String resourceField = switch (Objects.toString(boardTile.get("industryType"), "")) {
                case "coal_mine" -> "coal";
                case "iron_works" -> "iron";
                case "brewery" -> "beer";
                default -> "";
            };
            return resourceField.isBlank() ? 0 : parseInteger(boardTile.get(resourceField), 0);
        }
        int index = "rail".equals(era) ? 1 : 0;
        return parseInteger(amounts.get(Math.min(index, amounts.size() - 1)), 0);
    }

    private static List<String> cityCards() {
        return MAP_CITY_CARDS.isEmpty() ? CITY_CARDS : MAP_CITY_CARDS;
    }

    private static List<Map<String, Object>> routes() {
        return MAP_ROUTES.isEmpty() ? ROUTES : MAP_ROUTES;
    }

    private static Map<String, List<List<String>>> citySlotOptions() {
        if (!MAP_CITY_SLOT_OPTIONS.isEmpty()) {
            return MAP_CITY_SLOT_OPTIONS;
        }
        Map<String, List<List<String>>> fallback = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : CITY_SLOTS.entrySet()) {
            fallback.put(entry.getKey(), entry.getValue().stream().map(List::of).toList());
        }
        return fallback;
    }

    private static boolean cityCanBuild(String city, String industryType) {
        return citySlotOptions().getOrDefault(city, List.of()).stream()
                .anyMatch(slot -> slot.contains(industryType));
    }

    private static List<Map<String, Object>> fallbackMarkets() {
        List<Map<String, Object>> markets = new ArrayList<>();
        markets.add(market("Warrington", 3, 2, "5英镑"));
        markets.add(market("Shrewsbury", 2, 1, "4 VP"));
        markets.add(market("Nottingham", 4, 2, "3 VP"));
        markets.add(market("Gloucester", 2, 2, "Develop 1"));
        markets.add(market("Oxford", 2, 2, "2 Income"));
        return markets;
    }

    private static Map<String, Object> market(String city, int availablePlayers, int marketCount, String reward) {
        Map<String, Object> market = new LinkedHashMap<>();
        market.put("city", city);
        market.put("availablePlayers", availablePlayers);
        market.put("roadPoint", 2);
        market.put("marketCount", marketCount);
        market.put("reward", reward);
        return market;
    }

    private static String displayCityName(String raw) {
        if (raw.isBlank()) {
            return "";
        }
        for (String city : CITY_CARDS) {
            if (city.equalsIgnoreCase(raw)) {
                return city;
            }
        }
        if ("BREWERY".equalsIgnoreCase(raw)) {
            return "Personal_Brewery";
        }
        return raw;
    }

    private static String normalizeIndustryType(String raw) {
        String value = raw == null ? "" : raw.trim();
        return switch (value.toLowerCase()) {
            case "cotton", "cotton_mill" -> "cotton_mill";
            case "manufacture", "manufacturer" -> "manufacturer";
            case "brewery" -> "brewery";
            case "pottery" -> "pottery";
            case "iron", "iron_works" -> "iron_works";
            case "coal", "coal_mine" -> "coal_mine";
            default -> "";
        };
    }

    private static List<String> configuredIndustryTypes(Object rawFactory) {
        List<Object> factories;
        if (rawFactory instanceof List<?> list) {
            factories = new ArrayList<>(list);
        } else if (rawFactory == null) {
            factories = new ArrayList<>();
        } else {
            factories = new ArrayList<>(List.of(rawFactory));
        }
        if (factories.stream().anyMatch(factory -> "all".equalsIgnoreCase(Objects.toString(factory, "")))) {
            return new ArrayList<>(INDUSTRY_TYPES);
        }
        return factories.stream()
                .map(factory -> normalizeIndustryType(Objects.toString(factory, "")))
                .filter(industry -> !industry.isBlank())
                .distinct()
                .toList();
    }

    private List<Map<String, Object>> listCopy(List<Map<String, Object>> value) {
        List<Map<String, Object>> copied = new ArrayList<>();
        for (Map<String, Object> item : value) {
            copied.add(new LinkedHashMap<>(item));
        }
        return copied;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> staticMapOf(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> staticListOf(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> copied = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    copied.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
            return copied;
        }
        return new ArrayList<>();
    }

    private static List<Object> staticObjectList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    private static int parseStaticInteger(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private Map<String, Object> wildCard(String type, String name) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", type + "_" + System.nanoTime());
        card.put("type", type);
        card.put("key", type);
        card.put("name", name);
        card.put("wild", true);
        return card;
    }

    private Map<String, Object> takeScoutCard(Map<String, Object> state, String type, String fallbackName) {
        List<Map<String, Object>> scoutPool = listOf(state.get("scoutPool"));
        Map<String, Object> card = scoutPool.stream()
                .filter(item -> type.equals(Objects.toString(item.get("type"), "")))
                .findFirst()
                .orElse(null);
        if (card == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u4fa6\u67e5\u724c\u6c60\u5df2\u8017\u5c3d");
        }
        scoutPool.remove(card);
        state.put("scoutPool", scoutPool);
        return card;
    }

    private Map<String, Object> findCard(List<Map<String, Object>> cards, String cardId) {
        return cards.stream()
                .filter(card -> cardId.equals(Objects.toString(card.get("id"), "")))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> findTile(List<Map<String, Object>> tiles, String tileId) {
        return tiles.stream()
                .filter(tile -> tileId.equals(Objects.toString(tile.get("id"), "")))
                .findFirst()
                .orElse(null);
    }

    private boolean cardCanBuild(Map<String, Object> card, String city, String industryType, Map<String, Object> board, long playerId) {
        String type = Objects.toString(card.get("type"), "");
        String key = Objects.toString(card.get("key"), "");
        List<String> industryTypes = stringList(card.get("industryTypes"));
        if (Boolean.TRUE.equals(card.get("wild"))) {
            return ("wild_location".equals(type) || "wild_location".equals(key))
                    || (("wild_industry".equals(type) || "wild_industry".equals(key)) && cityBuildableWithIndustryCard(board, playerId, city));
        }
        return ("location".equals(type) && city.equals(key))
                || ("industry".equals(type)
                && (industryType.equals(key) || industryTypes.contains(industryType))
                && cityBuildableWithIndustryCard(board, playerId, city));
    }

    private boolean cardCanBuildAnonymousBrewery(Map<String, Object> card) {
        String type = Objects.toString(card.get("type"), "");
        String key = Objects.toString(card.get("key"), "");
        List<String> industryTypes = stringList(card.get("industryTypes"));
        return ("industry".equals(type) && ("brewery".equals(key) || industryTypes.contains("brewery")))
                || Boolean.TRUE.equals(card.get("wild")) && ("wild_industry".equals(type) || "wild_industry".equals(key));
    }

    private boolean isWildCard(Map<String, Object> card) {
        String type = Objects.toString(card.get("type"), "");
        String key = Objects.toString(card.get("key"), "");
        return Boolean.TRUE.equals(card.get("wild")) || type.startsWith("wild_") || key.startsWith("wild_");
    }

    private boolean cityBuildableWithIndustryCard(Map<String, Object> board, long playerId, String city) {
        List<String> networkCities = playerNetworkCities(board, playerId);
        return networkCities.isEmpty() || networkCities.contains(city);
    }

    private Map<String, Object> findRoute(String from, String to) {
        return routes().stream()
                .filter(route -> sameRoute(from, to, Objects.toString(route.get("from"), ""), Objects.toString(route.get("to"), "")))
                .findFirst()
                .orElse(null);
    }

    private List<Map<String, Object>> networkRouteSpecs(Map<String, Object> payload) {
        List<Map<String, Object>> routeSpecs = listOf(payload.get("routes"));
        if (!routeSpecs.isEmpty()) {
            return routeSpecs;
        }
        String from = Objects.toString(payload.get("from"), "");
        String to = Objects.toString(payload.get("to"), "");
        if (from.isBlank() || to.isBlank()) {
            return new ArrayList<>();
        }
        Map<String, Object> routeSpec = new LinkedHashMap<>();
        routeSpec.put("from", from);
        routeSpec.put("to", to);
        routeSpec.put("coalSourceTileId", Objects.toString(payload.get("coalSourceTileId"), ""));
        return new ArrayList<>(List.of(routeSpec));
    }

    private String preferredResourceId(Map<String, Object> routeSpec, Map<String, Object> payload, String singleField,
                                       String listField, int index) {
        String routeValue = Objects.toString(routeSpec.get(singleField), "");
        if (!routeValue.isBlank()) {
            return routeValue;
        }
        List<String> values = stringList(payload.get(listField));
        if (index >= 0 && index < values.size()) {
            return values.get(index);
        }
        return Objects.toString(payload.get(singleField), "");
    }

    private List<String> networkDestinations(List<Map<String, Object>> links) {
        List<String> destinations = new ArrayList<>();
        for (Map<String, Object> link : links) {
            destinations.add(Objects.toString(link.get("from"), ""));
            destinations.add(Objects.toString(link.get("to"), ""));
        }
        return destinations.stream().filter(value -> !value.isBlank()).distinct().toList();
    }

    private boolean sameRoute(String leftFrom, String leftTo, String rightFrom, String rightTo) {
        return (leftFrom.equals(rightFrom) && leftTo.equals(rightTo)) || (leftFrom.equals(rightTo) && leftTo.equals(rightFrom));
    }

    private List<String> normalizedRouteEnd(String from, String to) {
        return from.compareTo(to) <= 0 ? List.of(from, to) : List.of(to, from);
    }

    private String routeKey(String from, String to) {
        List<String> ends = normalizedRouteEnd(from, to);
        return ends.get(0) + "__" + ends.get(1);
    }

    private boolean routeOccupied(List<Map<String, Object>> links, String from, String to, String linkType) {
        return links.stream()
                .anyMatch(link -> linkType.equals(link.get("type"))
                        && sameRoute(from, to, Objects.toString(link.get("from"), ""), Objects.toString(link.get("to"), "")));
    }

    private boolean touchesPlayerNetwork(Map<String, Object> board, long playerId, String from, String to) {
        List<String> networkCities = playerNetworkCities(board, playerId);
        return networkCities.isEmpty() || networkCities.contains(from) || networkCities.contains(to);
    }

    private List<String> playerNetworkCities(Map<String, Object> board, long playerId) {
        List<String> cities = new ArrayList<>();
        for (Map<String, Object> tile : listOf(board.get("industries"))) {
            if (asLong(tile.get("ownerId")) == playerId) {
                cities.add(Objects.toString(tile.get("city"), ""));
            }
        }
        for (Map<String, Object> link : listOf(board.get("links"))) {
            if (asLong(link.get("ownerId")) == playerId) {
                cities.add(Objects.toString(link.get("from"), ""));
                cities.add(Objects.toString(link.get("to"), ""));
                Map<String, Object> route = findRoute(
                        Objects.toString(link.get("from"), ""),
                        Objects.toString(link.get("to"), "")
                );
                if (route != null && Boolean.TRUE.equals(route.get("hasBrewery"))) {
                    cities.add("Personal_Brewery");
                }
            }
        }
        return cities.stream().distinct().toList();
    }

    private boolean coalSourceReachable(Map<String, Object> board, Map<String, Object> source, List<String> destinations) {
        if (destinations == null || destinations.isEmpty()) {
            return true;
        }
        String sourceCity = Objects.toString(source.get("city"), "");
        return destinations.stream()
                .filter(destination -> !destination.isBlank())
                .anyMatch(destination -> citiesConnected(board, sourceCity, destination));
    }

    private boolean beerSourceReachable(Map<String, Object> board, Map<String, Object> source, String saleCity, long actorId) {
        if (asLong(source.get("ownerId")) == actorId) {
            return true;
        }
        String sourceCity = Objects.toString(source.get("city"), "");
        return citiesConnected(board, sourceCity, saleCity);
    }

    private boolean beerSourceReachableToAny(Map<String, Object> board, Map<String, Object> source,
                                             List<String> destinations, long actorId) {
        if (asLong(source.get("ownerId")) == actorId) {
            return true;
        }
        String sourceCity = Objects.toString(source.get("city"), "");
        return destinations.stream().anyMatch(destination -> citiesConnected(board, sourceCity, destination));
    }

    private boolean citiesConnected(Map<String, Object> board, String sourceCity, String destinationCity) {
        if (sourceCity.equals(destinationCity)) {
            return true;
        }
        List<Map<String, Object>> links = listOf(board.get("links"));
        List<String> visited = new ArrayList<>();
        List<String> pending = new ArrayList<>(List.of(sourceCity));
        while (!pending.isEmpty()) {
            String current = pending.remove(0);
            if (visited.contains(current)) {
                continue;
            }
            visited.add(current);
            for (Map<String, Object> link : links) {
                String from = Objects.toString(link.get("from"), "");
                String to = Objects.toString(link.get("to"), "");
                String next = "";
                if (current.equals(from)) {
                    next = to;
                } else if (current.equals(to)) {
                    next = from;
                }
                if (destinationCity.equals(next)) {
                    return true;
                }
                if (!next.isBlank() && !visited.contains(next)) {
                    pending.add(next);
                }
            }
        }
        return false;
    }

    private boolean playerAlreadyBuiltInCity(List<Map<String, Object>> industries, long playerId, String city) {
        return industries.stream()
                .anyMatch(tile -> asLong(tile.get("ownerId")) == playerId && city.equals(tile.get("city")));
    }

    private int firstAvailableSlot(List<Map<String, Object>> industries, String city, String industryType) {
        List<List<String>> slots = citySlotOptions().getOrDefault(city, List.of());
        for (int index = 0; index < slots.size(); index++) {
            if (slotHasDedicatedIndustry(city, industryType, index) && slotAvailable(industries, city, industryType, index)) {
                return index;
            }
        }
        for (int index = 0; index < slots.size(); index++) {
            if (!slotHasDedicatedIndustry(city, industryType, index) && slotAvailable(industries, city, industryType, index)) {
                return index;
            }
        }
        return -1;
    }

    private int firstBuildableDedicatedSlot(Map<String, Object> state, List<Map<String, Object>> industries, long playerId,
                                            String city, String industryType) {
        List<List<String>> slots = citySlotOptions().getOrDefault(city, List.of());
        int nextLevel = parseInteger(nextBuildableBoardTile(state, playerId, industryType).get("level"), 0);
        for (int index = 0; index < slots.size(); index++) {
            if (!slotHasDedicatedIndustry(city, industryType, index)) {
                continue;
            }
            if (slotAvailable(industries, city, industryType, index)) {
                return index;
            }
            if (!coverableOwnFlippedTile(industries, playerId, city, industryType, index, nextLevel).isEmpty()) {
                return index;
            }
            if (List.of("coal_mine", "iron_works").contains(industryType)
                    && !coverableOpponentResourceTile(state, playerId, city, industryType, index).isEmpty()) {
                return index;
            }
        }
        return -1;
    }

    private Map<String, Object> coverableOwnFlippedTile(List<Map<String, Object>> industries, long actorId, String city,
                                                        String industryType, int newLevel) {
        return industries.stream()
                .filter(tile -> city.equals(tile.get("city"))
                        && industryType.equals(tile.get("industryType"))
                        && asLong(tile.get("ownerId")) == actorId
                        && Boolean.TRUE.equals(tile.get("flipped"))
                        && parseInteger(tile.get("level"), 0) < newLevel)
                .findFirst()
                .orElse(new LinkedHashMap<>());
    }

    private Map<String, Object> coverableOwnFlippedTile(List<Map<String, Object>> industries, long actorId, String city,
                                                        String industryType, int slotIndex, int newLevel) {
        return industries.stream()
                .filter(tile -> city.equals(tile.get("city"))
                        && industryType.equals(tile.get("industryType"))
                        && parseInteger(tile.get("slotIndex"), -1) == slotIndex
                        && asLong(tile.get("ownerId")) == actorId
                        && Boolean.TRUE.equals(tile.get("flipped"))
                        && parseInteger(tile.get("level"), 0) < newLevel)
                .findFirst()
                .orElse(new LinkedHashMap<>());
    }

    private boolean slotHasDedicatedIndustry(String city, String industryType, int slotIndex) {
        List<List<String>> slots = citySlotOptions().getOrDefault(city, List.of());
        return slotIndex >= 0
                && slotIndex < slots.size()
                && slots.get(slotIndex).size() == 1
                && slots.get(slotIndex).contains(industryType);
    }

    private boolean slotAvailable(List<Map<String, Object>> industries, String city, String industryType, int slotIndex) {
        List<List<String>> slots = citySlotOptions().getOrDefault(city, List.of());
        if (slotIndex < 0 || slotIndex >= slots.size() || !slots.get(slotIndex).contains(industryType)) {
            return false;
        }
        return industries.stream()
                .noneMatch(tile -> city.equals(tile.get("city"))
                        && parseInteger(tile.get("slotIndex"), -1) == slotIndex);
    }

    private Map<String, Object> coverableOpponentResourceTile(Map<String, Object> state, long actorId, String city,
                                                              String industryType) {
        if (!resourceMarketEmpty(state, industryType) || resourceFactoriesHaveSupply(state, industryType)) {
            return new LinkedHashMap<>();
        }
        return listOf(mapOf(state.get("board")).get("industries")).stream()
                .filter(tile -> city.equals(tile.get("city"))
                        && industryType.equals(tile.get("industryType"))
                        && asLong(tile.get("ownerId")) != actorId)
                .findFirst()
                .orElse(new LinkedHashMap<>());
    }

    private Map<String, Object> coverableOpponentResourceTile(Map<String, Object> state, long actorId, String city,
                                                              String industryType, int slotIndex) {
        if (!resourceMarketEmpty(state, industryType) || resourceFactoriesHaveSupply(state, industryType)) {
            return new LinkedHashMap<>();
        }
        return listOf(mapOf(state.get("board")).get("industries")).stream()
                .filter(tile -> city.equals(tile.get("city"))
                        && industryType.equals(tile.get("industryType"))
                        && parseInteger(tile.get("slotIndex"), -1) == slotIndex
                        && asLong(tile.get("ownerId")) != actorId)
                .findFirst()
                .orElse(new LinkedHashMap<>());
    }

    private boolean resourceMarketEmpty(Map<String, Object> state, String industryType) {
        Map<String, Object> market = mapOf(state.get("market"));
        if ("coal_mine".equals(industryType)) {
            return objectList(market.get("coal")).isEmpty();
        }
        if ("iron_works".equals(industryType)) {
            return objectList(market.get("iron")).isEmpty();
        }
        return false;
    }

    private boolean cityConnectedToAnyMarket(Map<String, Object> board, String city) {
        return (MAP_MARKETS.isEmpty() ? fallbackMarkets() : MAP_MARKETS).stream()
                .map(market -> Objects.toString(market.get("city"), ""))
                .anyMatch(marketCity -> citiesConnected(board, city, marketCity));
    }

    private boolean resourceFactoriesHaveSupply(Map<String, Object> state, String industryType) {
        String resourceField = "coal_mine".equals(industryType) ? "coal" : "iron";
        return listOf(mapOf(state.get("board")).get("industries")).stream()
                .anyMatch(tile -> industryType.equals(tile.get("industryType"))
                        && parseInteger(tile.get(resourceField), 0) > 0);
    }

    private Map<String, Object> nextAvailableBoardTile(Map<String, Object> state, long playerId, String industryType) {
        List<Map<String, Object>> tiles = playerBoardTiles(state, playerId, industryType);
        if (tiles.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, industryNameCn(industryType) + "\u6ca1\u6709\u53ef\u5efa\u9020\u7684\u677f\u5757");
        }
        String era = Objects.toString(state.get("era"), "canal");
        Map<String, Object> tile = tiles.getFirst();
        if (!boardTileBuildableInEra(tile, era)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8be5\u677f\u5757\u4e0d\u80fd\u5728\u5f53\u524d\u65f6\u4ee3\u5efa\u9020");
        }
        return tile;
    }

    private boolean boardTileBuildableInEra(Map<String, Object> tile, String era) {
        int period = parseInteger(tile.get("period"), 0);
        if (period == 1) {
            return "canal".equals(era);
        }
        if (period == 2) {
            return "rail".equals(era);
        }
        return true;
    }

    private Map<String, Object> lowestDevelopableBoardTile(Map<String, Object> state, long playerId, String industryType) {
        List<Map<String, Object>> tiles = playerBoardTiles(state, playerId, industryType);
        if (tiles.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> lowest = tiles.getFirst();
        return Boolean.FALSE.equals(lowest.get("canDevelop")) ? new LinkedHashMap<>() : lowest;
    }

    private Map<String, Object> lowestDevelopableBoardTileOrThrow(Map<String, Object> state, long playerId, String industryType) {
        List<Map<String, Object>> tiles = playerBoardTiles(state, playerId, industryType);
        if (tiles.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> lowest = tiles.getFirst();
        if (Boolean.FALSE.equals(lowest.get("canDevelop"))) {
            if ("pottery".equals(industryType)) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "\u4f60\u4e0d\u80fd\u901a\u8fc7\u7814\u53d1\u884c\u52a8\u79fb\u9664"
                                + parseInteger(lowest.get("level"), 0)
                                + "\u7ea7\u9676\u74f7\u5382");
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, industryNameCn(industryType) + "\u5f53\u524d\u6700\u4f4e\u7b49\u7ea7\u677f\u5757\u4e0d\u80fd\u7814\u53d1");
        }
        return lowest;
    }

    private List<Map<String, Object>> playerBoardTiles(Map<String, Object> state, long playerId, String industryType) {
        Map<String, Object> playerBoards = mapOf(state.get("playerBoards"));
        Map<String, Object> playerBoard = mapOf(playerBoards.get(String.valueOf(playerId)));
        return listOf(playerBoard.get(industryType));
    }

    private void removeBoardTile(Map<String, Object> state, long playerId, String industryType, String tileId) {
        Map<String, Object> playerBoards = mapOf(state.get("playerBoards"));
        Map<String, Object> playerBoard = mapOf(playerBoards.get(String.valueOf(playerId)));
        List<Map<String, Object>> tiles = listOf(playerBoard.get(industryType));
        boolean removed = tiles.removeIf(tile -> tileId.equals(Objects.toString(tile.get("id"), "")));
        if (!removed) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "\u672a\u80fd\u79fb\u9664\u73a9\u5bb6\u677f\u5757");
        }
        playerBoard.put(industryType, tiles);
        playerBoards.put(String.valueOf(playerId), playerBoard);
        state.put("playerBoards", playerBoards);
    }

    private void incrementDevelopment(Map<String, Object> state, long playerId, String industryType) {
        Map<String, Object> developments = mapOf(state.get("developments"));
        Map<String, Object> playerDevelopments = mapOf(developments.get(String.valueOf(playerId)));
        playerDevelopments.put(industryType, parseInteger(playerDevelopments.get(industryType), 0) + 1);
        developments.put(String.valueOf(playerId), playerDevelopments);
        state.put("developments", developments);
    }

    private void payMoney(Map<String, Object> state, long playerId, int amount) {
        Map<String, Object> statsByPlayer = mapOf(state.get("playerStats"));
        Map<String, Object> stats = mapOf(statsByPlayer.get(String.valueOf(playerId)));
        int money = parseInteger(stats.get("money"), 0);
        if (money < amount) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "金钱不足");
        }
        stats.put("money", money - amount);
        stats.put("spentThisRound", parseInteger(stats.get("spentThisRound"), 0) + amount);
        statsByPlayer.put(String.valueOf(playerId), stats);
        state.put("playerStats", statsByPlayer);
    }

    private void discardCard(Map<String, Object> state, long playerId, String cardId) {
        Map<String, Object> hands = mapOf(state.get("hands"));
        List<Map<String, Object>> hand = listOf(hands.get(String.valueOf(playerId)));
        Map<String, Object> card = findCard(hand, cardId);
        if (card == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u627e\u4e0d\u5230\u8981\u5f03\u7f6e\u7684\u624b\u724c");
        }
        hand.removeIf(item -> cardId.equals(Objects.toString(item.get("id"), "")));
        if (isWildCard(card)) {
            List<Map<String, Object>> scoutPool = listOf(state.get("scoutPool"));
            scoutPool.add(card);
            state.put("scoutPool", scoutPool);
        } else {
            List<Map<String, Object>> discardPile = listOf(state.get("discardPile"));
            discardPile.add(card);
            state.put("discardPile", discardPile);
            decrementCardHint(state, card);
        }
        hands.put(String.valueOf(playerId), hand);
        state.put("hands", hands);
    }

    private static Map<String, Map<String, Object>> loadCityMetadata(Map<String, Object> map) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> city : staticListOf(map.get("city"))) {
            String name = displayCityName(Objects.toString(city.get("name"), ""));
            if (name.isBlank()) {
                continue;
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("name", name);
            meta.put("cnName", CITY_NAME_MAPPING.getOrDefault(name.toUpperCase(), Objects.toString(city.get("cn_name"), name)));
            meta.put("color", Objects.toString(city.get("color"), "white"));
            meta.put("type", Objects.toString(city.get("type"), ""));
            result.put(name, meta);
        }
        return result;
    }

    private String cityCnName(String city) {
        String raw = Objects.toString(city, "");
        String mapped = CITY_NAME_MAPPING.get(raw.toUpperCase());
        if (mapped != null) {
            return mapped;
        }
        return Objects.toString(MAP_CITY_METADATA.getOrDefault(raw, Map.of()).get("cnName"), raw);
    }

    private void decrementCardHint(Map<String, Object> state, Map<String, Object> card) {
        String key = cardHintKey(card);
        if (key.isBlank()) {
            return;
        }
        Map<String, Object> cardHints = mapOf(state.get("cardHints"));
        List<Map<String, Object>> hints = listOf(cardHints.get("cards"));
        for (Map<String, Object> hint : hints) {
            if (Objects.equals(hint.get("key"), key)) {
                hint.put("remaining", Math.max(0, parseInteger(hint.get("remaining"), 0) - 1));
                break;
            }
        }
        cardHints.put("cards", hints);
        state.put("cardHints", cardHints);
    }

    private String consumeMerchantBeer(Map<String, Object> state, long actorId, String merchantId, String saleCity,
                                       String industryType, int amount, String freeDevelopIndustryType) {
        Map<String, Object> market = mapOf(state.get("market"));
        List<Map<String, Object>> merchants = listOf(market.get("beerMerchants"));
        Map<String, Object> merchant = merchants.stream()
                .filter(item -> merchantId.equals(Objects.toString(item.get("id"), "")))
                .findFirst()
                .orElse(null);
        if (merchant == null || parseInteger(merchant.get("beer"), 0) < amount) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u627e\u4e0d\u5230\u53ef\u7528\u7684\u8d38\u6613\u5546\u5564\u9152");
        }
        if (!merchantAcceptsIndustry(merchant, industryType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8be5\u8d38\u6613\u5546\u4e0d\u63a5\u53d7\u6b64\u4ea7\u4e1a");
        }
        Map<String, Object> board = mapOf(state.get("board"));
        String merchantCity = Objects.toString(merchant.get("city"), "");
        if (!citiesConnected(board, saleCity, merchantCity)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u51fa\u552e\u57ce\u5e02\u672a\u8fde\u63a5\u5230\u8d38\u6613\u5546");
        }

        merchant.put("beer", parseInteger(merchant.get("beer"), 0) - amount);
        if (parseInteger(merchant.get("beer"), 0) == 0) {
            merchant.put("used", true);
        }
        String rewardNotice = applyMerchantReward(state, actorId, merchant, freeDevelopIndustryType);
        market.put("beerMerchants", merchants);
        state.put("market", market);
        return "\u4f7f\u7528 " + cityCnName(Objects.toString(merchant.get("city"), "")) + " \u8d38\u6613\u5546\u5564\u9152 x" + amount + "\u3002" + rewardNotice;
    }

    private void validateMerchantBeerForSale(Map<String, Object> state, long actorId, String merchantId, String saleCity,
                                             String industryType, int amount) {
        Map<String, Object> market = mapOf(state.get("market"));
        List<Map<String, Object>> merchants = listOf(market.get("beerMerchants"));
        Map<String, Object> merchant = merchants.stream()
                .filter(item -> merchantId.equals(Objects.toString(item.get("id"), "")))
                .findFirst()
                .orElse(null);
        if (merchant == null || parseInteger(merchant.get("beer"), 0) < amount) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u627e\u4e0d\u5230\u53ef\u7528\u7684\u8d38\u6613\u5546\u5564\u9152");
        }
        if (!merchantAcceptsIndustry(merchant, industryType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8be5\u8d38\u6613\u5546\u4e0d\u63a5\u53d7\u6b64\u4ea7\u4e1a");
        }
        Map<String, Object> board = mapOf(state.get("board"));
        String merchantCity = Objects.toString(merchant.get("city"), "");
        if (!citiesConnected(board, saleCity, merchantCity)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u51fa\u552e\u57ce\u5e02\u672a\u8fde\u63a5\u5230\u8d38\u6613\u5546");
        }
    }

    private boolean merchantAcceptsIndustry(Map<String, Object> merchant, String industryType) {
        if (Boolean.TRUE.equals(merchant.get("blank"))) {
            return false;
        }
        if (Boolean.TRUE.equals(merchant.get("wild"))) {
            return SELLABLE_INDUSTRIES.contains(industryType);
        }
        List<String> acceptedIndustryTypes = stringList(merchant.get("acceptedIndustryTypes"));
        return acceptedIndustryTypes.contains(industryType);
    }

    private String applyMerchantReward(Map<String, Object> state, long actorId, Map<String, Object> merchant,
                                       String freeDevelopIndustryType) {
        String reward = Objects.toString(merchant.get("reward"), "");
        if (reward.contains("Income")) {
            int amount = firstNumber(reward, 2);
            addIncome(state, actorId, amount);
            return "\u6536\u5165\u7b49\u7ea7 +" + amount + "\u3002";
        }
        if (reward.contains("VP")) {
            int amount = firstNumber(reward, 0);
            addVictoryPoints(state, actorId, amount);
            return "\u83b7\u5f97 " + amount + "VP\u3002";
        }
        if (reward.contains("5") && "Warrington".equals(Objects.toString(merchant.get("city"), ""))) {
            gainMoney(state, actorId, 5);
            return "\u83b7\u5f975\u82f1\u9551\u3002";
        }
        if (reward.contains("Develop")) {
            String industryType = freeDevelopIndustryType.isBlank()
                    ? freeDevelopFirstAvailable(state, actorId)
                    : freeDevelopSelected(state, actorId, freeDevelopIndustryType);
            return industryType.isBlank() ? "\u6ca1\u6709\u53ef\u514d\u8d39\u7814\u53d1\u7684\u677f\u5757\u3002" : "\u514d\u8d39\u7814\u53d1 " + industryNameCn(industryType) + "\u3002";
        }
        return "";
    }

    private String freeDevelopSelected(Map<String, Object> state, long actorId, String industryType) {
        if (!INDUSTRY_TYPES.contains(industryType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u4ea7\u4e1a\u7c7b\u578b\u4e0d\u5408\u6cd5");
        }
        Map<String, Object> tile = lowestDevelopableBoardTile(state, actorId, industryType);
        if (tile.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, industryNameCn(industryType) + "\u6ca1\u6709\u53ef\u514d\u8d39\u7814\u53d1\u7684\u677f\u5757");
        }
        removeBoardTile(state, actorId, industryType, Objects.toString(tile.get("id"), ""));
        incrementDevelopment(state, actorId, industryType);
        return industryType;
    }

    private String freeDevelopFirstAvailable(Map<String, Object> state, long actorId) {
        for (String industryType : INDUSTRY_TYPES) {
            Map<String, Object> tile = lowestDevelopableBoardTile(state, actorId, industryType);
            if (!tile.isEmpty()) {
                return freeDevelopSelected(state, actorId, industryType);
            }
        }
        return "";
    }

    private String consumeBeer(Map<String, Object> state, List<Map<String, Object>> industries, long actorId,
                               String preferredTileId, String saleCity, int amount) {
        Map<String, Object> board = mapOf(state.get("board"));
        int remaining = amount;
        List<String> notices = new ArrayList<>();
        Map<String, Object> source = null;
        if (!preferredTileId.isBlank()) {
            source = findTile(industries, preferredTileId);
            if (source == null
                    || !"brewery".equals(source.get("industryType"))
                    || parseInteger(source.get("beer"), 0) < remaining
                    || !beerSourceReachable(board, source, saleCity, actorId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "\u6307\u5b9a\u7684\u5564\u9152\u6765\u6e90\u4e0d\u53ef\u7528");
            }
            notices.add(consumeBeerFromTile(state, source, remaining));
            return String.join("", notices);
        }
        if (source == null) {
            source = industries.stream()
                    .filter(tile -> "brewery".equals(tile.get("industryType"))
                            && parseInteger(tile.get("beer"), 0) > 0
                            && asLong(tile.get("ownerId")) == actorId)
                    .findFirst()
                    .orElse(null);
        }
        if (source != null) {
            int consumed = Math.min(remaining, parseInteger(source.get("beer"), 0));
            notices.add(consumeBeerFromTile(state, source, consumed));
            remaining -= consumed;
        }
        while (remaining > 0) {
            source = industries.stream()
                    .filter(tile -> "brewery".equals(tile.get("industryType"))
                            && parseInteger(tile.get("beer"), 0) > 0
                            && beerSourceReachable(board, tile, saleCity, actorId))
                    .findFirst()
                    .orElse(null);
            if (source == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "\u6ca1\u6709\u53ef\u7528\u7684\u5564\u9152\u6765\u6e90");
            }
            int consumed = Math.min(remaining, parseInteger(source.get("beer"), 0));
            notices.add(consumeBeerFromTile(state, source, consumed));
            remaining -= consumed;
        }
        return String.join("", notices);
    }

    private String consumeNetworkBeer(Map<String, Object> state, List<Map<String, Object>> industries, long actorId,
                                      String preferredTileId, List<String> destinations, int amount) {
        Map<String, Object> board = mapOf(state.get("board"));
        int remaining = amount;
        List<String> notices = new ArrayList<>();
        Map<String, Object> source = null;
        if (!preferredTileId.isBlank()) {
            source = findTile(industries, preferredTileId);
            if (source == null
                    || !"brewery".equals(source.get("industryType"))
                    || parseInteger(source.get("beer"), 0) < remaining
                    || !beerSourceReachableToAny(board, source, destinations, actorId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "\u6307\u5b9a\u7684\u5564\u9152\u6765\u6e90\u4e0d\u53ef\u7528");
            }
            notices.add(consumeBeerFromTile(state, source, remaining));
            board.put("industries", industries);
            state.put("board", board);
            return String.join("", notices);
        }
        source = industries.stream()
                .filter(tile -> "brewery".equals(tile.get("industryType"))
                        && parseInteger(tile.get("beer"), 0) > 0
                        && asLong(tile.get("ownerId")) == actorId)
                .findFirst()
                .orElse(null);
        if (source != null) {
            int consumed = Math.min(remaining, parseInteger(source.get("beer"), 0));
            notices.add(consumeBeerFromTile(state, source, consumed));
            remaining -= consumed;
        }
        while (remaining > 0) {
            source = industries.stream()
                    .filter(tile -> "brewery".equals(tile.get("industryType"))
                            && parseInteger(tile.get("beer"), 0) > 0
                            && beerSourceReachableToAny(board, tile, destinations, actorId))
                    .findFirst()
                    .orElse(null);
            if (source == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "\u6ca1\u6709\u53ef\u7528\u7684\u5564\u9152\u6765\u6e90");
            }
            int consumed = Math.min(remaining, parseInteger(source.get("beer"), 0));
            notices.add(consumeBeerFromTile(state, source, consumed));
            remaining -= consumed;
        }
        board.put("industries", industries);
        return String.join("", notices);
    }

    private String consumeBeerFromTile(Map<String, Object> state, Map<String, Object> source, int amount) {
        source.put("beer", parseInteger(source.get("beer"), 0) - amount);
        String notice = "\u4f7f\u7528 " + source.get("ownerName") + " \u5728 " + cityCnName(Objects.toString(source.get("city"), "")) + " \u7684\u5564\u9152 x" + amount + "\u3002";
        if (parseInteger(source.get("beer"), 0) == 0) {
            flipTile(state, source);
            notice += "\u8be5\u917f\u9152\u5382\u5df2\u7ffb\u9762\u3002";
        }
        return notice;
    }

    private String consumeCoal(Map<String, Object> state, long actorId, String preferredTileId, List<String> destinations) {
        Map<String, Object> board = mapOf(state.get("board"));
        List<Map<String, Object>> industries = listOf(board.get("industries"));
        Map<String, Object> source = null;
        if (!preferredTileId.isBlank()) {
            source = findTile(industries, preferredTileId);
            if (source == null
                    || !"coal_mine".equals(source.get("industryType"))
                    || parseInteger(source.get("coal"), 0) <= 0
                    || !coalSourceReachable(board, source, destinations)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "\u6307\u5b9a\u7684\u7164\u6765\u6e90\u4e0d\u53ef\u7528");
            }
        }
        if (source == null) {
            source = industries.stream()
                    .filter(tile -> "coal_mine".equals(tile.get("industryType"))
                            && parseInteger(tile.get("coal"), 0) > 0
                            && asLong(tile.get("ownerId")) == actorId
                            && coalSourceReachable(board, tile, destinations))
                    .findFirst()
                    .orElse(null);
        }
        if (source == null) {
            source = industries.stream()
                    .filter(tile -> "coal_mine".equals(tile.get("industryType"))
                            && parseInteger(tile.get("coal"), 0) > 0
                            && coalSourceReachable(board, tile, destinations))
                    .findFirst()
                    .orElse(null);
        }
        if (source != null) {
            source.put("coal", parseInteger(source.get("coal"), 0) - 1);
            String notice = "\u4f7f\u7528 " + source.get("ownerName") + " \u5728 " + cityCnName(Objects.toString(source.get("city"), "")) + " \u7684\u7164\u3002";
            if (parseInteger(source.get("coal"), 0) == 0) {
                flipTile(state, source);
                notice += "\u8be5\u7164\u77ff\u5df2\u7ffb\u9762\u3002";
            }
            board.put("industries", industries);
            state.put("board", board);
            return notice;
        }
        if (!coalMarketReachable(board, destinations)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u65e0\u6cd5\u83b7\u5f97\u7164\uff1a\u6240\u9700\u4f4d\u7f6e\u672a\u8fde\u63a5\u5230\u4efb\u4f55\u7164\u77ff\u6216\u5e02\u573a");
        }
        Map<String, Object> market = mapOf(state.get("market"));
        List<Object> coalMarket = objectList(market.get("coal"));
        if (!coalMarket.isEmpty()) {
            int price = parseInteger(coalMarket.remove(0), 0);
            payMoney(state, actorId, price);
            market.put("coal", coalMarket);
            state.put("market", market);
            return "\u4ece\u7164\u5e02\u573a\u8d2d\u4e70\u7164\uff0c\u82b1\u8d39 " + price + "\u82f1\u9551\u3002";
        }
        payMoney(state, actorId, DISTANT_COAL_PRICE);
        state.put("market", market);
        return "\u4ece\u8fdc\u65b9\u5e02\u573a\u8d2d\u4e70\u7164\uff0c\u82b1\u8d39 " + DISTANT_COAL_PRICE + "\u82f1\u9551\u3002";
    }

    private boolean coalMarketReachable(Map<String, Object> board, List<String> destinations) {
        if (destinations == null || destinations.isEmpty()) {
            return false;
        }
        List<Map<String, Object>> markets = MAP_MARKETS.isEmpty() ? fallbackMarkets() : MAP_MARKETS;
        return destinations.stream()
                .filter(destination -> !destination.isBlank())
                .anyMatch(destination -> markets.stream()
                        .map(market -> Objects.toString(market.get("city"), ""))
                        .filter(city -> !city.isBlank())
                        .anyMatch(city -> citiesConnected(board, destination, city)));
    }

    private String consumeIron(Map<String, Object> state, long actorId, String preferredTileId) {
        Map<String, Object> board = mapOf(state.get("board"));
        List<Map<String, Object>> industries = listOf(board.get("industries"));
        Map<String, Object> source = null;
        if (!preferredTileId.isBlank()) {
            source = findTile(industries, preferredTileId);
            if (source == null || !"iron_works".equals(source.get("industryType")) || parseInteger(source.get("iron"), 0) <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "\u6307\u5b9a\u7684\u94c1\u6765\u6e90\u4e0d\u53ef\u7528");
            }
        }
        if (source == null) {
            source = industries.stream()
                    .filter(tile -> "iron_works".equals(tile.get("industryType"))
                            && parseInteger(tile.get("iron"), 0) > 0
                            && asLong(tile.get("ownerId")) == actorId)
                    .findFirst()
                    .orElse(null);
        }
        if (source == null) {
            source = industries.stream()
                    .filter(tile -> "iron_works".equals(tile.get("industryType"))
                            && parseInteger(tile.get("iron"), 0) > 0)
                    .findFirst()
                    .orElse(null);
        }
        if (source != null) {
            source.put("iron", parseInteger(source.get("iron"), 0) - 1);
            String notice = "\u4f7f\u7528 " + source.get("ownerName") + " \u5728 " + cityCnName(Objects.toString(source.get("city"), "")) + " \u7684\u94c1\u3002";
            if (parseInteger(source.get("iron"), 0) == 0) {
                flipTile(state, source);
                notice += "\u8be5\u94c1\u5382\u5df2\u7ffb\u9762\u3002";
            }
            board.put("industries", industries);
            state.put("board", board);
            return notice;
        }
        Map<String, Object> market = mapOf(state.get("market"));
        List<Object> ironMarket = objectList(market.get("iron"));
        if (!ironMarket.isEmpty()) {
            int price = parseInteger(ironMarket.remove(0), 0);
            payMoney(state, actorId, price);
            market.put("iron", ironMarket);
            state.put("market", market);
            return "\u4ece\u94c1\u5e02\u573a\u8d2d\u4e70\u94c1\uff0c\u82b1\u8d39 " + price + "\u82f1\u9551\u3002";
        }
        payMoney(state, actorId, DISTANT_IRON_PRICE);
        state.put("market", market);
        return "\u4ece\u8fdc\u65b9\u5e02\u573a\u8d2d\u4e70\u94c1\uff0c\u82b1\u8d39 " + DISTANT_IRON_PRICE + "\u82f1\u9551\u3002";
    }

    private List<String> sellNewResourceToMarket(Map<String, Object> state, Map<String, Object> tile) {
        if (!isDepleteTile(tile)) {
            return List.of();
        }
        String industryType = Objects.toString(tile.get("industryType"), "");
        if ("iron_works".equals(industryType)) {
            return sellIronToMarket(state, tile);
        }
        if ("coal_mine".equals(industryType)) {
            return sellCoalToMarket(state, tile);
        }
        return List.of();
    }

    private List<String> sellIronToMarket(Map<String, Object> state, Map<String, Object> tile) {
        int iron = parseInteger(tile.get("iron"), 0);
        if (iron <= 0) {
            return List.of();
        }
        Map<String, Object> market = mapOf(state.get("market"));
        List<Object> ironMarket = objectList(market.get("iron"));
        int sold = 0;
        int gained = 0;
        while (iron > 0 && ironMarket.size() < MAX_IRON_MARKET_SIZE) {
            int price = marketRestockPrice(FULL_IRON_MARKET, ironMarket.size());
            gainMoney(state, asLong(tile.get("ownerId")), price);
            gained += price;
            ironMarket.add(0, price);
            iron--;
            sold++;
        }
        tile.put("iron", iron);
        market.put("iron", ironMarket);
        state.put("market", market);
        if (sold <= 0) {
            return List.of();
        }
        List<String> notices = new ArrayList<>();
        notices.add("玩家" + tile.get("ownerName") + "因" + cityCnName(Objects.toString(tile.get("city"), ""))
                + "的钢铁厂向市场卖出" + sold + "个铁，获得" + gained + "英镑");
        if (iron == 0) {
            notices.add(flipTileWithoutNotice(state, tile));
        }
        return notices.stream().filter(notice -> !notice.isBlank()).toList();
    }

    private List<String> sellCoalToMarket(Map<String, Object> state, Map<String, Object> tile) {
        int coal = parseInteger(tile.get("coal"), 0);
        if (coal <= 0 || !cityConnectedToAnyMarket(mapOf(state.get("board")), Objects.toString(tile.get("city"), ""))) {
            return List.of();
        }
        Map<String, Object> market = mapOf(state.get("market"));
        List<Object> coalMarket = objectList(market.get("coal"));
        int sold = 0;
        int gained = 0;
        while (coal > 0 && coalMarket.size() < MAX_COAL_MARKET_SIZE) {
            int price = marketRestockPrice(FULL_COAL_MARKET, coalMarket.size());
            gainMoney(state, asLong(tile.get("ownerId")), price);
            gained += price;
            coalMarket.add(0, price);
            coal--;
            sold++;
        }
        tile.put("coal", coal);
        market.put("coal", coalMarket);
        state.put("market", market);
        if (sold <= 0) {
            return List.of();
        }
        List<String> notices = new ArrayList<>();
        notices.add("玩家" + tile.get("ownerName") + "因" + cityCnName(Objects.toString(tile.get("city"), ""))
                + "的煤矿场向市场卖出" + sold + "个煤，获得" + gained + "英镑");
        if (coal == 0) {
            notices.add(flipTileWithoutNotice(state, tile));
        }
        return notices.stream().filter(notice -> !notice.isBlank()).toList();
    }

    private int marketRestockPrice(List<Integer> fullMarket, int currentSize) {
        int missingIndex = Math.max(0, fullMarket.size() - currentSize - 1);
        return fullMarket.get(missingIndex);
    }

    private void flipTile(Map<String, Object> state, Map<String, Object> tile) {
        if (!Boolean.TRUE.equals(tile.get("flipped")) && List.of("coal_mine", "iron_works", "brewery").contains(Objects.toString(tile.get("industryType"), ""))) {
            appendNotice(state, "玩家" + tile.get("ownerName") + " 在 " + cityCnName(Objects.toString(tile.get("city"), ""))
                    + " 的 " + industryNameCn(Objects.toString(tile.get("industryType"), ""))
                    + " 因产能消耗完毕而翻面");
        }
        flipTileByOwnerMap(tile);
        addIncome(state, asLong(tile.get("ownerId")), parseInteger(tile.get("incomeReward"), 0));
    }

    private String flipTileWithoutNotice(Map<String, Object> state, Map<String, Object> tile) {
        if (Boolean.TRUE.equals(tile.get("flipped"))) {
            return "";
        }
        String notice = "";
        if (List.of("coal_mine", "iron_works", "brewery").contains(Objects.toString(tile.get("industryType"), ""))) {
            notice = "玩家" + tile.get("ownerName") + " 在 " + cityCnName(Objects.toString(tile.get("city"), ""))
                    + " 的 " + industryNameCn(Objects.toString(tile.get("industryType"), ""))
                    + " 因产能消耗完毕而翻面";
        }
        flipTileByOwnerMap(tile);
        addIncome(state, asLong(tile.get("ownerId")), parseInteger(tile.get("incomeReward"), 0));
        return notice;
    }

    private void flipTileByOwnerMap(Map<String, Object> tile) {
        if (Boolean.TRUE.equals(tile.get("flipped"))) {
            return;
        }
        tile.put("flipped", true);
        tile.put("coal", 0);
        tile.put("iron", 0);
        tile.put("beer", 0);
    }

    private void addIncome(Map<String, Object> state, long playerId, int incomeReward) {
        Map<String, Object> statsByPlayer = mapOf(state.get("playerStats"));
        Map<String, Object> stats = mapOf(statsByPlayer.get(String.valueOf(playerId)));
        int incomeLevel = parseInteger(stats.get("incomeLevel"), 0);
        stats.put("incomeLevel", Math.min(MAX_INCOME_LEVEL, incomeLevel + Math.max(0, incomeReward)));
        stats.put("income", incomeForLevel(parseInteger(stats.get("incomeLevel"), 0)));
        statsByPlayer.put(String.valueOf(playerId), stats);
        state.put("playerStats", statsByPlayer);
    }

    private void gainMoney(Map<String, Object> state, long playerId, int amount) {
        Map<String, Object> statsByPlayer = mapOf(state.get("playerStats"));
        Map<String, Object> stats = mapOf(statsByPlayer.get(String.valueOf(playerId)));
        stats.put("money", parseInteger(stats.get("money"), 0) + amount);
        statsByPlayer.put(String.valueOf(playerId), stats);
        state.put("playerStats", statsByPlayer);
    }

    private int playerMoney(Map<String, Object> state, long playerId) {
        return parseInteger(mapOf(mapOf(state.get("playerStats")).get(String.valueOf(playerId))).get("money"), 0);
    }

    private String merchantCityText(Map<String, Object> state, String merchantId, String fallbackCity) {
        if (merchantId == null || merchantId.isBlank()) {
            return cityCnName(fallbackCity);
        }
        return listOf(mapOf(state.get("market")).get("beerMerchants")).stream()
                .filter(merchant -> merchantId.equals(Objects.toString(merchant.get("id"), "")))
                .map(merchant -> cityCnName(Objects.toString(merchant.get("city"), fallbackCity)))
                .findFirst()
                .orElseGet(() -> cityCnName(fallbackCity));
    }

    private void addVictoryPoints(Map<String, Object> state, long playerId, int amount) {
        Map<String, Object> statsByPlayer = mapOf(state.get("playerStats"));
        Map<String, Object> stats = mapOf(statsByPlayer.get(String.valueOf(playerId)));
        stats.put("victoryPoints", parseInteger(stats.get("victoryPoints"), 0) + amount);
        statsByPlayer.put(String.valueOf(playerId), stats);
        state.put("playerStats", statsByPlayer);
    }

    private int firstNumber(String value, int fallback) {
        String digits = value.replaceAll("\\D+", " ").trim();
        if (digits.isBlank()) {
            return fallback;
        }
        return parseInteger(digits.split(" ")[0], fallback);
    }

    private void discardActionCard(Map<String, Object> state, long playerId, Map<String, Object> payload) {
        String cardId = Objects.toString(payload.get("cardId"), "");
        if (cardId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8bf7\u9009\u62e9\u8981\u5f03\u7f6e\u7684\u624b\u724c");
        }
        discardCard(state, playerId, cardId);
    }

    private void drawCards(Map<String, Object> state, long playerId, int count) {
        Map<String, Object> hands = mapOf(state.get("hands"));
        List<Map<String, Object>> hand = listOf(hands.get(String.valueOf(playerId)));
        List<Map<String, Object>> deck = listOf(state.get("deck"));
        for (int index = 0; index < count && !deck.isEmpty(); index++) {
            hand.add(deck.remove(0));
        }
        hands.put(String.valueOf(playerId), hand);
        state.put("hands", hands);
        state.put("deck", deck);
    }

    private void drawHandToLimit(Map<String, Object> state, long playerId, int limit) {
        Map<String, Object> hands = mapOf(state.get("hands"));
        List<Map<String, Object>> hand = listOf(hands.get(String.valueOf(playerId)));
        int drawCount = Math.max(0, limit - hand.size());
        drawCards(state, playerId, drawCount);
    }

    private String playerColor(Map<String, Object> state, long playerId) {
        return listOf(state.get("players")).stream()
                .filter(player -> asLong(player.get("userId")) == playerId)
                .map(player -> Objects.toString(player.get("color"), ""))
                .filter(color -> !color.isBlank())
                .findFirst()
                .orElse("red");
    }

    private String playerColorLabel(String color) {
        return switch (color) {
            case "red" -> "\u7ea2\u8272";
            case "yellow" -> "\u9ec4\u8272";
            case "blue" -> "\u84dd\u8272";
            case "purple" -> "\u7d2b\u8272";
            default -> color;
        };
    }

    private void ensurePlaying(Map<String, Object> state) {
        if (!"playing".equals(state.get("phase"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u6e38\u620f\u5c1a\u672a\u5f00\u59cb");
        }
    }

    private void ensureCurrentPlayer(Map<String, Object> state, UserSummary actor) {
        if (asLong(state.get("currentPlayerId")) != actor.id()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8fd8\u6ca1\u6709\u8f6e\u5230\u4f60");
        }
    }

    private void ensureTurnAcceptsAction(Map<String, Object> state) {
        if (Boolean.TRUE.equals(mapOf(state.get("turn")).get("awaitingEndTurn"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前回合行动已完成，请先结束回合");
        }
    }

    private void appendNotice(Map<String, Object> state, String notice) {
        List<String> notices = new ArrayList<>();
        for (Object item : objectList(state.get("notices"))) {
            notices.add(String.valueOf(item));
        }
        notices.add(0, notice);
        state.put("notices", notices.stream().limit(MAX_NOTICE_COUNT).toList());
    }

    private void appendActionLog(Map<String, Object> state, UserSummary actor, String type, String summary) {
        List<Map<String, Object>> log = listOf(state.get("actionLog"));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("actorId", actor.id());
        item.put("actorName", actor.username());
        item.put("type", type);
        item.put("summary", summary);
        item.put("era", state.get("era"));
        item.put("round", state.get("round"));
        item.put("actionsTaken", mapOf(state.get("turn")).getOrDefault("actionsTaken", 0));
        log.add(0, item);
        state.put("actionLog", log.stream().limit(30).toList());
    }

    private String playerName(List<Map<String, Object>> players, long userId) {
        return players.stream()
                .filter(player -> asLong(player.get("userId")) == userId)
                .map(player -> Objects.toString(player.get("username"), ""))
                .findFirst()
                .orElse("");
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private int parseInteger(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOf(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOf(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> copied = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    copied.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
            return copied;
        }
        return new ArrayList<>();
    }

    private List<Object> objectList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    private List<Long> longList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(this::asLong).toList();
        }
        return new ArrayList<>();
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copied = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copied.put(String.valueOf(entry.getKey()), deepCopy(entry.getValue()));
            }
            return copied;
        }
        if (value instanceof List<?> list) {
            List<Object> copied = new ArrayList<>();
            for (Object item : list) {
                copied.add(deepCopy(item));
            }
            return copied;
        }
        return value;
    }
}
