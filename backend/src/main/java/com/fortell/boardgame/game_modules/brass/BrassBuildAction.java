package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.ApiException;
import com.fortell.boardgame.models.UserSummary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import static com.fortell.boardgame.game_modules.brass.BrassConnectivityService.cityBuildableWithIndustryCard;
import static com.fortell.boardgame.game_modules.brass.BrassCardService.discardCard;
import static com.fortell.boardgame.game_modules.brass.BrassCardService.findCard;
import static com.fortell.boardgame.game_modules.brass.BrassIndustryService.buildSlotIndexForOption;
import static com.fortell.boardgame.game_modules.brass.BrassIndustryService.coverableOwnFlippedTile;
import static com.fortell.boardgame.game_modules.brass.BrassIndustryService.nextAvailableBoardTile;
import static com.fortell.boardgame.game_modules.brass.BrassIndustryService.playerAlreadyBuiltInCity;
import static com.fortell.boardgame.game_modules.brass.BrassIndustryService.removeBoardTile;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.listOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.mapOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.parseInteger;
import static com.fortell.boardgame.game_modules.brass.BrassPlayerService.payMoney;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.appendNotice;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.appendResourceNotices;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.describeOperation;
import static com.fortell.boardgame.game_modules.brass.BrassResourceModel.*;
import static com.fortell.boardgame.game_modules.brass.BrassResourcePlanner.consumeCoal;
import static com.fortell.boardgame.game_modules.brass.BrassResourcePlanner.consumeIron;
import static com.fortell.boardgame.game_modules.brass.BrassResourceSelectionService.consumeExplicitResources;
import static com.fortell.boardgame.game_modules.brass.BrassResourceSelectionService.hasExplicitResourceSelections;
import static com.fortell.boardgame.game_modules.brass.BrassResourceSelectionService.requireResourceSelectionContract;
import static com.fortell.boardgame.game_modules.brass.BrassResourceSelectionService.usesSimulationAutoPlanning;
import static com.fortell.boardgame.game_modules.brass.BrassResourceService.sellNewResourceToMarket;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;
import static com.fortell.boardgame.game_modules.brass.BrassTurnService.consumeAction;
import static com.fortell.boardgame.game_modules.brass.BrassTurnService.ensureTurnAcceptsAction;

/** Build Action rules; all working state is passed explicitly. */
final class BrassBuildAction {
    private BrassBuildAction() {}

    static Map<String, Object> buildAction(Map<String, Object> state, UserSummary actor, BrassActionCommand command, BrassExecutionMode mode) {
        Map<String, Object> payload = command.payload();
        BrassActionInput.Build input = (BrassActionInput.Build) command.input();
        ensureTurnAcceptsAction(state);
        String city = input.city();
        String industryType = input.industryType();
        String cardId = input.cardId();
        BrassTypedBuild.validateSite(city, industryType, citySlotOptions(), INDUSTRY_TYPES);

        Map<String, Object> hands = mapOf(state.get("hands"));
        List<Map<String, Object>> hand = listOf(hands.get(String.valueOf(actor.id())));
        Map<String, Object> card = findCard(hand, cardId);
        Map<String, Object> board = mapOf(state.get("board"));
        List<Map<String, Object>> industries = listOf(board.get("industries"));
        int requestedSlotIndex = input.slotIndex();
        BrassTypedBuild.validateCard(card == null ? null : BrassPersonalAdapter.card(card), city, industryType,
                cityBuildableWithIndustryCard(board, actor.id(), city));

        Map<String, Object> nextBoardTile = nextAvailableBoardTile(state, actor.id(), industryType);
        int nextLevel = parseInteger(nextBoardTile.get("level"), 0);
        boolean alreadyBuilt = playerAlreadyBuiltInCity(industries, actor.id(), city);
        if (requestedSlotIndex < 0 && "canal".equals(state.get("era")) && alreadyBuilt) {
            var own = coverableOwnFlippedTile(industries, actor.id(), city, industryType, nextLevel);
            if (!own.isEmpty()) requestedSlotIndex = parseInteger(own.get("slotIndex"), 0);
        }
        int slotIndex = requestedSlotIndex >= 0
                ? buildSlotIndexForOption(state, industries, actor.id(), city, industryType, requestedSlotIndex)
                : buildSlotIndexForOption(state, industries, actor.id(), city, industryType);
        if (slotIndex < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u6ca1\u6709\u53ef\u7528\u7684\u57ce\u5e02\u69fd\u4f4d");
        }
        Map<String, Object> ownCoveredTile = coverableOwnFlippedTile(industries, actor.id(), city, industryType, slotIndex, nextLevel);
        BrassTypedBuild.validateCityLimit("canal".equals(state.get("era")),
                alreadyBuilt, !ownCoveredTile.isEmpty());
        // Slot validation above enforces ownership/type/shortage rules. Remove the exact occupant
        // before choosing resources, so discarded stock cannot pay for its own replacement.
        industries.removeIf(tile -> city.equals(Objects.toString(tile.get("city"), ""))
                && parseInteger(tile.get("slotIndex"), 0) == slotIndex);
        board.put("industries", industries);
        state.put("board", board);

        Map<String, Object> boardTile = nextBoardTile;
        var plan = BrassTypedBuild.plan(BrassActionAdapter.buildDefinition(boardTile, industryType, Objects.toString(state.get("era"), "canal")), city);
        int cost = plan.cost(), coalCost = plan.coalCost(), ironCost = plan.ironCost();
        List<ResourceRequirement> requirements = plan.requirements();
        requireResourceSelectionContract(state, payload, requirements, mode);
        payMoney(state, actor.id(), cost);
        List<String> resourceNotices = new ArrayList<>();
        if (hasExplicitResourceSelections(payload) && !requirements.isEmpty()) {
            resourceNotices.addAll(consumeExplicitResources(state, actor.id(), requirements, payload, ""));
        } else if (usesSimulationAutoPlanning(mode, payload)) {
            for (int count = 0; count < coalCost; count++) {
                resourceNotices.add(consumeCoal(state, actor.id(), "", List.of(city)));
            }
            for (int count = 0; count < ironCost; count++) {
                resourceNotices.add(consumeIron(state, actor.id(), ""));
            }
        }
        removeBoardTile(state, actor.id(), industryType, Objects.toString(boardTile.get("id"), ""));
        discardCard(state, actor.id(), cardId);
        board = mapOf(state.get("board"));
        industries = listOf(board.get("industries"));

        Map<String, Object> tile = new LinkedHashMap<>();
        tile.put("id", "tile_" + actor.id() + "_" + System.nanoTime());
        tile.put("sourceTileId", boardTile.get("id"));
        tile.put("ownerId", actor.id());
        tile.put("ownerName", actor.username());
        tile.put("city", city);
        tile.put("slotIndex", slotIndex);
        tile.put("industryType", industryType);
        tile.put("industryName", industryNameCn(industryType));
        tile.put("level", boardTile.get("level"));
        tile.put("era", state.get("era"));
        tile.put("flipped", false);
        tile.put("cost", cost);
        int builtResourceAmount = plan.stock();
        tile.put("coal", "coal_mine".equals(industryType) ? builtResourceAmount : 0);
        tile.put("iron", "iron_works".equals(industryType) ? builtResourceAmount : 0);
        tile.put("beer", "brewery".equals(industryType) ? builtResourceAmount : 0);
        tile.put("coalCost", boardTile.get("coalCost"));
        tile.put("ironCost", boardTile.get("ironCost"));
        tile.put("saleBeerCost", boardTile.get("saleBeerCost"));
        tile.put("roadPoints", boardTile.get("roadPoints"));
        tile.put("flipType", boardTile.get("flipType"));
        tile.put("incomeReward", boardTile.get("incomeReward"));
        tile.put("victoryPoints", boardTile.get("victoryPoints"));
        industries.add(tile);
        board.put("industries", industries);
        state.put("board", board);
        List<String> marketNotices = sellNewResourceToMarket(state, tile);

        describeOperation(state, actor, "build", "建造" + cityCnName(city) + " " + industryNameCn(industryType));
        appendResourceNotices(state, resourceNotices);
        for (int index = marketNotices.size() - 1; index >= 0; index--) {
            appendNotice(state, marketNotices.get(index));
        }
        appendNotice(state, "玩家" + actor.username() + " 在 " + cityCnName(city) + " 建造了 " + industryNameCn(industryType));
        consumeAction(state, actor);
        return state;
    }
}
