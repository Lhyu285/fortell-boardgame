package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.models.UserSummary;
import java.util.*;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;

/** The only candidate-to-handler bridge. No dispatch, derived refresh, audit or persistence. */
final class BrassSimulationProbe implements BrassQueryModel.Probe {
    private final Map<String, Object> source;
    private final long actorId;
    BrassSimulationProbe(Map<String, Object> source, long id) {
        this.source = source;
        actorId = id;
    }
    @Override public boolean build(BrassQueryModel.Build candidate) {
        return accepts(BrassActionType.BUILD, Map.of("cardId", candidate.cardIds().getFirst(), "city", candidate.city(),
                "industryType", candidate.industryType(), "slotIndex", candidate.slotIndex()));
    }
    @Override public boolean sell(int tileIndex, int merchantIndex) {
        var tile = listOf(viewMap(source.get("board")).get("industries")).get(tileIndex);
        var merchant = listOf(viewMap(source.get("market")).get("beerMerchants")).get(merchantIndex);
        var payload = new LinkedHashMap<String, Object>();
        payload.put("tileId", Objects.toString(tile.get("id"), ""));
        payload.put("merchantId", Objects.toString(merchant.get("id"), ""));
        if (!Boolean.TRUE.equals(viewMap(source.get("turn")).get("sellInProgress"))) payload.put("cardId", firstCard());
        return accepts(BrassActionType.SELL, payload);
    }
    @Override public boolean network(List<BrassTypedNetwork.RouteSpec> routes) {
        if (BrassCardService.currentHand(source, actorId).isEmpty()) return false;
        return accepts(BrassActionType.NETWORK, Map.of("cardId", firstCard(), "routes", routes.stream()
                .map(route -> Map.of("from", route.from(), "to", route.to())).toList()));
    }
    private String firstCard() {
        return Objects.toString(BrassCardService.currentHand(source, actorId).getFirst().get("id"), "");
    }
    private boolean accepts(BrassActionType type, Map<String, Object> payload) {
        var copy = BrassStateDocument.simulationCopy(source);
        try {
            var actor = new UserSummary(actorId, BrassRuleData.playerName(listOf(source.get("players")), actorId));
            var command = BrassActionCommand.decode(type.key, payload);
            switch (type) {
                case BUILD -> BrassBuildAction.buildAction(copy, actor, command, BrassExecutionMode.SIMULATE);
                case SELL -> BrassSellAction.sellAction(copy, actor, command, BrassExecutionMode.SIMULATE);
                case NETWORK -> BrassNetworkAction.networkAction(copy, actor, command, BrassExecutionMode.SIMULATE);
                default -> throw new IllegalArgumentException("Unsupported candidate: " + type);
            }
            return true;
        } catch (RuntimeException invalidCandidate) {
            return false;
        }
    }
}
