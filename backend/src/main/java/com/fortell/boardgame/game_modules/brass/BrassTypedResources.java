package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassBoardView.Industry;
import com.fortell.boardgame.game_modules.brass.model.BrassBoardView.Site;
import com.fortell.boardgame.models.ApiException;
import org.springframework.http.HttpStatus;
import java.util.*;
import static com.fortell.boardgame.game_modules.brass.BrassResourceModel.*;

/** Pure allocation and stock arithmetic shared by real execution and simulation adapters. */
final class BrassTypedResources {
    private BrassTypedResources() {}
    private static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }

    static int amount(Industry tile, ResourceType type) {
        return switch (type) { case COAL -> tile.coal(); case IRON -> tile.iron(); case BEER -> tile.beer(); };
    }

    static boolean accepts(Merchant merchant, String type) {
        return !merchant.blank() && (merchant.wild()
                ? List.of("cotton_mill", "manufacturer", "pottery").contains(type) : merchant.acceptedTypes().contains(type));
    }

    static List<ResourceSource> eligible(ResourceBoard board, ResourceRequirement need, long actor) {
        return eligibleIndices(board, need, actor).stream().map(board.industries()::get)
                .map(tile -> new ResourceSource(SourceType.INDUSTRY, tile.id(), amount(tile, need.type()))).toList();
    }
    static List<Integer> stockIndices(List<Industry> industries, ResourceType type) {
        String industryType = switch (type) { case COAL -> "coal_mine"; case IRON -> "iron_works"; case BEER -> "brewery"; };
        return java.util.stream.IntStream.range(0, industries.size()).filter(index -> {
            var tile = industries.get(index);
            return industryType.equals(tile.type()) && BrassTypedIndustries.deplete(tile) && amount(tile, type) > 0;
        }).boxed().toList();
    }
    static List<Integer> eligibleIndices(ResourceBoard board, ResourceRequirement need, long actor) {
        return stockIndices(board.industries(), need.type()).stream().filter(index -> {
            var tile = board.industries().get(index);
            return switch (need.type()) {
                    case COAL -> BrassTypedConnectivity.coalReachable(board.network(), tile.city(), need.destinations());
                    case IRON -> true;
                    case BEER -> BrassTypedConnectivity.beerReachable(board.network(), new Site(tile.ownerId(), tile.city()),
                            need.saleCity() != null && !need.saleCity().isBlank() ? List.of(need.saleCity()) : need.destinations(), actor);
            };
        }).toList();
    }
    static List<Integer> merchantIndices(ResourceContext context, Industry tile) {
        return java.util.stream.IntStream.range(0, context.merchants().size()).filter(index -> {
            var merchant = context.merchants().get(index);
            return accepts(merchant, tile.type()) && BrassTypedConnectivity.connected(context.board().network(), tile.city(), merchant.city());
        }).boxed().toList();
    }

    static List<ResourceAllocation> validate(ResourceContext context, long actor, List<ResourceRequirement> needs,
                                              List<ResourceSelection> selections, List<String> coalMarkets) {
        var allocations = new ArrayList<ResourceAllocation>();
        for (int index = 0; index < needs.size(); index++) {
            int currentIndex = index;
            var need = needs.get(index);
            var current = selections.stream().filter(s -> s.requirementIndex() == currentIndex).toList();
            if (need.amount() <= 0) {
                if (!current.isEmpty()) throw invalid("不需要选择该资源");
                continue;
            }
            long total = current.stream().mapToLong(ResourceSelection::amount).sum();
            if (total != need.amount()) throw invalid(need.type() + "需要选择" + need.amount() + "个，当前选择了" + total + "个");
            ResourceBoard board = need.sourceBoard() == null ? context.board() : need.sourceBoard();
            var eligible = eligible(board, need, actor);
            long free = 0;
            for (var source : eligible) {
                long prior = selections.stream().filter(s -> s.requirementIndex() < currentIndex && s.type() == need.type()
                        && s.sourceType() == SourceType.INDUSTRY && s.sourceId().equals(source.id())).mapToLong(ResourceSelection::amount).sum();
                free += Math.max(0L, source.amount() - prior);
            }
            long selectedFree = current.stream().filter(s -> s.sourceType() == SourceType.INDUSTRY).mapToLong(ResourceSelection::amount).sum();
            long requiredFree = Math.min(need.amount(), free);
            if (need.type() != ResourceType.BEER && selectedFree != requiredFree)
                throw invalid(need.type() + "必须先选择全部所需的免费资源（当前应选择" + requiredFree + "个）");
            for (var selection : current) {
                if (selection.type() != need.type()) throw invalid("资源类型与行动需求不匹配");
                ResourceSource source = resolve(context, board, need, selection, eligible, coalMarkets);
                if (source == null) throw invalid("选择的资源来源不可用");
                long selected = current.stream().filter(s -> s.sourceType() == selection.sourceType()
                        && s.sourceId().equals(selection.sourceId())).mapToLong(ResourceSelection::amount).sum();
                if (selected > source.amount()) throw invalid("选择的资源数量超过来源当前库存");
                allocations.add(new ResourceAllocation(selection, need, source));
            }
        }
        if (selections.stream().anyMatch(s -> s.requirementIndex() < 0 || s.requirementIndex() >= needs.size()))
            throw invalid("资源选择对应的需求不存在");
        Map<SourceKey, Long> totals = new LinkedHashMap<>();
        for (var allocation : allocations) {
            var s = allocation.selection();
            totals.merge(new SourceKey(s.type(), s.sourceType(), s.sourceId()), (long) s.amount(), Long::sum);
        }
        for (var allocation : allocations) {
            var s = allocation.selection();
            if (totals.get(new SourceKey(s.type(), s.sourceType(), s.sourceId())) > allocation.source().amount())
                throw invalid("同一资源来源的总选择数量超过库存");
        }
        return List.copyOf(allocations);
    }

    private static ResourceSource resolve(ResourceContext context, ResourceBoard board, ResourceRequirement need,
                                            ResourceSelection selection, List<ResourceSource> eligible, List<String> coalMarkets) {
        if (selection.sourceType() == SourceType.MARKET) {
            if (need.type() == ResourceType.BEER || !("market:" + need.type()).equals(selection.sourceId())) return null;
            if (need.type() == ResourceType.COAL && !BrassTypedConnectivity.marketReachable(board.network(), need.destinations(), coalMarkets)) return null;
            return new ResourceSource(SourceType.MARKET, selection.sourceId(), Integer.MAX_VALUE);
        }
        if (selection.sourceType() == SourceType.INDUSTRY)
            return eligible.stream().filter(s -> s.id().equals(selection.sourceId())).findFirst().orElse(null);
        if (need.type() != ResourceType.BEER) return null;
        return context.merchants().stream().filter(m -> m.id().equals(selection.sourceId()) && need.saleCity() != null
                        && accepts(m, need.saleIndustryType()) && BrassTypedConnectivity.connected(context.board().network(), need.saleCity(), m.city()))
                .map(m -> new ResourceSource(SourceType.MERCHANT, m.id(), m.beer())).findFirst().orElse(null);
    }

    static StockChange consume(int available, int amount) {
        if (amount <= 0 || amount > available) throw invalid("选择的资源数量超过来源当前库存");
        return new StockChange(available - amount, available == amount);
    }

    static void merchantForSale(Merchant merchant, com.fortell.boardgame.game_modules.brass.model.BrassBoardView.Network board,
                                String saleCity, String industryType, int amount) {
        if (merchant == null || merchant.beer() < amount) throw invalid("找不到可用的贸易商啤酒");
        if (!accepts(merchant, industryType)) throw invalid("该贸易商不接受此产业");
        if (!BrassTypedConnectivity.connected(board, saleCity, merchant.city())) throw invalid("出售城市未连接到贸易商");
    }

    static MarketPurchase purchase(List<Integer> track, int amount, int emptyPrice) {
        var prices = new ArrayList<Integer>();
        int total = 0;
        for (int i = 0; i < amount; i++) {
            int price = i < track.size() ? track.get(i) : emptyPrice;
            total = Math.addExact(total, price);
            prices.add(price);
        }
        return new MarketPurchase(prices, Math.min(amount, track.size()), total);
    }

    static Restock restock(int supply, int trackSize, int capacity, List<Integer> fullTrack) {
        var prices = new ArrayList<Integer>();
        int gained = 0;
        while (supply > 0 && trackSize < capacity) {
            int price = fullTrack.get(Math.max(0, fullTrack.size() - trackSize - 1));
            prices.add(price); gained += price; supply--; trackSize++;
        }
        return new Restock(supply, prices, gained);
    }

    static Reward reward(String city, String text) {
        if (text.contains("Income")) return new Reward(RewardType.INCOME, firstNumber(text, 2));
        if (text.contains("VP")) return new Reward(RewardType.VP, firstNumber(text, 0));
        if (text.contains("5") && "Warrington".equals(city)) return new Reward(RewardType.MONEY, 5);
        if (text.contains("Develop")) return new Reward(RewardType.DEVELOP, 0);
        return new Reward(RewardType.NONE, 0);
    }
    private static int firstNumber(String text, int fallback) {
        var match = java.util.regex.Pattern.compile("[0-9]+").matcher(text);
        if (!match.find()) return fallback;
        try { return Integer.parseInt(match.group()); } catch (NumberFormatException ignored) { return fallback; }
    }
}
