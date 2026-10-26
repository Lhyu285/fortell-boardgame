package com.fortell.boardgame.game_modules.brass;

/** Immutable execution/audit flow view. Legacy decoding belongs to the flow boundary. */
record BrassGameState(Era era, Phase phase, int round, int playerCount, long currentPlayerId, long version, BrassTurnState turn) {
    enum Era { CANAL, RAIL }
    enum Phase { WAITING, ACTIONS, SELLING, AWAITING_END_TURN, INCOME_DEBT, ERA_MAINTENANCE, FINISHED }
    int roundsPerEra() { return BrassTypedTurns.roundsPerEra(playerCount); }
    boolean finalGameRound() { return era == Era.RAIL && round >= roundsPerEra(); }
    int actionsPerTurn() { return BrassTypedTurns.actions(era == Era.CANAL, round); }
    String turnKey() { return era.name().toLowerCase(java.util.Locale.ROOT) + "/" + round + "/" + currentPlayerId; }
}
