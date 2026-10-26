package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassBoardView.*;
import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.Card;
import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.RouteDefinition;
import java.util.*;
import static com.fortell.boardgame.game_modules.brass.BrassResourceModel.*;

/** Query values are independent of JSON field presence and of mutable save documents. */
final class BrassQueryModel {
    private BrassQueryModel() {}
    enum Shape { INACTIVE, DEBT, END_TURN, SELLING, NORMAL }
    record Context(Shape shape, long actor, int handSize, int taken, int sellCount,
                   boolean restartAvailable, boolean maintenanceReady, boolean loan, boolean scout,
                   int remainingLinks) {}
    record Supply(boolean buildable, int level, int cost, boolean developable) {}
    record Input(Context context, String era, int money, ResourceContext resources, List<Site> sites,
                 List<RouteDefinition> routes, List<Card> hand, Map<String, Supply> supply,
                 Map<String, List<List<String>>> slots, List<String> industryTypes,
                 Set<String> emptyMarkets, List<Integer> coalPrices, int distantCoalPrice,
                 List<String> coalMarkets) {
        Input {
            sites = List.copyOf(sites); routes = List.copyOf(routes); hand = List.copyOf(hand);
            supply = Map.copyOf(supply); industryTypes = List.copyOf(industryTypes);
            Map<String, List<List<String>>> copy = new LinkedHashMap<>();
            slots.forEach((city, rows) -> copy.put(city, rows.stream().map(List::copyOf).toList()));
            slots = Collections.unmodifiableMap(copy); emptyMarkets = Set.copyOf(emptyMarkets);
            coalPrices = List.copyOf(coalPrices); coalMarkets = List.copyOf(coalMarkets);
        }
    }
    record Build(String city, String industryType, int slotIndex, boolean coversOpponent,
                 boolean coversOwn, List<String> cardIds) {
        Build { cardIds = List.copyOf(cardIds); }
    }
    record RouteOption(BrassTypedNetwork.RouteSpec route, Network stage) {}
    record RoutePair(RouteOption first, RouteOption second) {}
    record Result(Context context, List<BrassActionType> actions, List<Build> builds,
                  List<Integer> sales, List<RouteOption> routes, List<RoutePair> pairs, List<String> developments) {
        Result {
            actions = List.copyOf(actions); builds = List.copyOf(builds); sales = List.copyOf(sales);
            routes = List.copyOf(routes); pairs = List.copyOf(pairs); developments = List.copyOf(developments);
        }
    }
    /** Candidate execution uses the same handlers, but only against a detached simulation document. */
    interface Probe {
        boolean build(Build candidate);
        boolean sell(int tileIndex, int merchantIndex);
        boolean network(List<BrassTypedNetwork.RouteSpec> routes);
    }
}
