package com.fortell.boardgame.game_modules.brass;

import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot.Era;
import java.util.List;

/** Immutable values for flow calculations; raw save attachments stay at the adapters. */
final class BrassFlowModel {
    private BrassFlowModel() {}
    record Counters(int allowed, int remaining, int taken, boolean awaitingEndTurn) {}
    enum TurnKind { ACTIONS, INCOME_DEBT, ERA_END }
    record TurnStart(TurnKind kind, Counters counters) {}
    record Round(Era era, int number, int playerCount) {}
    record Income(int money, int amount, int debt) {}
    record DebtAsset(long ownerId, int cost) {}
    record Liquidation(int proceeds, int money, int remainingDebt) {}
    enum DebtStep { COMPLETE, LIQUIDATE, LOSE_VP }
    record RoundCompletion(boolean eraEnding, int nextRound) {}
    record RailStart(long firstPlayerId, int round, int turnIndex) {}
    enum MaintenanceStep { START_RAIL, FINISH_GAME }
    record ScoringIndustry(long ownerId, String city, String type, String era, int level,
                           boolean flipped, boolean scored, int victoryPoints, int roadPoints) {}
    record ScoringLink(long ownerId, String from, String to, String type, boolean scored) {}
    record MarketRoad(String city, int points) {}
    enum AwardKind { LINK, INDUSTRY }
    record Award(AwardKind kind, int index, long ownerId, int points) {}
    record ScorePlan(List<Award> awards) {
        ScorePlan { awards = List.copyOf(awards); }
    }
}
