package com.fortell.boardgame.game_modules.brass;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;

/** Wire parsing is performed once; rule handlers consume the corresponding action input. */
sealed interface BrassActionInput {
    record Build(String cardId, String city, String industryType, int slotIndex) implements BrassActionInput {}
    record Develop(String cardId, List<String> industryTypes) implements BrassActionInput {}
    record Sale(String cardId, String tileId, String merchantId, String freeDevelopIndustryType) implements BrassActionInput {}
    record Network(String cardId, List<BrassTypedNetwork.RouteSpec> routes) implements BrassActionInput {
        public Network { routes = List.copyOf(routes); }
    }
    record Scout(List<String> cardIds) implements BrassActionInput {}
    record Card(String cardId) implements BrassActionInput {}
    record Debt(String tileId) implements BrassActionInput {}
    record Control() implements BrassActionInput {}

    static BrassActionInput decode(BrassActionType type, Map<String, Object> payload) {
        String cardId = text(payload, "cardId");
        return switch (type) {
            case BUILD -> new Build(cardId, text(payload, "city"), text(payload, "industryType"), parseInteger(payload.get("slotIndex"), -1));
            case DEVELOP -> new Develop(cardId, List.copyOf(stringList(payload.get("industryTypes"))));
            case SELL -> new Sale(cardId, text(payload, "tileId"), text(payload, "merchantId"), text(payload, "freeDevelopIndustryType"));
            case NETWORK -> new Network(cardId, BrassActionAdapter.routeSpecs(BrassConnectivityService.networkRouteSpecs(payload)));
            case SCOUT -> new Scout(List.copyOf(stringList(payload.get("cardIds"))));
            case LOAN, SKIP -> new Card(cardId);
            case RESOLVE_INCOME_DEBT -> new Debt(text(payload, "tileId"));
            default -> new Control();
        };
    }
    private static String text(Map<String, Object> payload, String name) { return Objects.toString(payload.get(name), ""); }
}
