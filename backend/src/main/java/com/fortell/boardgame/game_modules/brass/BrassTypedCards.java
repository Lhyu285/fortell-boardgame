package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.Card;
import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.CardHint;
import com.fortell.boardgame.models.ApiException;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.springframework.http.HttpStatus;

/** Card rules on immutable values; no JSON, persistence or query dependencies. */
final class BrassTypedCards {
    private BrassTypedCards() {}
    enum Field { HANDS, DECK, POOL, DISCARD, HINTS }
    record State(Map<String, List<Card>> hands, List<Card> deck, List<Card> pool, List<Card> discard, List<CardHint> hints) {
        State {
            Map<String, List<Card>> copy = new LinkedHashMap<>(); hands.forEach((id, cards) -> copy.put(id, List.copyOf(cards)));
            hands = Collections.unmodifiableMap(copy); deck = List.copyOf(deck); pool = List.copyOf(pool);
            discard = List.copyOf(discard); hints = List.copyOf(hints);
        }
        List<Card> hand(long playerId) { return hands.getOrDefault(String.valueOf(playerId), List.of()); }
    }
    record Change(State state, Set<Field> fields, Card taken, Long handOwner) {
        Change { fields = Set.copyOf(fields); }
        Change(State state, Set<Field> fields, Card taken) { this(state, fields, taken, null); }
    }
    static Card find(List<Card> cards, String id) { return cards.stream().filter(card -> id.equals(Objects.toString(card.id(), ""))).findFirst().orElse(null); }
    static boolean wild(Card card) { return card.wild() || Objects.toString(card.type(), "").startsWith("wild_") || Objects.toString(card.key(), "").startsWith("wild_"); }
    static boolean canScout(List<Card> hand, List<Card> pool) {
        return hand.stream().filter(card -> !wild(card)).count() >= 3
                && pool.stream().anyMatch(card -> "wild_industry".equals(card.key()))
                && pool.stream().anyMatch(card -> "wild_location".equals(card.key()));
    }
    static boolean canBuild(Card card, String city, String industry, BooleanSupplier network) {
        if (card.wild()) return "wild_location".equals(card.type()) || "wild_location".equals(card.key())
                || (("wild_industry".equals(card.type()) || "wild_industry".equals(card.key())) && network.getAsBoolean());
        return "location".equals(card.type()) && city.equals(card.key())
                || "industry".equals(card.type()) && (industry.equals(card.key()) || card.industryTypes().contains(industry)) && network.getAsBoolean();
    }
    static boolean canBuildAnonymousBrewery(Card card) {
        return "industry".equals(card.type()) && ("brewery".equals(card.key()) || card.industryTypes().contains("brewery"))
                || card.wild() && ("wild_industry".equals(card.type()) || "wild_industry".equals(card.key()));
    }
    static String hintKey(Card card) {
        return switch (Objects.toString(card.type(), "")) {
            case "location" -> "location:" + Objects.toString(card.key(), "");
            case "industry" -> "industry:" + String.join("/", card.industryTypes());
            default -> "";
        };
    }
    static List<CardHint> initialHints(List<Card> cards) {
        Map<String, CardHint> hints = new LinkedHashMap<>();
        for (Card card : cards) {
            if (wild(card) || hintKey(card).isBlank()) continue;
            String key = hintKey(card);
            CardHint previous = hints.get(key);
            if (previous == null) previous = new CardHint(key, Objects.toString(card.displayName(), Objects.toString(card.name(), "")), 0, 0);
            hints.put(key, new CardHint(key, previous.name(), previous.total() + 1, previous.remaining() + 1));
        }
        return List.copyOf(hints.values());
    }
    static Change discard(State state, long playerId, String id) {
        Card card = find(state.hand(playerId), id);
        if (card == null) throw bad("找不到要弃置的手牌");
        List<Card> hand = new ArrayList<>(state.hand(playerId)); hand.removeIf(item -> id.equals(item.id()));
        List<Card> pool = new ArrayList<>(state.pool()), discard = new ArrayList<>(state.discard());
        List<CardHint> hints = state.hints();
        Set<Field> fields = EnumSet.of(Field.HANDS);
        if (wild(card)) { pool.add(card); fields.add(Field.POOL); }
        else {
            discard.add(card); fields.add(Field.DISCARD);
            if (!hintKey(card).isBlank()) { hints = decrementHint(hints, card); fields.add(Field.HINTS); }
        }
        return new Change(withHand(state, playerId, hand, state.deck(), pool, discard, hints), fields, card, playerId);
    }
    static List<CardHint> decrementHint(List<CardHint> hints, Card card) {
        List<CardHint> result = new ArrayList<>(hints);
        for (int i = 0; i < result.size(); i++) {
            CardHint hint = result.get(i);
            if (Objects.equals(hint.key(), hintKey(card))) {
                result.set(i, new CardHint(hint.key(), hint.name(), hint.total(), Math.max(0, hint.remaining() - 1)));
                break;
            }
        }
        return result;
    }
    static Change draw(State state, long playerId, int count) {
        List<Card> hand = new ArrayList<>(state.hand(playerId)), deck = new ArrayList<>(state.deck());
        for (int i = 0; i < count && !deck.isEmpty(); i++) hand.add(deck.removeFirst());
        return new Change(withHand(state, playerId, hand, deck, state.pool(), state.discard(), state.hints()), Set.of(Field.HANDS, Field.DECK), null, playerId);
    }
    static Change take(State state, String type) {
        List<Card> pool = new ArrayList<>(state.pool());
        Card card = pool.stream().filter(item -> type.equals(item.type())).findFirst().orElseThrow(() -> bad("侦查牌池已耗尽"));
        pool.remove(card);
        return new Change(new State(state.hands(), state.deck(), pool, state.discard(), state.hints()), Set.of(Field.POOL), card);
    }
    static Change scout(State state, long playerId, List<String> ids) {
        if (ids.size() != 3) throw bad("侦查必须弃置3张牌");
        if (ids.stream().distinct().count() != ids.size()) throw bad("不能重复选择同一张牌");
        if (!canScout(state.hand(playerId), state.pool())) throw bad("当前不能侦查");
        List<Card> selected = new ArrayList<>();
        for (String id : ids) {
            Card card = find(state.hand(playerId), id);
            if (card == null) throw bad("找不到要丢弃的牌");
            if (wild(card)) throw bad("万能牌不能用于侦查");
            selected.add(card);
        }
        Change industry = take(state, "wild_industry"), location = take(industry.state(), "wild_location");
        List<Card> hand = new ArrayList<>(state.hand(playerId)); hand.removeIf(card -> ids.contains(card.id()));
        List<Card> discard = new ArrayList<>(state.discard()); discard.addAll(selected);
        hand.add(industry.taken()); hand.add(location.taken());
        // Preserve the established scout path: no card-hint decrement here.
        return new Change(withHand(state, playerId, hand, state.deck(), location.state().pool(), discard, state.hints()),
                Set.of(Field.HANDS, Field.POOL, Field.DISCARD), null, playerId);
    }
    private static State withHand(State state, long id, List<Card> hand, List<Card> deck, List<Card> pool, List<Card> discard, List<CardHint> hints) {
        Map<String, List<Card>> hands = new LinkedHashMap<>(state.hands()); hands.put(String.valueOf(id), hand);
        return new State(hands, deck, pool, discard, hints);
    }
    private static ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
}
