package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.Card;
import com.fortell.boardgame.models.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import static com.fortell.boardgame.game_modules.brass.BrassConnectivityService.cityBuildableWithIndustryCard;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.listOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.mapOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.parseInteger;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.staticListOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.staticMapOf;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;

/** Wire/configuration facade. Live card rules are implemented by BrassTypedCards. */
final class BrassCardService {
    private BrassCardService() {}

    static List<Map<String, Object>> initialScoutPool() {
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

    static List<Map<String, Object>> currentHand(Map<String, Object> state, long playerId) {
        return listOf(mapOf(state.get("hands")).get(String.valueOf(playerId)));
    }

    static boolean canScout(Map<String, Object> state, List<Map<String, Object>> hand) {
        return BrassTypedCards.canScout(BrassPersonalAdapter.cards(hand), BrassPersonalAdapter.cards(listOf(state.get("scoutPool"))));
    }

    static List<Map<String, Object>> buildEraDeck(int playerCount) {
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

    static Map<String, Object> card(String type, String key, String name) {
        return BrassPersonalAdapter.initialCard(new Card(
                type + "_" + key + "_" + System.nanoTime(), type, key, name, null, null, null, List.of(), false), false);
    }

    static Map<String, Object> initialCardHints(List<Map<String, Object>> deck) {
        return BrassPersonalAdapter.initialHints(BrassTypedCards.initialHints(BrassPersonalAdapter.cards(deck)));
    }

    static String cardHintKey(Map<String, Object> card) {
        return BrassTypedCards.hintKey(BrassPersonalAdapter.card(card));
    }

    static String cardHintName(Map<String, Object> card) {
        return Objects.toString(card.get("displayName"), Objects.toString(card.get("name"), ""));
    }

    static List<Map<String, Object>> buildConfiguredEraDeck(int playerCount) {
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

    static Map<String, Object> configuredDeckCard(Map<String, Object> entry) {
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
                    .map(BrassRuleData::industryNameCn)
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

    static Map<String, Object> wildCard(String type, String name) {
        return BrassPersonalAdapter.initialCard(new Card(
                type + "_" + System.nanoTime(), type, type, name, null, null, null, List.of(), true), true);
    }

    static Map<String, Object> takeScoutCard(Map<String, Object> state, String type, String fallbackName) {
        var cards = BrassPersonalAdapter.cards(state);
        var change = BrassTypedCards.take(cards.value(), type);
        Map<String, Object> card = cards.wire(change.taken()); cards.apply(change); return card;
    }

    static Map<String, Object> findCard(List<Map<String, Object>> cards, String cardId) {
        return BrassPersonalAdapter.findCard(cards, cardId);
    }

    static boolean cardCanBuild(Map<String, Object> card, String city, String industryType, Map<String, Object> board, long playerId) {
        return BrassTypedCards.canBuild(BrassPersonalAdapter.card(card), city, industryType, () -> cityBuildableWithIndustryCard(board, playerId, city));
    }

    static boolean cardCanBuildAnonymousBrewery(Map<String, Object> card) {
        return BrassTypedCards.canBuildAnonymousBrewery(BrassPersonalAdapter.card(card));
    }

    static boolean isWildCard(Map<String, Object> card) {
        return BrassTypedCards.wild(BrassPersonalAdapter.card(card));
    }

    static void discardCard(Map<String, Object> state, long playerId, String cardId) {
        var cards = BrassPersonalAdapter.cards(state, playerId);
        cards.apply(BrassTypedCards.discard(cards.value(), playerId, cardId));
    }

    static void decrementCardHint(Map<String, Object> state, Map<String, Object> card) {
        var value = BrassPersonalAdapter.card(card);
        if (BrassTypedCards.hintKey(value).isBlank()) return;
        var cards = BrassPersonalAdapter.cards(state); var before = cards.value();
        cards.apply(new BrassTypedCards.Change(new BrassTypedCards.State(before.hands(), before.deck(), before.pool(), before.discard(),
                BrassTypedCards.decrementHint(before.hints(), value)), java.util.Set.of(BrassTypedCards.Field.HINTS), null));
    }

    static void discardActionCard(Map<String, Object> state, long playerId, Map<String, Object> payload) {
        discardActionCard(state, playerId, Objects.toString(payload.get("cardId"), ""));
    }

    static void discardActionCard(Map<String, Object> state, long playerId, String cardId) {
        if (cardId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8bf7\u9009\u62e9\u8981\u5f03\u7f6e\u7684\u624b\u724c");
        }
        discardCard(state, playerId, cardId);
    }

    static void drawCards(Map<String, Object> state, long playerId, int count) {
        var cards = BrassPersonalAdapter.cardsForDraw(state, playerId);
        cards.apply(BrassTypedCards.draw(cards.value(), playerId, count));
    }

    static void drawHandToLimit(Map<String, Object> state, long playerId, int limit) {
        Map<String, Object> hands = mapOf(state.get("hands"));
        List<Map<String, Object>> hand = listOf(hands.get(String.valueOf(playerId)));
        int drawCount = Math.max(0, limit - hand.size());
        drawCards(state, playerId, drawCount);
    }
}
