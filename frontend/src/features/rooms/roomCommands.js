import { TEXT } from "./roomText.js";
import { deriveRoomModel } from "./roomModel.js";

export function createClientActionId() {
  if (window.crypto?.randomUUID) {
    return window.crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

/** Stable commands read the current room snapshot at invocation, not a previous render. */
export function createRoomCommands(session, { gameType, protocol = {},
  confirm = message => window.confirm(message), newActionId = createClientActionId }) {
  let pendingGeneration = null;
  function submitAction(type, payload = {}) {
    const token = session.token();
    if (token === null || protocol.blockWhilePending && pendingGeneration === token) return;
    const body = { type, payload };
    if (protocol.versioned) {
      body.stateVersion = Number(session.getSnapshot().room?.gameState?.version ?? 0);
      body.clientActionId = newActionId();
    }
    if (protocol.blockWhilePending) {
      pendingGeneration = token;
      session.setActionPending(true, token);
    }
    return session.post("/actions", body).finally(() => {
      if (protocol.blockWhilePending) {
        if (pendingGeneration === token) pendingGeneration = null;
        session.setActionPending(false, token);
      }
    });
  }
  function updateGameConfig(field, value) {
    const room = session.getSnapshot().room;
    return session.post("/config", { config: { ...(room?.config ?? {}), [field]: value } });
  }
  function changeSeatCount(delta) {
    const room = session.getSnapshot().room;
    const { seatCount, occupiedCount, seatBounds } = deriveRoomModel(room, gameType);
    const nextValue = seatCount + delta;
    if (nextValue < occupiedCount) { session.setError(TEXT.seatCountTooSmall); return; }
    if (nextValue < seatBounds.min || nextValue > seatBounds.max) {
      session.setError(TEXT.seatCountOutOfRange.replace("{min}", String(seatBounds.min)).replace("{max}", String(seatBounds.max)));
      return;
    }
    return updateGameConfig("seatCount", nextValue);
  }
  return {
    submitAction,
    submitMove: move => submitAction("submit_move", { move }),
    moveSeat: seatIndex => session.post("/seat", { seatIndex }),
    standUp: () => session.post("/stand", {}),
    forceStandUp: seatIndex => session.post(`/seats/${seatIndex}/stand`, {}),
    changeSeatCount,
    updateGameConfig,
    startGame: () => confirm("Start game?") ? session.post("/start", {}) : undefined,
    proposeEndGame: () => session.post("/propose-end", {}),
    endGame: () => confirm(TEXT.confirmEndGame) ? session.post("/end", {}) : undefined,
    leaveRoom: () => session.post("/leave", {}, true),
    dismissRoom: () => session.dismiss()
  };
}
