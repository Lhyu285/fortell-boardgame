package com.fortell.boardgame.game_modules.brass;

record BrassTurnState(int actionsAllowed, int actionsRemaining, int actionsTaken,
                      boolean awaitingEndTurn, boolean sellInProgress, int sellCount, long sellActionStartVersion) {
    BrassTurnState consumeAction() {
        var next = BrassTypedTurns.consume(new BrassFlowModel.Counters(actionsAllowed, actionsRemaining, actionsTaken, awaitingEndTurn));
        return new BrassTurnState(next.allowed(), next.remaining(), next.taken(), next.awaitingEndTurn(), sellInProgress, sellCount, sellActionStartVersion);
    }
}
