package com.fortell.boardgame.game_modules.brass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.listOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.mapOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.parseInteger;
import static com.fortell.boardgame.game_modules.brass.BrassRecordSupport.appendNotice;
import static com.fortell.boardgame.game_modules.brass.BrassRuleData.*;

/** Applies typed score awards to exact JSON instances and formats legacy notices/history. */
final class BrassScoringService {
    private BrassScoringService() {}

    static void scoreCurrentEra(Map<String, Object> state) {
        String era = Objects.toString(state.get("era"), "");
        Map<String, Object> board = mapOf(state.get("board"));
        List<Map<String, Object>> links = listOf(board.get("links"));
        List<Map<String, Object>> industries = listOf(board.get("industries"));
        Map<String, Object> statsByPlayer = mapOf(state.get("playerStats"));
        List<Map<String, Object>> eraScores = listOf(state.get("eraScores"));
        List<Map<String, Object>> currentEraScores = new ArrayList<>();
        var plan = BrassTypedScoring.score(BrassFlowAdapter.industries(industries), BrassFlowAdapter.links(links),
                BrassFlowAdapter.markets(), eraLinkType(era));
        for (var award : plan.awards()) {
            boolean linkAward = award.kind() == BrassFlowModel.AwardKind.LINK;
            var instance = (linkAward ? links : industries).get(award.index());
            var stats = mapOf(statsByPlayer.get(String.valueOf(award.ownerId())));
            stats.put("victoryPoints", BrassTypedScoring.add(parseInteger(stats.get("victoryPoints"), 0), award.points()));
            statsByPlayer.put(String.valueOf(award.ownerId()), stats);
            if (linkAward) { instance.put("scored", true); instance.put("score", award.points()); }
            else instance.put("industryVpScored", true);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("era", era); item.put("ownerId", award.ownerId()); item.put("ownerName", instance.get("ownerName"));
            item.put("type", linkAward ? "link" : "industry");
            item.put("summary", linkAward ? instance.get("from") + " - " + instance.get("to")
                    : cityCnName(Objects.toString(instance.get("city"), "")) + " " + industryNameCn(Objects.toString(instance.get("industryType"), "")));
            item.put("points", award.points());
            currentEraScores.add(item);
        }
        eraScores.addAll(0, currentEraScores);
        board.put("links", links); board.put("industries", industries);
        state.put("board", board); state.put("playerStats", statsByPlayer);
        state.put("eraScores", eraScores.stream().limit(80).toList());
        appendNotice(state, eraName(era) + "计分完成");
    }

    static int routeVictoryPoints(List<Map<String, Object>> industries, String from, String to) {
        return BrassTypedScoring.route(BrassFlowAdapter.industries(industries), BrassFlowAdapter.markets(), from, to);
    }

    static int endpointRoadPoints(List<Map<String, Object>> industries, String city) {
        return BrassTypedScoring.endpoint(BrassFlowAdapter.industries(industries), BrassFlowAdapter.markets(), city);
    }

    static int marketRoadPoints(String city) {
        return BrassTypedScoring.marketRoadPoints(BrassFlowAdapter.markets(), city);
    }

    static List<Map<String, Object>> winnersOf(Map<String, Object> state) {
        List<Map<String, Object>> players = listOf(state.get("players"));
        var points = BrassFlowAdapter.playerScores(state, players);
        List<Map<String, Object>> winners = new ArrayList<>();
        for (int index : BrassTypedScoring.winnerIndices(points)) {
            var player = players.get(index);
            Map<String, Object> winner = new LinkedHashMap<>();
            winner.put("userId", player.get("userId")); winner.put("username", player.get("username"));
            winner.put("victoryPoints", points.get(index));
            winners.add(winner);
        }
        return winners;
    }

    static String winnersText(List<Map<String, Object>> winners) {
        return winners.stream()
                .map(winner -> Objects.toString(winner.get("username"), ""))
                .reduce((left, right) -> left + "\u3001" + right)
                .orElse("");
    }
}
