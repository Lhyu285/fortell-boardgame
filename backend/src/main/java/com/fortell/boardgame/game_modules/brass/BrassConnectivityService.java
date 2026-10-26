package com.fortell.boardgame.game_modules.brass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.listOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.mapOf;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;

/** Legacy JSON facade; connectivity decisions run on immutable typed board values. */
final class BrassConnectivityService {
    private BrassConnectivityService() {}

    static Map<String, Object> routeLink(String from, String to, String type) {
        Map<String, Object> link = new LinkedHashMap<>();
        link.put("from", normalizedRouteEnd(from, to).get(0));
        link.put("to", normalizedRouteEnd(from, to).get(1));
        link.put("type", type);
        return link;
    }

    static long playerLinkCount(List<Map<String, Object>> links, long playerId) {
        return BrassTypedConnectivity.playerLinkCount(BrassBoardAdapter.links(links), playerId);
    }

    static boolean cityBuildableWithIndustryCard(Map<String, Object> board, long playerId, String city) {
        return BrassTypedConnectivity.canBuild(playerNetworkCities(board, playerId), city);
    }

    static Map<String, Object> findRoute(String from, String to) {
        List<Map<String, Object>> definitions = routes();
        int index = BrassTypedConnectivity.routeIndex(definitions.stream().map(BrassBoardAdapter::route).toList(), from, to);
        return index < 0 ? null : definitions.get(index);
    }

    static List<Map<String, Object>> networkRouteSpecs(Map<String, Object> payload) {
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
        return new ArrayList<>(List.of(routeSpec));
    }

    static List<String> networkDestinations(List<Map<String, Object>> links) {
        return BrassTypedConnectivity.destinations(BrassBoardAdapter.links(links));
    }

    static boolean sameRoute(String leftFrom, String leftTo, String rightFrom, String rightTo) {
        return BrassTypedConnectivity.sameRoute(leftFrom, leftTo, rightFrom, rightTo);
    }

    static List<String> normalizedRouteEnd(String from, String to) {
        return BrassTypedConnectivity.normalizedEnds(from, to);
    }

    static String routeKey(String from, String to) {
        List<String> ends = normalizedRouteEnd(from, to);
        return ends.get(0) + "__" + ends.get(1);
    }

    static boolean routeOccupied(List<Map<String, Object>> links, String from, String to, String linkType) {
        return BrassTypedConnectivity.occupied(BrassBoardAdapter.links(links), from, to, linkType);
    }

    static boolean touchesPlayerNetwork(Map<String, Object> board, long playerId, String from, String to) {
        return BrassTypedConnectivity.touches(playerNetworkCities(board, playerId), from, to);
    }

    static List<String> playerNetworkCities(Map<String, Object> board, long playerId) {
        return BrassTypedConnectivity.playerCities(BrassBoardAdapter.sites(board),
                BrassBoardAdapter.network(board).links(), routes().stream().map(BrassBoardAdapter::route).toList(), playerId);
    }

    static boolean coalSourceReachable(Map<String, Object> board, Map<String, Object> source, List<String> destinations) {
        return BrassTypedConnectivity.coalReachable(BrassBoardAdapter.network(board),
                Objects.toString(source.get("city"), ""), destinations);
    }

    static boolean beerSourceReachable(Map<String, Object> board, Map<String, Object> source, String saleCity, long actorId) {
        return beerSourceReachableToAny(board, source, List.of(saleCity), actorId);
    }

    static boolean beerSourceReachableToAny(Map<String, Object> board, Map<String, Object> source,
                                             List<String> destinations, long actorId) {
        return BrassTypedConnectivity.beerReachable(BrassBoardAdapter.network(board), BrassBoardAdapter.site(source), destinations, actorId);
    }

    static boolean citiesConnected(Map<String, Object> board, String sourceCity, String destinationCity) {
        return BrassTypedConnectivity.connected(BrassBoardAdapter.network(board), sourceCity, destinationCity);
    }

    static boolean cityConnectedToAnyMarket(Map<String, Object> board, String city) {
        return (MAP_MARKETS.isEmpty() ? fallbackMarkets() : MAP_MARKETS).stream()
                .map(market -> Objects.toString(market.get("city"), ""))
                .anyMatch(marketCity -> citiesConnected(board, city, marketCity));
    }

    static boolean coalMarketReachable(Map<String, Object> board, List<String> destinations) {
        return BrassTypedConnectivity.marketReachable(BrassBoardAdapter.network(board), destinations,
                (MAP_MARKETS.isEmpty() ? fallbackMarkets() : MAP_MARKETS).stream()
                        .map(market -> Objects.toString(market.get("city"), "")).toList());
    }
}
