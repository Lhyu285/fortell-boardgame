package com.fortell.boardgame.game_modules.brass.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.*;

/** Normalized definitions; the existing configuration loader is deliberately unchanged. */
public record BrassRuleBook(Map<String, List<IndustryDefinition>> industries, List<RouteDefinition> routes,
        Map<String, List<List<String>>> citySlots, List<MerchantDefinition> merchants,
        List<Integer> incomeByLevel, Map<String, Integer> incomeLevelAfterLoan,
        List<CardDefinition> cards, List<MarketDefinition> markets) {
    public BrassRuleBook {
        industries = lists(industries); routes = list(routes); merchants = list(merchants);
        incomeByLevel = list(incomeByLevel); incomeLevelAfterLoan = map(incomeLevelAfterLoan);
        cards = list(cards); markets = list(markets);
        Map<String, List<List<String>>> slots = new LinkedHashMap<>();
        if (citySlots != null) citySlots.forEach((city, values) -> slots.put(city, list(values).stream().map(BrassSnapshot::list).toList()));
        citySlots = map(slots);
    }
    public record MerchantDefinition(String id, List<String> industryTypes, boolean wild, boolean blank, boolean providesBeer) {
        public MerchantDefinition { industryTypes = list(industryTypes); }
    }
    public record CardDefinition(String type, String city, List<String> industryTypes, boolean wild,
                                 Map<String, Integer> amountByPlayerCount, int amount, String deck) {
        public CardDefinition { industryTypes = list(industryTypes); amountByPlayerCount = map(amountByPlayerCount); }
    }
    public record MarketDefinition(String city, int availablePlayers, int marketCount, String reward, int roadPoint) {}
}
