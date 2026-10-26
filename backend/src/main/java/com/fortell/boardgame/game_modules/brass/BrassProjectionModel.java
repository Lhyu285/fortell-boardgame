package com.fortell.boardgame.game_modules.brass;

import java.util.List;

/** Presentation plans contain counts and access decisions, never private card/save payloads. */
final class BrassProjectionModel {
    private BrassProjectionModel() {}
    enum PrivateField {
        TURN_SNAPSHOT("turnStartSnapshot"), SIMULATION_MARKER("_candidateSimulation");
        final String key;
        PrivateField(String key) { this.key = key; }
    }
    enum PublicActionField {
        PLAYER("currentPlayerId"), MAINTENANCE("maintenanceReady"), HAND_SIZE("handSize"),
        SELLING("sellInProgress"), SELL_COUNT("sellCount"), DEBT("incomeDebt");
        final String key;
        PublicActionField(String key) { this.key = key; }
    }
    record HandCount(String playerId, int count) {}
    record HandAccess(String playerId, int count, boolean visible) {}
    record ViewerPlan(List<HandAccess> hands, int deckCount, boolean fullActions) {
        ViewerPlan { hands = List.copyOf(hands); }
    }
    record LinkScore(long owner, String type, int score) {}
}
