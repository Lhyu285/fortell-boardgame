package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassBoardView.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Public resource transport and personal building network are intentionally different rules. */
final class BrassTypedConnectivity {
    private BrassTypedConnectivity() {}

    static boolean sameRoute(String from, String to, String otherFrom, String otherTo) {
        return (from.equals(otherFrom) && to.equals(otherTo)) || (from.equals(otherTo) && to.equals(otherFrom));
    }

    static List<String> normalizedEnds(String from, String to) {
        return from.compareTo(to) <= 0 ? List.of(from, to) : List.of(to, from);
    }

    static int routeIndex(List<Route> routes, String from, String to) {
        for (int index = 0; index < routes.size(); index++) {
            Route route = routes.get(index);
            if (sameRoute(from, to, route.from(), route.to())) return index;
        }
        return -1;
    }

    static long playerLinkCount(List<Link> links, long ownerId) {
        return links.stream().filter(link -> link.ownerId() == ownerId).count();
    }

    static List<String> destinations(List<Link> links) {
        var cities = new ArrayList<String>();
        for (Link link : links) { cities.add(link.from()); cities.add(link.to()); }
        return cities.stream().filter(city -> !city.isBlank()).distinct().toList();
    }

    static boolean occupied(List<Link> links, String from, String to, String type) {
        return links.stream().anyMatch(link -> type.equals(link.type()) && sameRoute(from, to, link.from(), link.to()));
    }

    static List<String> playerCities(List<Site> sites, List<Link> links, List<Route> routes, long ownerId) {
        var cities = new ArrayList<String>();
        for (Site site : sites) if (site.ownerId() == ownerId) cities.add(site.city());
        for (Link link : links) {
            if (link.ownerId() != ownerId) continue;
            cities.add(link.from()); cities.add(link.to());
            int route = routeIndex(routes, link.from(), link.to());
            if (route >= 0 && routes.get(route).hasBrewery()) cities.add("Personal_Brewery");
        }
        return cities.stream().distinct().toList();
    }

    static boolean canBuild(List<String> playerCities, String city) {
        return playerCities.isEmpty() || playerCities.contains(city);
    }

    static boolean touches(List<String> playerCities, String from, String to) {
        return playerCities.isEmpty() || playerCities.contains(from) || playerCities.contains(to);
    }

    static boolean connected(Network board, String source, String destination) {
        if (source.equals(destination)) return true;
        var visited = new HashSet<String>();
        var pending = new ArrayDeque<String>();
        pending.add(source);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) continue;
            for (Link link : board.links()) {
                String next = "";
                if (current.equals(link.from())) next = link.to();
                else if (current.equals(link.to())) next = link.from();
                // Keep historical empty-endpoint handling at this migration boundary.
                if (destination.equals(next)) return true;
                if (!next.isBlank() && !visited.contains(next)) pending.addLast(next);
            }
        }
        return false;
    }

    static boolean coalReachable(Network board, String source, List<String> destinations) {
        return destinations == null || destinations.isEmpty() || destinations.stream()
                .filter(city -> !city.isBlank()).anyMatch(city -> connected(board, source, city));
    }

    static boolean beerReachable(Network board, Site source, List<String> destinations, long actorId) {
        return source.ownerId() == actorId || destinations.stream().anyMatch(city -> connected(board, source.city(), city));
    }

    static boolean marketReachable(Network board, List<String> destinations, List<String> markets) {
        return destinations != null && destinations.stream().filter(city -> !city.isBlank())
                .anyMatch(city -> markets.stream().filter(market -> !market.isBlank()).anyMatch(market -> connected(board, city, market)));
    }
}
