package com.fortell.boardgame.game_modules.brass;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import static com.fortell.boardgame.game_modules.brass.BrassActionQueryService.availableActions;
import static com.fortell.boardgame.game_modules.brass.BrassConnectivityService.routeKey;
import static com.fortell.boardgame.game_modules.brass.BrassIndustryService.remainingIndustryResource;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.asLong;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.listOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.mapOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.parseInteger;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.eraLinkType;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.industryNameCn;
import static com.fortell.boardgame.game_modules.brass.BrassScoringService.routeVictoryPoints;
import static com.fortell.boardgame.game_modules.brass.BrassTurnService.canMaintainEra;

/** State Projection rules; all working state is passed explicitly. */
final class BrassStateProjection {
    private BrassStateProjection() {}

    static void updateMaintenanceFlag(Map<String, Object> state) {
        refreshBrassDerivedState(state);
        boolean canMaintain = canMaintainEra(state);
        state.put("canMaintainEra", canMaintain);
        state.put("eraEnding", canMaintain && "playing".equals(state.get("phase")));
        state.put("availableActions", availableActions(state));
    }

    static void refreshBrassDerivedState(Map<String, Object> state) {
        Map<String, Object> board = mapOf(state.get("board"));
        if (board.isEmpty()) {
            return;
        }
        List<Map<String, Object>> industries = listOf(board.get("industries"));
        List<Map<String, Object>> links = listOf(board.get("links"));
        refreshIndustryDisplayFields(industries);
        for (Map<String, Object> link : links) {
            int score = routeVictoryPoints(
                    industries,
                    Objects.toString(link.get("from"), ""),
                    Objects.toString(link.get("to"), "")
            );
            link.put("builtLinkId", link.get("id"));
            link.put("linkId", routeKey(Objects.toString(link.get("from"), ""), Objects.toString(link.get("to"), "")));
            link.put("ownerPlayerId", link.get("ownerId"));
            link.put("src", link.get("from"));
            link.put("dst", link.get("to"));
            link.put("eraBuilt", link.get("type"));
            link.put("currentScore", score);
            link.put("score", score);
        }
        board.put("industries", industries);
        board.put("links", links);
        state.put("board", board);
        refreshEstimatedEraEndScores(state, industries, links);
    }

    static void refreshIndustryDisplayFields(List<Map<String, Object>> industries) {
        for (Map<String, Object> tile : industries) {
            String industryType = Objects.toString(tile.get("industryType"), "");
            tile.put("builtIndustryId", tile.get("id"));
            tile.put("ownerPlayerId", tile.get("ownerId"));
            tile.put("cityId", tile.get("city"));
            tile.put("industryId", industryType);
            tile.put("industryName", industryNameCn(industryType));
            tile.put("vp", parseInteger(tile.get("victoryPoints"), 0));
            tile.put("roadPoint", parseInteger(tile.get("roadPoints"), 0));
            tile.put("saleWine", parseInteger(tile.get("saleBeerCost"), 0));
            tile.put("remainingResource", remainingIndustryResource(tile));
        }
    }

    static void refreshEstimatedEraEndScores(Map<String, Object> state, List<Map<String, Object>> industries,
                                              List<Map<String, Object>> links) {
        String era = Objects.toString(state.get("era"), "");
        var scoringIndustries = BrassFlowAdapter.industries(industries);
        var scoringLinks = links.stream().map(link -> new BrassProjectionModel.LinkScore(asLong(link.get("ownerId")),
                Objects.toString(link.get("type"), ""), parseInteger(link.get("currentScore"), 0))).toList();
        Map<String, Object> statsByPlayer = mapOf(state.get("playerStats"));
        for (Map<String, Object> player : listOf(state.get("players"))) {
            long playerId = asLong(player.get("userId"));
            int score = BrassTypedProjection.estimate(era, eraLinkType(era), playerId, scoringIndustries, scoringLinks);
            Map<String, Object> stats = mapOf(statsByPlayer.get(String.valueOf(playerId)));
            if (!stats.isEmpty()) {
                stats.put("estimatedEraEndScore", score);
                statsByPlayer.put(String.valueOf(playerId), stats);
            }
        }
        state.put("playerStats", statsByPlayer);
    }
}
