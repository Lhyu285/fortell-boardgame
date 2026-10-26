package com.fortell.boardgame.game_modules.brass;

import java.util.List;
import static com.fortell.boardgame.game_modules.brass.BrassProjectionModel.*;
import static com.fortell.boardgame.game_modules.brass.BrassFlowModel.ScoringIndustry;

/** Explicit disclosure policy shared by all phases, including finished and restartable saves. */
final class BrassTypedProjection {
    private BrassTypedProjection() {}
    static ViewerPlan viewer(Long viewer, long currentPlayer, List<HandCount> hands, int deckCount) {
        String ownId = viewer == null ? "" : String.valueOf(viewer);
        return new ViewerPlan(hands.stream().map(hand -> new HandAccess(hand.playerId(), hand.count(),
                viewer != null && hand.playerId().equals(ownId))).toList(), deckCount, viewer != null && viewer == currentPlayer);
    }
    static int estimate(String era, String linkType, long player, List<ScoringIndustry> industries, List<LinkScore> links) {
        int points = industries.stream().filter(tile -> tile.ownerId() == player && era.equals(tile.era()) && tile.flipped())
                .mapToInt(ScoringIndustry::victoryPoints).sum();
        return points + links.stream().filter(link -> link.owner() == player && linkType.equals(link.type())).mapToInt(LinkScore::score).sum();
    }
}
