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
import static com.fortell.boardgame.game_modules.brass.BrassIndustryService.incrementDevelopment;
import static com.fortell.boardgame.game_modules.brass.BrassIndustryService.lowestDevelopableBoardTileOrThrow;
import static com.fortell.boardgame.game_modules.brass.BrassIndustryService.removeBoardTile;
import static com.fortell.boardgame.game_modules.brass.BrassPlayerService.playerMoney;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.appendNotice;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.appendResourceNotices;
import static com.fortell.boardgame.game_modules.brass.BrassResourceModel.*;
import static com.fortell.boardgame.game_modules.brass.BrassResourcePlanner.consumeIron;
import static com.fortell.boardgame.game_modules.brass.BrassResourceSelectionService.consumeExplicitResources;
import static com.fortell.boardgame.game_modules.brass.BrassResourceSelectionService.hasExplicitResourceSelections;
import static com.fortell.boardgame.game_modules.brass.BrassResourceSelectionService.requireResourceSelectionContract;
import static com.fortell.boardgame.game_modules.brass.BrassResourceSelectionService.usesSimulationAutoPlanning;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;
import static com.fortell.boardgame.game_modules.brass.BrassTurnService.consumeAction;
import static com.fortell.boardgame.game_modules.brass.BrassTurnService.ensureTurnAcceptsAction;

/** Develop Action rules; all working state is passed explicitly. */
final class BrassDevelopAction {
    private BrassDevelopAction() {}

    static Map<String, Object> developAction(Map<String, Object> state, UserSummary actor, BrassActionCommand command, BrassExecutionMode mode) {
        Map<String, Object> payload = command.payload();
        BrassActionInput.Develop input = (BrassActionInput.Develop) command.input();
        ensureTurnAcceptsAction(state);
        String cardId = input.cardId();
        List<String> industryTypes = input.industryTypes().stream()
                .filter(value -> !value.isBlank())
                .toList();
        if (cardId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8bf7\u9009\u62e9\u8981\u5f03\u7f6e\u7684\u624b\u724c");
        }
        if (findCard(currentHand(state, actor.id()), cardId) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u6240\u9009\u624b\u724c\u4e0d\u5728\u4f60\u7684\u624b\u724c\u4e2d");
        }
        var plan = BrassTypedDevelop.plan(industryTypes, INDUSTRY_TYPES);
        industryTypes = plan.industryTypes();
        List<ResourceRequirement> requirements = plan.requirements();
        requireResourceSelectionContract(state, payload, requirements, mode);
        int moneyBeforeDevelop = playerMoney(state, actor.id());
        discardCard(state, actor.id(), cardId);
        List<String> notices = new ArrayList<>();
        List<String> developedItems = new ArrayList<>();
        if (hasExplicitResourceSelections(payload)) {
            notices.addAll(consumeExplicitResources(state, actor.id(), requirements, payload, ""));
        }
        for (int index = 0; index < industryTypes.size(); index++) {
            String industryType = industryTypes.get(index);
            Map<String, Object> removedTile = lowestDevelopableBoardTileOrThrow(state, actor.id(), industryType);
            if (removedTile.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, industryNameCn(industryType) + "\u6ca1\u6709\u53ef\u7814\u53d1\u7684\u677f\u5757");
            }
            developedItems.add(removedTile.get("level") + "级" + industryNameCn(industryType));
            if (usesSimulationAutoPlanning(mode, payload)) {
                notices.add(consumeIron(state, actor.id(), ""));
            }
            removeBoardTile(state, actor.id(), industryType, Objects.toString(removedTile.get("id"), ""));
            incrementDevelopment(state, actor.id(), industryType);
        }
        String developedNames = developedItems.stream().reduce((left, right) -> left + " 和 " + right).orElse("");
        int developCost = Math.max(0, moneyBeforeDevelop - playerMoney(state, actor.id()));
        appendResourceNotices(state, notices);
        appendNotice(state, "玩家" + actor.username() + " 研发了 " + developedNames + "，花费 " + developCost + " 英镑");
        consumeAction(state, actor);
        return state;
    }
}
