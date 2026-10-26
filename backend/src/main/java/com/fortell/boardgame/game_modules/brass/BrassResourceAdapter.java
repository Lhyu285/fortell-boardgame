package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.ApiException;
import org.springframework.http.HttpStatus;
import java.util.*;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;
import static com.fortell.boardgame.game_modules.brass.BrassResourceModel.*;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;

/** Resource JSON boundary. Unknown/sparse save fields are retained; never serialize typed views wholesale. */
final class BrassResourceAdapter {
    private BrassResourceAdapter() {}
    static ResourceBoard board(Map<String, Object> board) {
        return new ResourceBoard(BrassBoardAdapter.network(board), BrassBoardAdapter.industries(listOf(board.get("industries"))));
    }
    static Merchant merchant(Map<String, Object> value) {
        return new Merchant(Objects.toString(value.get("id"), ""), Objects.toString(value.get("city"), ""),
                parseInteger(value.get("beer"), 0), Boolean.TRUE.equals(value.get("blank")), Boolean.TRUE.equals(value.get("wild")),
                stringList(value.get("acceptedIndustryTypes")));
    }
    static ResourceContext context(Map<String, Object> state) {
        return new ResourceContext(board(mapOf(state.get("board"))),
                listOf(mapOf(state.get("market")).get("beerMerchants")).stream().map(BrassResourceAdapter::merchant).toList());
    }
    static List<String> coalMarkets() {
        return (MAP_MARKETS.isEmpty() ? fallbackMarkets() : MAP_MARKETS).stream().map(m -> Objects.toString(m.get("city"), "")).toList();
    }
    static List<ResourceSelection> selections(Map<String, Object> payload) {
        if (!(payload.get("resourceSelections") instanceof List<?> list))
            throw new ApiException(HttpStatus.BAD_REQUEST, "资源选择格式不正确");
        var result = new ArrayList<ResourceSelection>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) throw new ApiException(HttpStatus.BAD_REQUEST, "资源选择格式不正确");
            var value = mapOf(raw);
            String type = Objects.toString(value.get("type"), Objects.toString(value.get("resourceType"), ""));
            String kind = Objects.toString(value.get("sourceType"), "");
            String id = Objects.toString(value.get("sourceId"), "");
            int amount = parseInteger(value.get("amount"), 0);
            int index = parseInteger(value.get("requirementIndex"), 0);
            if (amount <= 0 || id.isBlank() || !List.of("coal", "iron", "beer").contains(type)
                    || !List.of("industry", "merchant", "market").contains(kind))
                throw new ApiException(HttpStatus.BAD_REQUEST, "资源选择不合法");
            result.add(new ResourceSelection(ResourceType.from(type), SourceType.from(kind), id, index, amount));
        }
        return List.copyOf(result);
    }

    static List<String> consumeIndustry(Map<String, Object> state, ResourceAllocation allocation) {
        var selection = allocation.selection();
        var board = mapOf(state.get("board"));
        var industries = listOf(board.get("industries"));
        var tile = BrassIndustryService.findTile(industries, selection.sourceId());
        if (tile == null) throw new ApiException(HttpStatus.BAD_REQUEST, "选择的资源来源不可用");
        var notices = new ArrayList<String>();
        int steps = selection.type() == ResourceType.BEER ? 1 : selection.amount();
        for (int i = 0; i < steps; i++) {
            int used = selection.type() == ResourceType.BEER ? selection.amount() : 1;
            var change = BrassTypedResources.consume(parseInteger(tile.get(selection.type().key), 0), used);
            tile.put(selection.type().key, change.remaining());
            String name = switch (selection.type()) { case COAL -> "煤"; case IRON -> "铁"; case BEER -> "啤酒"; };
            String notice = "使用 " + tile.get("ownerName") + " 在 " + cityCnName(Objects.toString(tile.get("city"), ""))
                    + " 的" + name + (selection.type() == ResourceType.BEER ? " x" + used : "") + "。";
            if (change.depleted()) {
                BrassIndustryService.flipTile(state, tile);
                notice += switch (selection.type()) { case COAL -> "该煤矿已翻面。"; case IRON -> "该铁厂已翻面。"; case BEER -> "该酿酒厂已翻面。"; };
            }
            notices.add(notice);
        }
        board.put("industries", industries); state.put("board", board);
        return notices;
    }
}
