package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.ApiException;
import com.fortell.boardgame.models.UserSummary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import static com.fortell.boardgame.game_modules.brass.BrassCardService.currentHand;
import static com.fortell.boardgame.game_modules.brass.BrassCardService.discardCard;
import static com.fortell.boardgame.game_modules.brass.BrassCardService.findCard;
import static com.fortell.boardgame.game_modules.brass.BrassConnectivityService.playerLinkCount;
import static com.fortell.boardgame.game_modules.brass.BrassConnectivityService.networkDestinations;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.listOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.mapOf;
import static com.fortell.boardgame.game_modules.brass.BrassPlayerService.payMoney;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.appendNotice;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.appendResourceNotices;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.describeOperation;
import static com.fortell.boardgame.game_modules.brass.BrassResourceModel.*;
import static com.fortell.boardgame.game_modules.brass.BrassResourcePlanner.consumeCoal;
import static com.fortell.boardgame.game_modules.brass.BrassResourcePlanner.consumeNetworkBeer;
import static com.fortell.boardgame.game_modules.brass.BrassResourceSelectionService.consumeExplicitResources;
import static com.fortell.boardgame.game_modules.brass.BrassResourceSelectionService.hasExplicitResourceSelections;
import static com.fortell.boardgame.game_modules.brass.BrassResourceSelectionService.requireResourceSelectionContract;
import static com.fortell.boardgame.game_modules.brass.BrassResourceSelectionService.usesSimulationAutoPlanning;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;
import static com.fortell.boardgame.game_modules.brass.BrassTurnService.consumeAction;
import static com.fortell.boardgame.game_modules.brass.BrassTurnService.ensureTurnAcceptsAction;

/** Network Action rules; all working state is passed explicitly. */
final class BrassNetworkAction {
    private BrassNetworkAction() {}

    static Map<String, Object> networkAction(Map<String, Object> state, UserSummary actor, BrassActionCommand command, BrassExecutionMode mode) {
        Map<String, Object> payload = command.payload();
        BrassActionInput.Network input = (BrassActionInput.Network) command.input();
        ensureTurnAcceptsAction(state);
        String cardId = input.cardId();
        if (cardId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u8bf7\u9009\u62e9\u8981\u5f03\u7f6e\u7684\u624b\u724c");
        }
        if (findCard(currentHand(state, actor.id()), cardId) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\u6240\u9009\u624b\u724c\u4e0d\u5728\u4f60\u7684\u624b\u724c\u4e2d");
        }
        String linkType = "canal".equals(state.get("era")) ? "canal" : "rail";
        List<BrassTypedNetwork.RouteSpec> routeSpecs = input.routes();
        BrassTypedNetwork.cost(linkType, routeSpecs.size(), 0);
        requireResourceSelectionContract(state, payload, BrassTypedNetwork.requirements(linkType, routeSpecs, List.of()), mode);

        Map<String, Object> board = mapOf(state.get("board"));
        List<Map<String, Object>> links = listOf(board.get("links"));
        long existingLinkCount = playerLinkCount(links, actor.id());
        List<Map<String, Object>> builtLinks = new ArrayList<>();
        List<ResourceBoard> resourceBoardsAfterEachLink = new ArrayList<>();
        List<String> resourceNotices = new ArrayList<>();
        payMoney(state, actor.id(), BrassTypedNetwork.cost(linkType, routeSpecs.size(), existingLinkCount));
        var routeRules = BrassActionAdapter.routeRules();
        for (BrassTypedNetwork.RouteSpec routeSpec : routeSpecs) {
            var plannedLink = BrassTypedNetwork.step(routeSpec, linkType, actor.id(), BrassBoardAdapter.network(board),
                    BrassBoardAdapter.sites(board), routeRules);

            Map<String, Object> link = new LinkedHashMap<>();
            link.put("id", "link_" + actor.id() + "_" + System.nanoTime() + "_" + builtLinks.size());
            link.put("ownerId", actor.id());
            link.put("ownerName", actor.username());
            link.put("from", plannedLink.from());
            link.put("to", plannedLink.to());
            link.put("type", linkType);
            link.put("color", playerColor(state, actor.id()));
            links.add(link);
            builtLinks.add(link);
            board.put("links", links);
            state.put("board", board);
            resourceBoardsAfterEachLink.add(BrassResourceAdapter.board(board));
            if ("rail".equals(linkType)) {
                if (usesSimulationAutoPlanning(mode, payload)) {
                    resourceNotices.add(consumeCoal(
                            state,
                            actor.id(),
                            "",
                            List.of(Objects.toString(link.get("from"), ""), Objects.toString(link.get("to"), ""))
                    ));
                }
                board = mapOf(state.get("board"));
                links = listOf(board.get("links"));
            }
        }

        if (hasExplicitResourceSelections(payload)) {
            List<ResourceRequirement> requirements = BrassTypedNetwork.requirements(linkType, routeSpecs, resourceBoardsAfterEachLink);
            resourceNotices.addAll(consumeExplicitResources(state, actor.id(), requirements, payload, ""));
        } else if (usesSimulationAutoPlanning(mode, payload) && "rail".equals(linkType) && builtLinks.size() == 2) {
            board = mapOf(state.get("board"));
            resourceNotices.add(consumeNetworkBeer(
                    state,
                    listOf(board.get("industries")),
                    actor.id(),
                    "",
                    networkDestinations(builtLinks),
                    1
            ));
        }
        discardCard(state, actor.id(), cardId);
        board = mapOf(state.get("board"));
        state.put("board", board);

        String routeText = builtLinks.stream()
                .map(link -> cityCnName(Objects.toString(link.get("from"), "")) + " - " + cityCnName(Objects.toString(link.get("to"), "")))
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
        describeOperation(state, actor, "network", "修建" + ("rail".equals(linkType) ? "铁路" : "运河") + " " + routeText);
        appendResourceNotices(state, resourceNotices);
        for (Map<String, Object> link : builtLinks) {
        appendNotice(state, "玩家" + actor.username() + " 建造了连接 "
                    + cityCnName(Objects.toString(link.get("from"), "")) + " 和 "
                    + cityCnName(Objects.toString(link.get("to"), "")) + " 的"
                    + ("rail".equals(linkType) ? "铁路" : "运河"));
        }
        consumeAction(state, actor);
        return state;
    }
}
