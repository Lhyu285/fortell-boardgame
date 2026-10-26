package com.fortell.boardgame.game_modules.brass;

/** Typed audit metadata. Opaque command JSON and save deltas are encoded only at the event boundary. */
record BrassEventModel(BrassActionType type, long actorId, String actorName, String summary,
                       BrassGameState before, BrassGameState after, long actionStartVersion) {
    static final int SCHEMA_VERSION = 1;
    long afterVersion() { return before.version() + 1; }
    Integer actionIndex() { return type.businessAction ? before.turn().actionsTaken() + 1 : null; }
    String businessActionId() {
        return type.businessAction ? before.turnKey() + "/" + actionIndex() + "/" + actionStartVersion : null;
    }
    boolean actionCompleted() { return type.businessAction && type != BrassActionType.SELL; }
    String kind() { return type.businessAction ? "business_action" : "flow_operation"; }
    String era() { return before.era().name().toLowerCase(java.util.Locale.ROOT); }
    String message() { return "玩家" + actorName + " " + summary; }
}
