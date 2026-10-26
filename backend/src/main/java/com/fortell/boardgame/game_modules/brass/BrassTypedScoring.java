package com.fortell.boardgame.game_modules.brass;

import java.util.ArrayList;
import java.util.List;
import static com.fortell.boardgame.game_modules.brass.BrassFlowModel.*;

final class BrassTypedScoring {
    private BrassTypedScoring() {}
    static int marketRoadPoints(List<MarketRoad> markets, String city) {
        return markets.stream().filter(m -> city.equals(m.city())).mapToInt(MarketRoad::points).findFirst().orElse(0);
    }
    static int endpoint(List<ScoringIndustry> industries, List<MarketRoad> markets, String city) {
        int points = marketRoadPoints(markets, city);
        for (var tile : industries) if (tile.flipped() && city.equals(tile.city())) points += Math.max(0, tile.roadPoints());
        return points;
    }
    static int route(List<ScoringIndustry> industries, List<MarketRoad> markets, String from, String to) {
        return endpoint(industries, markets, from) + endpoint(industries, markets, to);
    }
    static ScorePlan score(List<ScoringIndustry> industries, List<ScoringLink> links, List<MarketRoad> markets, String linkType) {
        var awards = new ArrayList<Award>();
        for (int i = 0; i < links.size(); i++) {
            var link = links.get(i);
            if (linkType.equals(link.type()) && !link.scored())
                awards.add(new Award(AwardKind.LINK, i, link.ownerId(), route(industries, markets, link.from(), link.to())));
        }
        for (int i = 0; i < industries.size(); i++) {
            var tile = industries.get(i);
            if (tile.flipped() && !tile.scored()) awards.add(new Award(AwardKind.INDUSTRY, i, tile.ownerId(), tile.victoryPoints()));
        }
        return new ScorePlan(awards);
    }
    static int add(int currentPoints, int awardedPoints) { return currentPoints + awardedPoints; }
    static List<Integer> winnerIndices(List<Integer> scores) {
        int highest = scores.stream().mapToInt(Integer::intValue).max().orElse(0);
        var winners = new ArrayList<Integer>();
        for (int i = 0; i < scores.size(); i++) if (scores.get(i) == highest) winners.add(i);
        return List.copyOf(winners);
    }
}
