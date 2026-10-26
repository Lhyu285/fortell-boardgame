package com.fortell.boardgame.game_modules.brass;

/** Sale sub-submissions retain one business ID across saves; flow operations have no action index. */
final class BrassTypedEvents {
    private BrassTypedEvents() {}
    static long startVersion(BrassGameState source, BrassActionType type) {
        long start = (type == BrassActionType.SELL || type == BrassActionType.END_SELL) && source.turn().sellInProgress()
                ? source.turn().sellActionStartVersion() : source.version() + 1;
        return start <= 0 ? source.version() + 1 : start;
    }
    static boolean startsTurn(BrassGameState source, BrassGameState target) {
        return !source.turnKey().equals(target.turnKey()) && target.phase() == BrassGameState.Phase.ACTIONS;
    }
}
