package com.fortell.boardgame.game_modules.brass;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.asLong;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.listOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.mapOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.objectList;
import static com.fortell.boardgame.game_modules.brass.BrassResourceModel.*;

/** Resource query JSON boundary; selection rules are shared with typed allocation validation. */
final class BrassResourceSources {
    private BrassResourceSources() {}

    static Map<String, Object> availableResourceSources(Map<String, Object> state) {
        List<Map<String, Object>> industries = listOf(mapOf(state.get("board")).get("industries"));
        Map<String, Object> sources = new LinkedHashMap<>();
        sources.put("coal", resourceSourceItems(industries, ResourceType.COAL));
        sources.put("iron", resourceSourceItems(industries, ResourceType.IRON));
        sources.put("beer", resourceSourceItems(industries, ResourceType.BEER));
        sources.put("marketCoal", objectList(mapOf(state.get("market")).get("coal")).size());
        sources.put("marketIron", objectList(mapOf(state.get("market")).get("iron")).size());
        return sources;
    }

    private static List<Map<String, Object>> resourceSourceItems(List<Map<String, Object>> industries, ResourceType type) {
        return BrassTypedResources.stockIndices(BrassBoardAdapter.industries(industries), type).stream()
                .map(index -> resourceSourceItem(industries.get(index), type.key)).toList();
    }

    static List<Map<String, Object>> coalSourceItems(Map<String, Object> board, List<String> destinations) {
        return eligibleItems(board, new ResourceRequirement("coal", 1, destinations == null ? List.of() : destinations, null, ""), 0);
    }

    static List<Map<String, Object>> ironSourceItems(List<Map<String, Object>> industries) {
        return resourceSourceItems(industries, ResourceType.IRON);
    }

    static List<Map<String, Object>> beerSourceItems(Map<String, Object> board, String saleCity, long actorId) {
        return eligibleItems(board, new ResourceRequirement("beer", 1, List.of(saleCity), null, ""), actorId);
    }

    static List<Map<String, Object>> beerSourceItemsToAny(Map<String, Object> board, List<String> destinations, long actorId) {
        return eligibleItems(board, new ResourceRequirement("beer", 1, destinations, null, ""), actorId);
    }
    private static List<Map<String, Object>> eligibleItems(Map<String, Object> board, ResourceRequirement need, long actor) {
        var industries = listOf(board.get("industries"));
        return BrassTypedResources.eligibleIndices(BrassResourceAdapter.board(board), need, actor).stream()
                .map(index -> resourceSourceItem(industries.get(index), need.type().key)).toList();
    }

    static List<Map<String, Object>> merchantSourceItems(Map<String, Object> state, Map<String, Object> saleTile) {
        Map<String, Object> board = mapOf(state.get("board"));
        long sellerId = asLong(saleTile.get("ownerId"));
        var merchants = listOf(mapOf(state.get("market")).get("beerMerchants"));
        return BrassTypedResources.merchantIndices(BrassResourceAdapter.context(state), BrassBoardAdapter.industry(saleTile)).stream()
                .map(merchants::get)
                .map(merchant -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", merchant.get("id"));
                    item.put("city", merchant.get("city"));
                    item.put("reward", merchant.get("reward"));
                    item.put("beer", merchant.get("beer"));
                    item.put("acceptedIndustryTypes", merchant.get("acceptedIndustryTypes"));
                    item.put("beerSources", beerSourceItems(board, Objects.toString(merchant.get("city"), ""), sellerId));
                    return item;
                })
                .toList();
    }

    static Map<String, Object> resourceSourceItem(Map<String, Object> tile, String resourceField) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", tile.get("id"));
        item.put("industryType", tile.get("industryType"));
        item.put("resourceType", resourceField);
        item.put("ownerId", tile.get("ownerId"));
        item.put("ownerName", tile.get("ownerName"));
        item.put("city", tile.get("city"));
        item.put("amount", tile.get(resourceField));
        return item;
    }

    static boolean merchantAcceptsIndustry(Map<String, Object> merchant, String industryType) {
        return BrassTypedResources.accepts(BrassResourceAdapter.merchant(merchant), industryType);
    }

    static Map<String, Object> findMerchant(Map<String, Object> state, String merchantId) {
        return listOf(mapOf(state.get("market")).get("beerMerchants")).stream()
                .filter(merchant -> merchantId.equals(Objects.toString(merchant.get("id"), "")))
                .findFirst()
                .orElse(null);
    }

}
