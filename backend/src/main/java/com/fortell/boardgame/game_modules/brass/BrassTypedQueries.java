package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassBoardView.*;
import java.util.*;
import static com.fortell.boardgame.game_modules.brass.BrassQueryModel.*;
import static com.fortell.boardgame.game_modules.brass.BrassResourceModel.*;
import static com.fortell.boardgame.game_modules.brass.BrassActionType.*;

/** Pure query selection. Wire aliases and legacy scalar representations belong to the encoder. */
final class BrassTypedQueries {
    private BrassTypedQueries() {}

    static Result query(Input input, Probe probe) {
        Context c = input.context();
        var actions = new ArrayList<BrassActionType>();
        var builds = List.<Build>of(); var sales = List.<Integer>of();
        var routes = List.<RouteOption>of(); var pairs = List.<RoutePair>of();
        var developments = List.<String>of();
        switch (c.shape()) {
            case INACTIVE -> { }
            case DEBT -> actions.add(RESOLVE_INCOME_DEBT);
            case END_TURN -> { actions.add(RESTART_TURN); actions.add(END_TURN); }
            case SELLING -> {
                if (c.restartAvailable()) actions.add(RESTART_TURN);
                sales = sales(input, probe);
                if (!sales.isEmpty()) actions.add(SELL);
                if (c.sellCount() > 0) actions.add(END_SELL);
            }
            case NORMAL -> {
                if (c.restartAvailable()) actions.add(RESTART_TURN);
                if (c.maintenanceReady()) actions.add(MAINTAIN_ERA);
                else {
                    builds = builds(input, probe); sales = sales(input, probe);
                    routes = routes(input, probe); pairs = pairs(input, probe);
                    developments = input.industryTypes().stream().filter(type -> input.supply().get(type).developable()).toList();
                    if (c.handSize() > 0) { actions.add(SKIP); if (c.loan()) actions.add(LOAN); }
                    if (!builds.isEmpty()) actions.add(BUILD);
                    if (c.handSize() > 0 && !sales.isEmpty()) actions.add(SELL);
                    if (c.handSize() > 0 && !routes.isEmpty()) actions.add(NETWORK);
                    if (c.handSize() > 0 && !developments.isEmpty()) actions.add(DEVELOP);
                    if (c.scout()) actions.add(SCOUT);
                }
            }
        }
        return new Result(c, actions, builds, sales, routes, pairs, developments);
    }

    private static List<String> cities(Input input, Network network) {
        var definitions = input.routes().stream().map(r -> new Route(r.from(), r.to(), r.hasBrewery())).toList();
        return BrassTypedConnectivity.playerCities(input.sites(), network.links(), definitions, input.context().actor());
    }

    private static List<Build> builds(Input input, Probe probe) {
        if (input.hand().isEmpty()) return List.of();
        var result = new ArrayList<Build>();
        var industries = input.resources().board().industries();
        var ownCities = cities(input, input.resources().board().network());
        long actor = input.context().actor();
        for (var entry : input.slots().entrySet()) for (int slot = 0; slot < entry.getValue().size(); slot++) {
            String city = entry.getKey();
            for (String type : entry.getValue().get(slot)) {
                Supply supply = input.supply().get(type);
                if (supply == null || !supply.buildable()) continue;
                int index = BrassTypedIndustries.buildSlot(industries, industries, input.slots(), actor, city, type,
                        supply.level(), input.emptyMarkets().contains(type), slot);
                if (index < 0) continue;
                boolean ownCover = BrassTypedIndustries.ownCover(industries, actor, city, type, index, supply.level()) >= 0;
                if ("canal".equals(input.era()) && !ownCover
                        && industries.stream().anyMatch(tile -> tile.ownerId() == actor && city.equals(tile.city()))) continue;
                List<String> ids = input.hand().stream().filter(card -> BrassTypedCards.canBuild(card, city, type,
                                () -> BrassTypedConnectivity.canBuild(ownCities, city)))
                        .map(card -> card.id()).filter(id -> !id.isBlank()).toList();
                if (ids.isEmpty() || input.money() < supply.cost()) continue;
                var candidate = new Build(city, type, index,
                        !BrassTypedIndustries.available(industries, input.slots(), city, type, index) && !ownCover, ownCover, ids);
                if (probe.build(candidate)) result.add(candidate);
            }
        }
        return List.copyOf(result);
    }

    private static List<Integer> sales(Input input, Probe probe) {
        var result = new ArrayList<Integer>();
        if (input.context().shape() != Shape.SELLING && input.hand().isEmpty()) return result;
        var board = input.resources().board();
        for (int index = 0; index < board.industries().size(); index++) {
            var tile = board.industries().get(index);
            if (tile.ownerId() != input.context().actor() || tile.flipped() || !"sell".equals(tile.flipType())) continue;
            for (int merchantIndex = 0; merchantIndex < input.resources().merchants().size(); merchantIndex++) {
                var merchant = input.resources().merchants().get(merchantIndex);
                if (!BrassTypedResources.accepts(merchant, tile.type())
                        || !BrassTypedConnectivity.connected(board.network(), tile.city(), merchant.city())) continue;
                if (probe.sell(index, merchantIndex) || enoughBeer(input, tile, merchant)) {
                    result.add(index); break;
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean enoughBeer(Input input, Industry tile, Merchant merchant) {
        int required = BrassTypedIndustries.saleBeer(tile);
        if (required <= 0) return true;
        var need = new ResourceRequirement(ResourceType.BEER, required, List.of(), merchant.city(), tile.type(), null);
        return BrassTypedResources.eligible(input.resources().board(), need, input.context().actor()).stream()
                .mapToInt(ResourceSource::amount).sum() + merchant.beer() >= required;
    }

    private static Network add(Network network, BrassTypedNetwork.RouteSpec route, String type, long actor) {
        var ends = BrassTypedConnectivity.normalizedEnds(route.from(), route.to());
        return network.withLink(new Link(ends.get(0), ends.get(1), actor, type));
    }
    private static boolean usable(Input input, Network network, BrassTypedNetwork.RouteSpec route, String type) {
        return !BrassTypedConnectivity.occupied(network.links(), route.from(), route.to(), type)
                && BrassTypedConnectivity.touches(cities(input, network), route.from(), route.to());
    }
    private static List<ResourceSource> sources(Input input, Network network, ResourceType type, List<String> destinations) {
        return BrassTypedResources.eligible(new ResourceBoard(network, input.resources().board().industries()),
                new ResourceRequirement(type, 1, destinations, null, "", null), input.context().actor());
    }

    private static List<RouteOption> routes(Input input, Probe probe) {
        if (input.context().remainingLinks() <= 0) return List.of();
        boolean rail = "rail".equals(input.era());
        String type = rail ? "rail" : "canal";
        var board = input.resources().board().network(); var result = new ArrayList<RouteOption>();
        for (var rule : input.routes()) {
            var route = new BrassTypedNetwork.RouteSpec(rule.from(), rule.to());
            if (!(rail ? rule.rail() : rule.canal()) || !usable(input, board, route, type)) continue;
            var stage = add(board, route, type, input.context().actor());
            int coalCost = !rail || !sources(input, stage, ResourceType.COAL, List.of(route.from(), route.to())).isEmpty() ? 0
                    : BrassTypedResources.purchase(input.coalPrices(), 1, input.distantCoalPrice()).totalCost();
            if (input.money() >= (rail ? 5 + coalCost : 3) && probe.network(List.of(route))) result.add(new RouteOption(route, stage));
        }
        return List.copyOf(result);
    }

    private static List<RoutePair> pairs(Input input, Probe probe) {
        if (!"rail".equals(input.era()) || input.context().remainingLinks() < 2 || input.money() < 15) return List.of();
        var rules = input.routes().stream().filter(r -> r.rail()).toList();
        var result = new ArrayList<RoutePair>(); var board = input.resources().board().network();
        for (var first : rules) {
            var a = new BrassTypedNetwork.RouteSpec(first.from(), first.to());
            if (!usable(input, board, a, "rail")) continue;
            var afterFirst = add(board, a, "rail", input.context().actor());
            var firstCities = List.of(a.from(), a.to());
            if (sources(input, afterFirst, ResourceType.COAL, firstCities).isEmpty()
                    && !BrassTypedConnectivity.marketReachable(afterFirst, firstCities, input.coalMarkets())) continue;
            for (var second : rules) {
                var b = new BrassTypedNetwork.RouteSpec(second.from(), second.to());
                if (BrassTypedConnectivity.sameRoute(a.from(), a.to(), b.from(), b.to()) || !usable(input, afterFirst, b, "rail")) continue;
                var afterBoth = add(afterFirst, b, "rail", input.context().actor());
                if (sources(input, afterBoth, ResourceType.BEER, List.of(a.from(), a.to(), b.from(), b.to())).isEmpty()) continue;
                if (probe.network(List.of(a, b))) result.add(new RoutePair(new RouteOption(a, afterFirst), new RouteOption(b, afterBoth)));
            }
        }
        return List.copyOf(result);
    }
}
