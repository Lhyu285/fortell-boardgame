package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.*;
import java.util.*;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;

/** Temporary, component-scoped wire boundary. Never decodes board/network/era state.
 * Cards and supply tiles retain their original field presence and extensions when moved.
 */
final class BrassPersonalAdapter {
    private BrassPersonalAdapter() {}

    static Map<String, Object> player(Player value) {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("seatIndex", value.seatIndex()); wire.put("userId", value.userId());
        wire.put("username", value.username()); wire.put("color", value.color()); return wire;
    }
    static Map<String, Object> initialStats(PlayerStats value) {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("money", value.money()); wire.put("incomeLevel", value.incomeLevel()); wire.put("income", value.income());
        wire.put("victoryPoints", value.victoryPoints()); wire.put("spentThisRound", value.spentThisRound());
        wire.put("estimatedEraEndScore", value.estimatedEraEndScore()); return wire;
    }

    static Card card(Map<String, Object> wire) {
        return new Card(text(wire, "id"), text(wire, "type"), text(wire, "key"), nullableText(wire, "name"),
                nullableText(wire, "sourceType"), nullableText(wire, "city"), nullableText(wire, "displayName"),
                stringList(wire.get("industryTypes")), Boolean.TRUE.equals(wire.get("wild")));
    }
    static Map<String, Object> initialCard(Card value, boolean wild) {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("id", value.id()); wire.put("type", value.type()); wire.put("key", value.key()); wire.put("name", value.name());
        if (wild) wire.put("wild", true);
        return wire;
    }
    static Map<String, Object> initialHints(List<CardHint> hints) {
        List<Map<String, Object>> rows = hints.stream().map(hint -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", hint.key()); row.put("name", hint.name()); row.put("total", hint.total()); row.put("remaining", hint.remaining());
            return row;
        }).toList();
        return new LinkedHashMap<>(Map.of("cards", rows));
    }
    static List<Card> cards(List<Map<String, Object>> wire) { return wire.stream().map(BrassPersonalAdapter::card).toList(); }
    static Map<String, Object> findCard(List<Map<String, Object>> cards, String id) {
        return cards.stream().filter(card -> id.equals(text(card, "id"))).findFirst().orElse(null);
    }
    static PlayerStats stats(Map<String, Object> state, long id, int missingIncomeLevel) {
        Map<String, Object> values = viewMap(viewMap(state.get("playerStats")).get(String.valueOf(id)));
        return new PlayerStats(parseInteger(values.get("money"), 0), parseInteger(values.get("incomeLevel"), missingIncomeLevel),
                parseInteger(values.get("income"), 0), parseInteger(values.get("victoryPoints"), 0), parseInteger(values.get("spentThisRound"), 0),
                parseInteger(values.get("estimatedEraEndScore"), 0), values.get("lastIncome") == null ? null : parseInteger(values.get("lastIncome"), 0));
    }
    static void writeStats(Map<String, Object> state, long id, PlayerStats value, Set<BrassTypedPlayers.Field> fields) {
        Map<String, Object> all = mapOf(state.get("playerStats")), row = mapOf(all.get(String.valueOf(id)));
        for (var field : fields) switch (field) {
            case MONEY -> row.put("money", value.money());
            case INCOME_LEVEL -> row.put("incomeLevel", value.incomeLevel());
            case INCOME -> row.put("income", value.income());
            case SPENT -> row.put("spentThisRound", value.spentThisRound());
            case VP -> row.put("victoryPoints", value.victoryPoints());
        }
        all.put(String.valueOf(id), row); state.put("playerStats", all);
    }

    static Cards cards(Map<String, Object> state) { return new Cards(state, null, false); }
    static Cards cards(Map<String, Object> state, long owner) { return new Cards(state, owner, false); }
    static Cards cardsForDraw(Map<String, Object> state, long owner) { return new Cards(state, owner, true); }
    static final class Cards {
        private final Map<String, Object> source;
        private final IdentityHashMap<Card, Map<String, Object>> origins = new IdentityHashMap<>();
        private final List<Map<String, Object>> rawHints;
        private final BrassTypedCards.State value;
        private Cards(Map<String, Object> source, Long owner, boolean includeDeck) {
            this.source = source;
            Map<String, List<Card>> hands = new LinkedHashMap<>();
            if (owner != null) hands.put(String.valueOf(owner), decode(viewMap(source.get("hands")).get(String.valueOf(owner))));
            rawHints = listOf(viewMap(source.get("cardHints")).get("cards"));
            List<CardHint> hints = rawHints.stream().map(hint -> new CardHint(nullableText(hint, "key"), nullableText(hint, "name"),
                    parseInteger(hint.get("total"), 0), parseInteger(hint.get("remaining"), 0))).toList();
            value = new BrassTypedCards.State(hands, includeDeck ? decode(source.get("deck")) : List.of(), decode(source.get("scoutPool")), decode(source.get("discardPile")), hints);
        }
        BrassTypedCards.State value() { return value; }
        private List<Card> decode(Object items) {
            return listOf(items).stream().map(wire -> {
                Card card = card(wire); origins.put(card, wire); return card;
            }).toList();
        }
        Map<String, Object> wire(Card card) {
            Map<String, Object> original = origins.get(card);
            if (original == null) throw new IllegalStateException("Card identity must come from the loaded hand/deck/pool");
            return mapOf(deepCopy(original));
        }
        private List<Map<String, Object>> encode(List<Card> cards) { return cards.stream().map(this::wire).toList(); }
        void apply(BrassTypedCards.Change change) {
            var next = change.state();
            Map<String, Object> updates = new LinkedHashMap<>();
            for (var field : change.fields()) switch (field) {
                case HANDS -> {
                    Map<String, Object> hands = mapOf(source.get("hands"));
                    long owner = Objects.requireNonNull(change.handOwner(), "handOwner");
                    hands.put(String.valueOf(owner), encode(next.hand(owner)));
                    updates.put("hands", hands);
                }
                case DECK -> updates.put("deck", encode(next.deck()));
                case POOL -> updates.put("scoutPool", encode(next.pool()));
                case DISCARD -> updates.put("discardPile", encode(next.discard()));
                case HINTS -> {
                    Map<String, Object> hints = mapOf(source.get("cardHints"));
                    List<Map<String, Object>> rows = new ArrayList<>();
                    for (int i = 0; i < next.hints().size(); i++) {
                        Map<String, Object> wire = mapOf(deepCopy(rawHints.get(i)));
                        if (next.hints().get(i) != value.hints().get(i)) wire.put("remaining", next.hints().get(i).remaining());
                        rows.add(wire);
                    }
                    hints.put("cards", rows); updates.put("cardHints", hints);
                }
            }
            source.putAll(updates);
        }
    }

    static Era era(String wire) { return "canal".equals(wire) ? Era.CANAL : "rail".equals(wire) ? Era.RAIL : null; }
    static BrassTypedSupply.Position position(Map<String, Object> tile) {
        return new BrassTypedSupply.Position(text(tile, "id"), parseInteger(tile.get("level"), 0),
                parseInteger(tile.get("period"), 0), !Boolean.FALSE.equals(tile.get("canDevelop")));
    }
    static Supply supply(Map<String, Object> state, long id, String type) { return new Supply(state, id, type); }
    static final class Supply {
        private final Map<String, Object> source;
        private final long playerId;
        private final String type;
        private final IdentityHashMap<BrassTypedSupply.Position, Map<String, Object>> origins = new IdentityHashMap<>();
        private final List<Map<String, Object>> rawTiles;
        private List<BrassTypedSupply.Position> tiles;
        private Supply(Map<String, Object> source, long id, String type) {
            this.source = source; this.playerId = id; this.type = type;
            rawTiles = listOf(viewMap(viewMap(source.get("playerBoards")).get(String.valueOf(id))).get(type));
        }
        private BrassTypedSupply.Position decode(Map<String, Object> wire) { var tile = position(wire); origins.put(tile, wire); return tile; }
        List<BrassTypedSupply.Position> leading() { return rawTiles.isEmpty() ? List.of() : List.of(decode(rawTiles.getFirst())); }
        List<BrassTypedSupply.Position> tiles() {
            if (tiles == null) tiles = rawTiles.stream().map(this::decode).toList();
            return tiles;
        }
        Map<String, Object> wire(BrassTypedSupply.Position tile) { return tile == null ? new LinkedHashMap<>() : mapOf(origins.get(tile)); }
        void replace(List<BrassTypedSupply.Position> tiles) {
            Map<String, Object> boards = mapOf(source.get("playerBoards")), board = mapOf(boards.get(String.valueOf(playerId)));
            board.put(type, tiles.stream().map(this::wire).toList()); boards.put(String.valueOf(playerId), board); source.put("playerBoards", boards);
        }
    }
    static int development(Map<String, Object> state, long id, String type) {
        return parseInteger(viewMap(viewMap(state.get("developments")).get(String.valueOf(id))).get(type), 0);
    }
    static void writeDevelopment(Map<String, Object> state, long id, String type, int count) {
        Map<String, Object> all = mapOf(state.get("developments")), row = mapOf(all.get(String.valueOf(id)));
        row.put(type, count); all.put(String.valueOf(id), row); state.put("developments", all);
    }
    private static String text(Map<String, Object> value, String key) { return Objects.toString(value.get(key), ""); }
    private static String nullableText(Map<String, Object> value, String key) { return Objects.toString(value.get(key), null); }
}
