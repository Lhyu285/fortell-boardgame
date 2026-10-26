package com.fortell.boardgame.game_modules.brass;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static com.fortell.boardgame.game_modules.brass.BrassConnectivityService.cityConnectedToAnyMarket;
import static com.fortell.boardgame.game_modules.brass.BrassIndustryService.flipTileWithoutNotice;
import static com.fortell.boardgame.game_modules.brass.BrassIndustryService.freeDevelopFirstAvailable;
import static com.fortell.boardgame.game_modules.brass.BrassIndustryService.freeDevelopSelected;
import static com.fortell.boardgame.game_modules.brass.BrassIndustryService.isDepleteTile;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.asLong;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.listOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.mapOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.objectList;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.parseInteger;
import static com.fortell.boardgame.game_modules.brass.BrassPlayerService.addIncome;
import static com.fortell.boardgame.game_modules.brass.BrassPlayerService.addVictoryPoints;
import static com.fortell.boardgame.game_modules.brass.BrassPlayerService.gainMoney;
import static com.fortell.boardgame.game_modules.brass.BrassPlayerService.payMoney;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.resourceCnName;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;

/** Resource Service rules; all working state is passed explicitly. */
final class BrassResourceService {
    private BrassResourceService() {}

    static String consumeMerchantBeer(Map<String, Object> state, long actorId, String merchantId, String saleCity,
                                       String industryType, int amount, String freeDevelopIndustryType) {
        Map<String, Object> market = mapOf(state.get("market"));
        List<Map<String, Object>> merchants = listOf(market.get("beerMerchants"));
        Map<String, Object> merchant = merchants.stream()
                .filter(item -> merchantId.equals(Objects.toString(item.get("id"), "")))
                .findFirst()
                .orElse(null);
        BrassTypedResources.merchantForSale(merchant == null ? null : BrassResourceAdapter.merchant(merchant),
                BrassBoardAdapter.network(mapOf(state.get("board"))), saleCity, industryType, amount);

        merchant.put("beer", BrassTypedResources.consume(parseInteger(merchant.get("beer"), 0), amount).remaining());
        if (parseInteger(merchant.get("beer"), 0) == 0) {
            merchant.put("used", true);
        }
        market.put("beerMerchants", merchants);
        state.put("market", market);
        // Consuming beer and rewarding a sale are separate: the sold tile must flip first.
        return "\u4f7f\u7528 " + cityCnName(Objects.toString(merchant.get("city"), "")) + " \u8d38\u6613\u5546\u5564\u9152 x" + amount + "\u3002";
    }

    static void validateMerchantBeerForSale(Map<String, Object> state, long actorId, String merchantId, String saleCity,
                                             String industryType, int amount) {
        Map<String, Object> market = mapOf(state.get("market"));
        List<Map<String, Object>> merchants = listOf(market.get("beerMerchants"));
        Map<String, Object> merchant = merchants.stream()
                .filter(item -> merchantId.equals(Objects.toString(item.get("id"), "")))
                .findFirst()
                .orElse(null);
        BrassTypedResources.merchantForSale(merchant == null ? null : BrassResourceAdapter.merchant(merchant),
                BrassBoardAdapter.network(mapOf(state.get("board"))), saleCity, industryType, amount);
    }

    static String applyMerchantReward(Map<String, Object> state, long actorId, Map<String, Object> merchant,
                                       String freeDevelopIndustryType) {
        var reward = BrassTypedResources.reward(Objects.toString(merchant.get("city"), ""), Objects.toString(merchant.get("reward"), ""));
        return switch (reward.type()) {
            case INCOME -> {
                addIncome(state, actorId, reward.amount());
                yield "收入等级 +" + reward.amount() + "。";
            }
            case VP -> {
                addVictoryPoints(state, actorId, reward.amount());
                yield "获得 " + reward.amount() + "VP。";
            }
            case MONEY -> {
                gainMoney(state, actorId, reward.amount());
                yield "获得5英镑。";
            }
            case DEVELOP -> {
                String industryType = freeDevelopIndustryType.isBlank() ? freeDevelopFirstAvailable(state, actorId)
                        : freeDevelopSelected(state, actorId, freeDevelopIndustryType);
                yield industryType.isBlank() ? "没有可免费研发的板块。" : "免费研发 " + industryNameCn(industryType) + "。";
            }
            case NONE -> "";
        };
    }

    static String consumeMarketResource(Map<String, Object> state, long actorId, String type, int amount) {
        Map<String, Object> market = mapOf(state.get("market"));
        List<Object> values = objectList(market.get(type));
        var purchase = BrassTypedResources.purchase(values.stream().map(value -> parseInteger(value, 0)).toList(),
                amount, "coal".equals(type) ? DISTANT_COAL_PRICE : DISTANT_IRON_PRICE);
        int totalCost = purchase.totalCost();
        for (int price : purchase.prices()) {
            payMoney(state, actorId, price);
        }
        values.subList(0, purchase.takenFromTrack()).clear();
        market.put(type, values);
        state.put("market", market);
        return "从市场消耗" + resourceCnName(type) + " x" + amount + "，花费" + totalCost + "英镑。";
    }

    static List<String> sellNewResourceToMarket(Map<String, Object> state, Map<String, Object> tile) {
        if (!isDepleteTile(tile)) {
            return List.of();
        }
        String industryType = Objects.toString(tile.get("industryType"), "");
        if ("iron_works".equals(industryType)) {
            return sellIronToMarket(state, tile);
        }
        if ("coal_mine".equals(industryType)) {
            return sellCoalToMarket(state, tile);
        }
        return List.of();
    }

    static List<String> sellIronToMarket(Map<String, Object> state, Map<String, Object> tile) {
        int iron = parseInteger(tile.get("iron"), 0);
        if (iron <= 0) {
            return List.of();
        }
        Map<String, Object> market = mapOf(state.get("market"));
        List<Object> ironMarket = objectList(market.get("iron"));
        var restock = BrassTypedResources.restock(iron, ironMarket.size(), MAX_IRON_MARKET_SIZE, FULL_IRON_MARKET);
        int sold = restock.prices().size(), gained = restock.gained();
        for (int price : restock.prices()) {
            gainMoney(state, asLong(tile.get("ownerId")), price);
            ironMarket.add(0, price);
        }
        iron = restock.remaining();
        tile.put("iron", iron);
        market.put("iron", ironMarket);
        state.put("market", market);
        if (sold <= 0) {
            return List.of();
        }
        List<String> notices = new ArrayList<>();
        notices.add("玩家" + tile.get("ownerName") + "因" + cityCnName(Objects.toString(tile.get("city"), ""))
                + "的钢铁厂向市场卖出" + sold + "个铁，获得" + gained + "英镑");
        if (iron == 0) {
            notices.add(flipTileWithoutNotice(state, tile));
        }
        return notices.stream().filter(notice -> !notice.isBlank()).toList();
    }

    static List<String> sellCoalToMarket(Map<String, Object> state, Map<String, Object> tile) {
        int coal = parseInteger(tile.get("coal"), 0);
        if (coal <= 0 || !cityConnectedToAnyMarket(mapOf(state.get("board")), Objects.toString(tile.get("city"), ""))) {
            return List.of();
        }
        Map<String, Object> market = mapOf(state.get("market"));
        List<Object> coalMarket = objectList(market.get("coal"));
        var restock = BrassTypedResources.restock(coal, coalMarket.size(), MAX_COAL_MARKET_SIZE, FULL_COAL_MARKET);
        int sold = restock.prices().size(), gained = restock.gained();
        for (int price : restock.prices()) {
            gainMoney(state, asLong(tile.get("ownerId")), price);
            coalMarket.add(0, price);
        }
        coal = restock.remaining();
        tile.put("coal", coal);
        market.put("coal", coalMarket);
        state.put("market", market);
        if (sold <= 0) {
            return List.of();
        }
        List<String> notices = new ArrayList<>();
        notices.add("玩家" + tile.get("ownerName") + "因" + cityCnName(Objects.toString(tile.get("city"), ""))
                + "的煤矿场向市场卖出" + sold + "个煤，获得" + gained + "英镑");
        if (coal == 0) {
            notices.add(flipTileWithoutNotice(state, tile));
        }
        return notices.stream().filter(notice -> !notice.isBlank()).toList();
    }

    static String merchantCityText(Map<String, Object> state, String merchantId, String fallbackCity) {
        if (merchantId == null || merchantId.isBlank()) {
            return cityCnName(fallbackCity);
        }
        return listOf(mapOf(state.get("market")).get("beerMerchants")).stream()
                .filter(merchant -> merchantId.equals(Objects.toString(merchant.get("id"), "")))
                .map(merchant -> cityCnName(Objects.toString(merchant.get("city"), fallbackCity)))
                .findFirst()
                .orElseGet(() -> cityCnName(fallbackCity));
    }
}
