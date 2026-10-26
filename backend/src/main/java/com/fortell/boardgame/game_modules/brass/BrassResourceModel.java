package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassBoardView.Industry;
import com.fortell.boardgame.game_modules.brass.model.BrassBoardView.Network;
import java.util.List;

/** Immutable resource inputs, stage snapshots and validated results; no save/JSON objects. */
final class BrassResourceModel {
    private BrassResourceModel() {}
    enum ResourceType {
        COAL("coal"), IRON("iron"), BEER("beer");
        final String key;
        ResourceType(String key) { this.key = key; }
        static ResourceType from(String value) {
            for (var type : values()) if (type.key.equals(value)) return type;
            throw new IllegalArgumentException("Unknown resource: " + value);
        }
        @Override public String toString() { return key; }
    }
    enum SourceType {
        INDUSTRY("industry"), MERCHANT("merchant"), MARKET("market");
        final String key;
        SourceType(String key) { this.key = key; }
        static SourceType from(String value) {
            for (var type : values()) if (type.key.equals(value)) return type;
            throw new IllegalArgumentException("Unknown source: " + value);
        }
    }
    record ResourceBoard(Network network, List<Industry> industries) {
        ResourceBoard { industries = List.copyOf(industries); }
    }
    record Merchant(String id, String city, int beer, boolean blank, boolean wild,
                    List<String> acceptedTypes) {
        Merchant { acceptedTypes = List.copyOf(acceptedTypes); }
    }
    record ResourceContext(ResourceBoard board, List<Merchant> merchants) {
        ResourceContext { merchants = List.copyOf(merchants); }
    }
    record ResourceRequirement(ResourceType type, int amount, List<String> destinations,
                               String saleCity, String saleIndustryType, ResourceBoard sourceBoard) {
        ResourceRequirement { destinations = List.copyOf(destinations); }
        ResourceRequirement(String type, int amount, List<String> destinations, String saleCity, String saleIndustryType) {
            this(ResourceType.from(type), amount, destinations, saleCity, saleIndustryType, null);
        }
    }
    record ResourceSelection(ResourceType type, SourceType sourceType, String sourceId,
                             int requirementIndex, int amount) {}
    record ResourceSource(SourceType kind, String id, int amount) {}
    record ResourceAllocation(ResourceSelection selection, ResourceRequirement requirement, ResourceSource source) {}
    record SourceKey(ResourceType type, SourceType kind, String id) {}
    record StockChange(int remaining, boolean depleted) {}
    enum RewardType { INCOME, VP, MONEY, DEVELOP, NONE }
    record Reward(RewardType type, int amount) {}
    record MarketPurchase(List<Integer> prices, int takenFromTrack, int totalCost) {
        MarketPurchase { prices = List.copyOf(prices); }
    }
    record Restock(int remaining, List<Integer> prices, int gained) {
        Restock { prices = List.copyOf(prices); }
    }
}
