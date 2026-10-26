package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassBoardView.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;

/** Component JSON boundary. Reads only the required board component; writes explicit deltas. */
final class BrassBoardAdapter {
    private BrassBoardAdapter() {}

    static Industry industry(Map<String, Object> tile) {
        return new Industry(text(tile, "id"), text(tile, "city"), text(tile, "industryType"),
                asLong(tile.get("ownerId")), parseInteger(tile.get("slotIndex"), -1),
                parseInteger(tile.get("level"), 0), Boolean.TRUE.equals(tile.get("flipped")),
                parseInteger(tile.get("coal"), 0), parseInteger(tile.get("iron"), 0),
                parseInteger(tile.get("beer"), 0),
                tile.containsKey("saleBeerCost") ? parseInteger(tile.get("saleBeerCost"), 0) : null,
                text(tile, "flipType"), tile.containsKey("flipType"), parseInteger(tile.get("incomeReward"), 0));
    }

    static List<Industry> industries(List<Map<String, Object>> tiles) {
        return tiles.stream().map(BrassBoardAdapter::industry).toList();
    }

    static List<Site> sites(Map<String, Object> board) {
        return listOf(board.get("industries")).stream()
                .map(tile -> new Site(asLong(tile.get("ownerId")), text(tile, "city"))).toList();
    }

    static List<Link> links(List<Map<String, Object>> links) {
        return links.stream().map(link -> new Link(text(link, "from"), text(link, "to"),
                asLong(link.get("ownerId")), text(link, "type"))).toList();
    }

    static Network network(Map<String, Object> board) {
        return new Network(links(listOf(board.get("links"))));
    }

    static Site site(Map<String, Object> source) {
        return new Site(asLong(source.get("ownerId")), text(source, "city"));
    }

    static Route route(Map<String, Object> route) {
        return new Route(text(route, "from"), text(route, "to"), Boolean.TRUE.equals(route.get("hasBrewery")));
    }

    static void writeFlip(Map<String, Object> tile, Industry next) {
        // Do not rebuild the tile or key by ID: extensions and sparse fields stay with this instance.
        tile.put("flipped", next.flipped());
        tile.put("coal", next.coal());
        tile.put("iron", next.iron());
        tile.put("beer", next.beer());
    }

    private static String text(Map<String, Object> value, String key) {
        return Objects.toString(value.get(key), "");
    }
}
