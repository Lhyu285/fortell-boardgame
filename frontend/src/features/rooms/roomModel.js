import { isSeatOccupied } from "./roomView.js";
import { gameSeatBounds } from "../../games/registry.js";

export function deriveRoomModel(room, gameType) {
  const seats = seatListOf(room);
  const currentSeat = seats.find(seat => seat.currentUserSeat) ?? null;
  const occupiedCount = seats.filter(isSeatOccupied).length;
  const seatCount = room?.roomState?.seatCount ?? room?.seatCount ?? seats.length;
  const allSeatsOccupied = seats.length > 0 && occupiedCount === seatCount && seats.every(isSeatOccupied);
  const owner = Boolean(room?.currentUser?.id === room?.owner?.id);
  const waiting = room?.status === "WAITING";
  const inProgress = room?.status === "IN_PROGRESS";
  const seatBounds = gameSeatBounds(gameType);
  const minSeatCount = Math.max(seatBounds.min, occupiedCount);
  return {
    seats, currentSeat, occupiedCount, seatCount, allSeatsOccupied, owner, waiting, inProgress, seatBounds,
    canStart: owner && waiting && allSeatsOccupied,
    canLeaveRoom: room?.permissions?.canLeaveRoom === true,
    gameFinished: isFinishedGameState(room?.gameState),
    canAddSeat: owner && waiting && seatCount < seatBounds.max,
    canRemoveSeat: owner && waiting && seatCount > minSeatCount
  };
}

export function roomCurrentPlayerName(room, seats = []) {
  const state = room?.gameState ?? {};
  const currentPlayerId = state.currentPlayerId;
  if (currentPlayerId === undefined || currentPlayerId === null || currentPlayerId === "") return "";
  const players = Array.isArray(state.players) ? state.players : [];
  const player = players.find((item) => String(item.userId ?? item.id) === String(currentPlayerId));
  if (player?.username) return player.username;
  const seat = seats.find((item) => String(item.userId ?? item.playerId) === String(currentPlayerId));
  return seat?.username ?? seat?.playerName ?? "";
}

export function seatListOf(room) {
  return room?.roomState?.seats ?? room?.seats ?? [];
}

export function hasCurrentUserProposed(room) {
  const currentUserId = room?.currentUser?.id;
  const proposals = room?.gameState?.endProposals ?? [];
  return proposals.some((proposal) => String(proposal.userId) === String(currentUserId));
}

export function isFinishedGameState(state) {
  const phase = String(state?.phase ?? "").toLowerCase();
  return phase === "finished" || phase === "game_over";
}
