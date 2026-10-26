package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import static com.fortell.boardgame.game_modules.brass.BrassConnectivityService.beerSourceReachable;
import static com.fortell.boardgame.game_modules.brass.BrassConnectivityService.beerSourceReachableToAny;
import static com.fortell.boardgame.game_modules.brass.BrassConnectivityService.coalMarketReachable;
import static com.fortell.boardgame.game_modules.brass.BrassConnectivityService.coalSourceReachable;
import static com.fortell.boardgame.game_modules.brass.BrassIndustryService.findTile;
import static com.fortell.boardgame.game_modules.brass.BrassIndustryService.flipTile;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.asLong;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.listOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.mapOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.objectList;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.parseInteger;
import static com.fortell.boardgame.game_modules.brass.BrassPlayerService.payMoney;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;

/** Resource Planner rules; all working state is passed explicitly. */
final class BrassResourcePlanner {
    private BrassResourcePlanner() {}

    static String consumeBeer(Map<String, Object> state, List<Map<String, Object>> industries, long actorId,
                               String preferredTileId, String saleCity, int amount) {
        Map<String, Object> board = mapOf(state.get("board"));
        int remaining = amount;
        List<String> notices = new ArrayList<>();
        Map<String, Object> source = null;
        if (!preferredTileId.isBlank()) {
            source = findTile(industries, preferredTileId);
            if (source == null
                    || !"brewery".equals(source.get("industryType"))
                    || parseInteger(source.get("beer"), 0) < remaining
                    || !beerSourceReachable(board, source, saleCity, actorId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "\u6307\u5b9a\u7684\u5564\u9152\u6765\u6e90\u4e0d\u53ef\u7528");
            }
            notices.add(consumeBeerFromTile(state, source, remaining));
            return String.join("", notices);
        }
        if (source == null) {
            source = industries.stream()
                    .filter(tile -> "brewery".equals(tile.get("industryType"))
                            && parseInteger(tile.get("beer"), 0) > 0
                            && asLong(tile.get("ownerId")) == actorId)
                    .findFirst()
                    .orElse(null);
        }
        if (source != null) {
            int consumed = Math.min(remaining, parseInteger(source.get("beer"), 0));
            notices.add(consumeBeerFromTile(state, source, consumed));
            remaining -= consumed;
        }
        while (remaining > 0) {
            source = industries.stream()
                    .filter(tile -> "brewery".equals(tile.get("industryType"))
                            && parseInteger(tile.get("beer"), 0) > 0
                            && beerSourceReachable(board, tile, saleCity, actorId))
                    .findFirst()
                    .orElse(null);
            if (source == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "\u6ca1\u6709\u53ef\u7528\u7684\u5564\u9152\u6765\u6e90");
            }
            int consumed = Math.min(remaining, parseInteger(source.get("beer"), 0));
            notices.add(consumeBeerFromTile(state, source, consumed));
            remaining -= consumed;
        }
        return String.join("", notices);
    }

    static String consumeNetworkBeer(Map<String, Object> state, List<Map<String, Object>> industries, long actorId,
                                      String preferredTileId, List<String> destinations, int amount) {
        Map<String, Object> board = mapOf(state.get("board"));
        int remaining = amount;
        List<String> notices = new ArrayList<>();
        Map<String, Object> source = null;
        if (!preferredTileId.isBlank()) {
            source = findTile(industries, preferredTileId);
            if (source == null
                    || !"brewery".equals(source.get("industryType"))
                    || parseInteger(source.get("beer"), 0) < remaining
                    || !beerSourceReachableToAny(board, source, destinations, actorId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "\u6307\u5b9a\u7684\u5564\u9152\u6765\u6e90\u4e0d\u53ef\u7528");
            }
            notices.add(consumeBeerFromTile(state, source, remaining));
            board.put("industries", industries);
            state.put("board", board);
            return String.join("", notices);
        }
        source = industries.stream()
                .filter(tile -> "brewery".equals(tile.get("industryType"))
                        && parseInteger(tile.get("beer"), 0) > 0
                        && asLong(tile.get("ownerId")) == actorId)
                .findFirst()
                .orElse(null);
        if (source != null) {
            int consumed = Math.min(remaining, parseInteger(source.get("beer"), 0));
            notices.add(consumeBeerFromTile(state, source, consumed));
            remaining -= consumed;
        }
        while (remaining > 0) {
            source = industries.stream()
                    .filter(tile -> "brewery".equals(tile.get("industryType"))
                            && parseInteger(tile.get("beer"), 0) > 0
                            && beerSourceReachableToAny(board, tile, destinations, actorId))
                    .findFirst()
                    .orElse(null);
            if (source == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "\u6ca1\u6709\u53ef\u7528\u7684\u5564\u9152\u6765\u6e90");
            }
            int consumed = Math.min(remaining, parseInteger(source.get("beer"), 0));
            notices.add(consumeBeerFromTile(state, source, consumed));
            remaining -= consumed;
        }
        board.put("industries", industries);
        return String.join("", notices);
    }

    static String consumeBeerFromTile(Map<String, Object> state, Map<String, Object> source, int amount) {
        source.put("beer", BrassTypedResources.consume(parseInteger(source.get("beer"), 0), amount).remaining());
        String notice = "\u4f7f\u7528 " + source.get("ownerName") + " \u5728 " + cityCnName(Objects.toString(source.get("city"), "")) + " \u7684\u5564\u9152 x" + amount + "\u3002";
        if (parseInteger(source.get("beer"), 0) == 0) {
            flipTile(state, source);
            notice += "\u8be5\u917f\u9152\u5382\u5df2\u7ffb\u9762\u3002";
        }
        return notice;
    }

    static String consumeCoal(Map<String, Object> state, long actorId, String preferredTileId, List<String> destinations) {
        Map<String, Object> board = mapOf(state.get("board"));
        List<Map<String, Object>> industries = listOf(board.get("industries"));
        Map<String, Object> source = null;
        if (!preferredTileId.isBlank()) {
            source = findTile(industries, preferredTileId);
            if (source == null
                    || !"coal_mine".equals(source.get("industryType"))
                    || parseInteger(source.get("coal"), 0) <= 0
                    || !coalSourceReachable(board, source, destinations)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "\u6307\u5b9a\u7684\u7164\u6765\u6e90\u4e0d\u53ef\u7528");
            }
        }
        if (source == null) {
            source = industries.stream()
                    .filter(tile -> "coal_mine".equals(tile.get("industryType"))
                            && parseInteger(tile.get("coal"), 0) > 0
                            && asLong(tile.get("ownerId")) == actorId
                            && coalSourceReachable(board, tile, destinations))
                    .findFirst()
                    .orElse(null);
        }
        if (source == null) {
            source = industries.stream()
                    .filter(tile -> "coal_mine".equals(tile.get("industryType"))
                            && parseInteger(tile.get("coal"), 0) > 0
                            && coalSourceReachable(board, tile, destinations))
                    .findFirst()
                    .orElse(null);
        }
        if (source != null) {
            source.put("coal", BrassTypedResources.consume(parseInteger(source.get("coal"), 0), 1).remaining());
            String notice = "\u4f7f\u7528 " + source.get("ownerName") + " \u5728 " + cityCnName(Objects.toString(source.get("city"), "")) + " \u7684\u7164\u3002";
            if (parseInteger(source.get("coal"), 0) == 0) {
                flipTile(state, source);
                notice += "\u8be5\u7164\u77ff\u5df2\u7ffb\u9762\u3002";
            }
            board.put("industries", industries);
            state.put("board", board);
            return notice;
        }
        if (!coalMarketReachable(board, destinations)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u65e0\u6cd5\u83b7\u5f97\u7164\uff1a\u6240\u9700\u4f4d\u7f6e\u672a\u8fde\u63a5\u5230\u4efb\u4f55\u7164\u77ff\u6216\u5e02\u573a");
        }
        Map<String, Object> market = mapOf(state.get("market"));
        List<Object> coalMarket = objectList(market.get("coal"));
        if (!coalMarket.isEmpty()) {
            int price = parseInteger(coalMarket.remove(0), 0);
            payMoney(state, actorId, price);
            market.put("coal", coalMarket);
            state.put("market", market);
            return "\u4ece\u7164\u5e02\u573a\u8d2d\u4e70\u7164\uff0c\u82b1\u8d39 " + price + "\u82f1\u9551\u3002";
        }
        payMoney(state, actorId, DISTANT_COAL_PRICE);
        state.put("market", market);
        return "\u4ece\u8fdc\u65b9\u5e02\u573a\u8d2d\u4e70\u7164\uff0c\u82b1\u8d39 " + DISTANT_COAL_PRICE + "\u82f1\u9551\u3002";
    }

    static String consumeIron(Map<String, Object> state, long actorId, String preferredTileId) {
        Map<String, Object> board = mapOf(state.get("board"));
        List<Map<String, Object>> industries = listOf(board.get("industries"));
        Map<String, Object> source = null;
        if (!preferredTileId.isBlank()) {
            source = findTile(industries, preferredTileId);
            if (source == null || !"iron_works".equals(source.get("industryType")) || parseInteger(source.get("iron"), 0) <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "\u6307\u5b9a\u7684\u94c1\u6765\u6e90\u4e0d\u53ef\u7528");
            }
        }
        if (source == null) {
            source = industries.stream()
                    .filter(tile -> "iron_works".equals(tile.get("industryType"))
                            && parseInteger(tile.get("iron"), 0) > 0
                            && asLong(tile.get("ownerId")) == actorId)
                    .findFirst()
                    .orElse(null);
        }
        if (source == null) {
            source = industries.stream()
                    .filter(tile -> "iron_works".equals(tile.get("industryType"))
                            && parseInteger(tile.get("iron"), 0) > 0)
                    .findFirst()
                    .orElse(null);
        }
        if (source != null) {
            source.put("iron", BrassTypedResources.consume(parseInteger(source.get("iron"), 0), 1).remaining());
            String notice = "\u4f7f\u7528 " + source.get("ownerName") + " \u5728 " + cityCnName(Objects.toString(source.get("city"), "")) + " \u7684\u94c1\u3002";
            if (parseInteger(source.get("iron"), 0) == 0) {
                flipTile(state, source);
                notice += "\u8be5\u94c1\u5382\u5df2\u7ffb\u9762\u3002";
            }
            board.put("industries", industries);
            state.put("board", board);
            return notice;
        }
        Map<String, Object> market = mapOf(state.get("market"));
        List<Object> ironMarket = objectList(market.get("iron"));
        if (!ironMarket.isEmpty()) {
            int price = parseInteger(ironMarket.remove(0), 0);
            payMoney(state, actorId, price);
            market.put("iron", ironMarket);
            state.put("market", market);
            return "\u4ece\u94c1\u5e02\u573a\u8d2d\u4e70\u94c1\uff0c\u82b1\u8d39 " + price + "\u82f1\u9551\u3002";
        }
        payMoney(state, actorId, DISTANT_IRON_PRICE);
        state.put("market", market);
        return "\u4ece\u8fdc\u65b9\u5e02\u573a\u8d2d\u4e70\u94c1\uff0c\u82b1\u8d39 " + DISTANT_IRON_PRICE + "\u82f1\u9551\u3002";
    }
}
