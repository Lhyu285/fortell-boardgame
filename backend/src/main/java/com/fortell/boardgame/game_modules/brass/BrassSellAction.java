package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.ApiException;
import com.fortell.boardgame.models.UserSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import static com.fortell.boardgame.game_modules.brass.BrassCardService.currentHand;
import static com.fortell.boardgame.game_modules.brass.BrassCardService.discardCard;
import static com.fortell.boardgame.game_modules.brass.BrassCardService.findCard;
import static com.fortell.boardgame.game_modules.brass.BrassIndustryService.findTile;
import static com.fortell.boardgame.game_modules.brass.BrassIndustryService.flipTile;
import static com.fortell.boardgame.game_modules.brass.BrassIndustryService.saleBeerRequired;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.listOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.mapOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.parseInteger;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.appendNotice;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.appendResourceNotices;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.describeOperation;
import static com.fortell.boardgame.game_modules.brass.BrassResourceModel.*;
import static com.fortell.boardgame.game_modules.brass.BrassResourcePlanner.consumeBeer;
import static com.fortell.boardgame.game_modules.brass.BrassResourceSelectionService.consumeExplicitResources;
import static com.fortell.boardgame.game_modules.brass.BrassResourceSelectionService.hasExplicitResourceSelections;
import static com.fortell.boardgame.game_modules.brass.BrassResourceSelectionService.parseResourceSelections;
import static com.fortell.boardgame.game_modules.brass.BrassResourceSelectionService.requireResourceSelectionContract;
import static com.fortell.boardgame.game_modules.brass.BrassResourceSelectionService.usesSimulationAutoPlanning;
import static com.fortell.boardgame.game_modules.brass.BrassResourceService.applyMerchantReward;
import static com.fortell.boardgame.game_modules.brass.BrassResourceService.consumeMerchantBeer;
import static com.fortell.boardgame.game_modules.brass.BrassResourceService.merchantCityText;
import static com.fortell.boardgame.game_modules.brass.BrassResourceService.validateMerchantBeerForSale;
import static com.fortell.boardgame.game_modules.brass.BrassResourceSources.findMerchant;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.cityCnName;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.industryNameCn;
import static com.fortell.boardgame.game_modules.brass.BrassTurnService.consumeAction;
import static com.fortell.boardgame.game_modules.brass.BrassTurnService.ensureTurnAcceptsAction;

/** Sell Action rules; all working state is passed explicitly. */
final class BrassSellAction {
    private BrassSellAction() {}

    static Map<String, Object> sellAction(Map<String, Object> state, UserSummary actor, BrassActionCommand command, BrassExecutionMode mode) {
        Map<String, Object> payload = command.payload();
        BrassActionInput.Sale input = (BrassActionInput.Sale) command.input();
        ensureTurnAcceptsAction(state);
        Map<String, Object> turn = mapOf(state.get("turn"));
        boolean sellInProgress = Boolean.TRUE.equals(turn.get("sellInProgress"));
        String cardId = input.cardId();
        if (payload.containsKey("tileIds")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "每次只能出售一个产业");
        }
        String tileId = input.tileId();
        if (!sellInProgress && cardId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8bf7\u9009\u62e9\u8981\u5f03\u7f6e\u7684\u624b\u724c");
        }
        if (tileId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8bf7\u9009\u62e9\u8981\u51fa\u552e\u7684\u4ea7\u4e1a");
        }
        if (!sellInProgress && findCard(currentHand(state, actor.id()), cardId) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u6240\u9009\u624b\u724c\u4e0d\u5728\u4f60\u7684\u624b\u724c\u4e2d");
        }

        Map<String, Object> board = mapOf(state.get("board"));
        List<Map<String, Object>> industries = listOf(board.get("industries"));
        Map<String, Object> tile = findTile(industries, tileId);
        var saleTile = tile == null ? null : BrassBoardAdapter.industry(tile);
        BrassTypedSale.validateTarget(saleTile, actor.id());

        String merchantId = input.merchantId();
        int beerRequired = saleBeerRequired(tile);
        Map<String, Object> targetMerchant = findMerchant(state, merchantId);
        List<ResourceRequirement> requirements = BrassTypedSale.requirements(saleTile,
                targetMerchant == null ? null : BrassResourceAdapter.merchant(targetMerchant));
        requireResourceSelectionContract(state, payload, requirements, mode);
        if (!usesSimulationAutoPlanning(mode, payload)) {
            if (merchantId.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "出售产业必须选择接受该产业的贸易商");
            }
            validateMerchantBeerForSale(state, actor.id(), merchantId, Objects.toString(tile.get("city"), ""),
                    Objects.toString(tile.get("industryType"), ""), 0);
        }
        if (!sellInProgress) {
            discardCard(state, actor.id(), cardId);
            turn.put("sellInProgress", true);
            turn.put("sellCount", 0);
        }
        List<String> resourceNotices = new ArrayList<>();
        boolean consumedMerchantBeer = false;
        if (hasExplicitResourceSelections(payload)) {
            BrassTypedSale.validateMerchants(parseResourceSelections(payload), merchantId);
            if (!requirements.isEmpty()) {
                resourceNotices.addAll(consumeExplicitResources(state, actor.id(), requirements, payload,
                        input.freeDevelopIndustryType()));
                consumedMerchantBeer = parseResourceSelections(payload).stream()
                        .anyMatch(selection -> selection.sourceType() == SourceType.MERCHANT);
            }
        } else if (merchantId.isBlank() && beerRequired > 0) {
            consumeBeer(state, industries, actor.id(), "",
                    Objects.toString(tile.get("city"), ""), beerRequired);
        } else {
            if (merchantId.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "出售产业必须选择接受该产业的贸易商");
            }
            validateMerchantBeerForSale(state, actor.id(), merchantId, Objects.toString(tile.get("city"), ""),
                    Objects.toString(tile.get("industryType"), ""), beerRequired);
            if (beerRequired > 0) {
                resourceNotices.add(consumeMerchantBeer(state, actor.id(), merchantId, Objects.toString(tile.get("city"), ""),
                        Objects.toString(tile.get("industryType"), ""), beerRequired,
                        input.freeDevelopIndustryType()));
                consumedMerchantBeer = true;
            }
        }
        if (hasExplicitResourceSelections(payload)) {
            // 资源分配会写回一份新的产业列表；重新读取，避免后续翻面把
            // 资源扣除覆盖回旧快照。
            board = mapOf(state.get("board"));
            industries = listOf(board.get("industries"));
            tile = findTile(industries, tileId);
            if (tile == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "出售目标在资源结算后不可用");
            }
        }
        flipTile(state, tile);
        board.put("industries", industries);
        state.put("board", board);
        // One bonus per sale that actually used this merchant's beer, after the flip reward.
        if (consumedMerchantBeer) {
            Map<String, Object> selectedMerchant = findMerchant(state, merchantId);
            String rewardNotice = applyMerchantReward(state, actor.id(), selectedMerchant,
                    input.freeDevelopIndustryType());
            if (!rewardNotice.isBlank()) {
                resourceNotices.add("获得" + cityCnName(Objects.toString(selectedMerchant.get("city"), ""))
                        + "贸易商奖励：" + rewardNotice);
            }
        }
        int sellCount = new BrassTypedSale.Session(true, parseInteger(turn.get("sellCount"), 0)).sold().count();
        turn.put("sellCount", sellCount);
        state.put("turn", turn);
        String soldText = cityCnName(Objects.toString(tile.get("city"), "")) + "的"
                + tile.get("level") + "级" + industryNameCn(Objects.toString(tile.get("industryType"), ""));
        describeOperation(state, actor, "sell", "出售 " + soldText);
        appendResourceNotices(state, resourceNotices);
        String merchantCityText = merchantCityText(state, merchantId, Objects.toString(tile.get("city"), ""));
        appendNotice(state, "玩家" + actor.username() + " 在 " + merchantCityText + "市场 出售了 " + soldText);
        if (consumedMerchantBeer) {
            appendNotice(state, "玩家" + actor.username() + " 因消耗 " + merchantCityText + "市场 的酒获得奖励");
        }
        return state;
    }

    static Map<String, Object> endSellAction(Map<String, Object> state, UserSummary actor) {
        ensureTurnAcceptsAction(state);
        Map<String, Object> turn = mapOf(state.get("turn"));
        BrassTypedSale.end(new BrassTypedSale.Session(Boolean.TRUE.equals(turn.get("sellInProgress")), parseInteger(turn.get("sellCount"), 0)));
        turn.remove("sellInProgress");
        turn.remove("sellCount");
        state.put("turn", turn);
        appendNotice(state, "玩家" + actor.username() + " 结束了出售行动");
        consumeAction(state, actor);
        return state;
    }
}
