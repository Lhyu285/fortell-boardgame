package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassBoardView.Network;
import java.util.*;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;
import static com.fortell.boardgame.game_modules.brass.BrassQueryModel.*;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;
import static com.fortell.boardgame.game_modules.brass.BrassResourceSources.*;

/** Scoped query decoder/encoder. Preserve branch-specific fields and raw legacy scalar shapes. */
final class BrassQueryAdapter {
    private BrassQueryAdapter() {}
    static Input input(Map<String, Object> state) {
        // Inactive legacy documents only exposed the raw actor field; do not decode unrelated components.
        if (!"playing".equals(state.get("phase"))) return new Input(new Context(Shape.INACTIVE, 0, 0, 0, 0,
                false, false, false, false, 0), "", 0,
                new BrassResourceModel.ResourceContext(new BrassResourceModel.ResourceBoard(new Network(List.of()), List.of()), List.of()),
                List.of(), List.of(), List.of(), Map.of(), Map.of(), List.of(), Set.of(), List.of(), 0, List.of());
        long actor = asLong(state.get("currentPlayerId"));
        var hand = BrassCardService.currentHand(state, actor); var turn = viewMap(state.get("turn"));
        Shape shape = !viewMap(state.get("incomeDebt")).isEmpty() ? Shape.DEBT
                : Boolean.TRUE.equals(turn.get("awaitingEndTurn")) ? Shape.END_TURN
                : Boolean.TRUE.equals(turn.get("sellInProgress")) ? Shape.SELLING : Shape.NORMAL;
        var board = viewMap(state.get("board"));
        var stats = BrassPersonalAdapter.stats(state, actor, STARTING_INCOME_LEVEL);
        boolean maintenance = BrassTurnService.canMaintainEra(state);
        int taken = parseInteger(turn.get("actionsTaken"), 0);
        var context = new Context(shape, actor, hand.size(), taken, parseInteger(turn.get("sellCount"), 0),
                !viewMap(state.get("turnStartSnapshot")).isEmpty() && taken > 0, maintenance,
                stats.incomeLevel() >= 3 && INCOME_LEVEL_AFTER_LOAN.containsKey(stats.incomeLevel()),
                BrassCardService.canScout(state, hand), Math.max(0, MAX_PLAYER_LINKS - (int) BrassTypedConnectivity.playerLinkCount(
                        BrassBoardAdapter.network(board).links(), actor)));
        var supply = new LinkedHashMap<String, Supply>(); var empty = new HashSet<String>();
        if (shape == Shape.NORMAL && !maintenance) for (String type : INDUSTRY_TYPES) {
            var tile = BrassIndustryService.nextBuildableBoardTile(state, actor, type);
            supply.put(type, new Supply(!tile.isEmpty(), parseInteger(tile.get("level"), 0), parseInteger(tile.get("cost"), 0),
                    !BrassIndustryService.lowestDevelopableBoardTile(state, actor, type).isEmpty()));
            if (BrassIndustryService.resourceMarketEmpty(state, type)) empty.add(type);
        }
        return new Input(context, Objects.toString(state.get("era"), "canal"), stats.money(), BrassResourceAdapter.context(state),
                BrassBoardAdapter.sites(board), BrassActionAdapter.routeRules(), BrassPersonalAdapter.cards(hand), supply,
                citySlotOptions(), INDUSTRY_TYPES, empty,
                objectList(viewMap(state.get("market")).get("coal")).stream().map(value -> parseInteger(value, 0)).toList(),
                DISTANT_COAL_PRICE, BrassResourceAdapter.coalMarkets());
    }

    static Map<String, Object> encode(Map<String, Object> state, Result query) {
        var result = new LinkedHashMap<String, Object>(); var c = query.context();
        result.put("currentPlayerId", c.shape() == Shape.INACTIVE ? state.get("currentPlayerId") : c.actor());
        result.put("actions", query.actions().stream().map(action -> action.key).toList());
        if (c.shape() == Shape.INACTIVE) return result;
        if (c.shape() == Shape.DEBT) {
            result.put("incomeDebt", mapOf(state.get("incomeDebt")));
            result.put("incomeDebtTiles", BrassPlayerService.ownedMapIndustries(state, c.actor())); return result;
        }
        if (c.shape() == Shape.END_TURN) {
            result.put("handSize", c.handSize());
            for (String key : List.of("buildOptions", "sellTiles", "networkRoutes", "developIndustries")) result.put(key, List.of());
            result.put("resourceSources", new LinkedHashMap<>()); return result;
        }
        result.put("requiresCard", c.shape() == Shape.SELLING ? List.of("build", "network", "develop", "loan", "skip")
                : List.of("build", "sell", "network", "develop", "loan", "skip"));
        result.put("maintenanceReady", c.shape() != Shape.SELLING && c.maintenanceReady());
        result.put("handSize", c.handSize());
        if (c.shape() == Shape.SELLING) { result.put("sellInProgress", true); result.put("sellCount", c.sellCount()); }
        result.put("buildOptions", query.builds().stream().map(candidate -> build(state, c.actor(), candidate)).toList());
        var industries = listOf(viewMap(state.get("board")).get("industries"));
        result.put("sellTiles", query.sales().stream().map(index -> sale(state, c.actor(), industries.get(index))).toList());
        String type = eraLinkType(Objects.toString(state.get("era"), "canal"));
        result.put("networkRoutes", query.routes().stream().map(route -> route(state, route, type)).toList());
        result.put("networkRoutePairs", query.pairs().stream().map(pair -> pair(state, c.actor(), pair)).toList());
        result.put("remainingLinks", c.remainingLinks());
        result.put("developIndustries", query.developments().stream().map(industry -> development(state, c.actor(), industry)).toList());
        result.put("resourceSources", availableResourceSources(state));
        return result;
    }

    private static Map<String, Object> build(Map<String, Object> state, long actor, Build candidate) {
        var item = new LinkedHashMap<String, Object>();
        item.put("city", candidate.city()); item.put("industryType", candidate.industryType());
        item.put("industryName", industryNameCn(candidate.industryType())); item.put("slotIndex", candidate.slotIndex());
        item.put("coversOpponent", candidate.coversOpponent()); item.put("coversOwn", candidate.coversOwn());
        var tile = BrassIndustryService.nextBuildableBoardTile(state, actor, candidate.industryType());
        for (String key : List.of("level", "cost", "coalCost", "ironCost")) item.put(key, tile.get(key));
        var board = mapOf(state.get("board"));
        // Match execution: a covered tile's remaining resources are discarded, not consumed.
        board.put("industries", listOf(board.get("industries")).stream()
                .filter(old -> !candidate.city().equals(Objects.toString(old.get("city"), ""))
                        || parseInteger(old.get("slotIndex"), 0) != candidate.slotIndex()).toList());
        item.put("coalSources", coalSourceItems(board, List.of(candidate.city())));
        item.put("ironSources", ironSourceItems(listOf(board.get("industries")))); item.put("cardIds", candidate.cardIds());
        return item;
    }
    private static Map<String, Object> sale(Map<String, Object> state, long actor, Map<String, Object> tile) {
        var item = new LinkedHashMap<String, Object>();
        for (String key : List.of("id", "city", "industryType", "industryName", "saleBeerCost")) item.put(key, tile.get(key));
        item.put("beerSources", BrassIndustryService.saleBeerRequired(tile) == 0 ? List.of()
                : beerSourceItems(viewMap(state.get("board")), Objects.toString(tile.get("city"), ""), actor));
        item.put("merchantSources", merchantSourceItems(state, tile)); return item;
    }
    private static Map<String, Object> stage(Map<String, Object> state, Network network) {
        var board = mapOf(state.get("board"));
        board.put("links", network.links().stream().map(link -> Map.<String, Object>of("from", link.from(), "to", link.to(),
                "ownerId", link.ownerId(), "type", link.type())).toList()); return board;
    }
    private static Map<String, Object> route(Map<String, Object> state, RouteOption option, String type) {
        var item = routeEnds(option);
        item.put("type", type); item.put("coalCost", "rail".equals(type) ? 1 : 0); item.put("moneyCost", "rail".equals(type) ? 5 : 3);
        if ("rail".equals(type)) item.put("coalSources", coalSourceItems(stage(state, option.stage()), List.of(option.route().from(), option.route().to())));
        return item;
    }
    private static Map<String, Object> routeEnds(RouteOption option) {
        var item = new LinkedHashMap<String, Object>(); item.put("from", option.route().from()); item.put("to", option.route().to()); return item;
    }
    private static Map<String, Object> pair(Map<String, Object> state, long actor, RoutePair pair) {
        var item = new LinkedHashMap<String, Object>(); var routeItems = new ArrayList<Map<String, Object>>();
        for (var option : List.of(pair.first(), pair.second())) {
            var route = routeEnds(option);
            route.put("coalSources", coalSourceItems(stage(state, option.stage()), List.of(option.route().from(), option.route().to())));
            routeItems.add(route);
        }
        item.put("routes", routeItems); item.put("type", "rail"); item.put("moneyCost", 15); item.put("coalCost", 2); item.put("beerCost", 1);
        item.put("beerSources", beerSourceItemsToAny(stage(state, pair.second().stage()), List.of(pair.first().route().from(),
                pair.first().route().to(), pair.second().route().from(), pair.second().route().to()), actor)); return item;
    }
    private static Map<String, Object> development(Map<String, Object> state, long actor, String type) {
        var item = new LinkedHashMap<String, Object>(); item.put("industryType", type); item.put("industryName", industryNameCn(type));
        item.put("nextRemovedLevel", BrassIndustryService.lowestDevelopableBoardTile(state, actor, type).get("level")); item.put("ironCost", 1);
        item.put("ironSources", ironSourceItems(listOf(viewMap(state.get("board")).get("industries")))); return item;
    }
}
