import React from "react";
import { useRpsSelection } from "./useRpsSelection.js";
import { TEXT } from "../../features/rooms/roomText.js";
import { SeatBoard, NoticePanel } from "../../features/rooms/RoomPanels.jsx";
import {
  MOVE_LABELS,
  relationLines,
  visibleRpsGroups,
  rpsPlayerLabel,
  moveDisplayFor,
  rpsStatusText
} from "./helpers.jsx";

function RpsRoom({ room, seats, currentSeat, owner, onSubmitMove, selectedMove, setSelectedMove }) {
  const gameState = room.gameState ?? {};
  const submissions = gameState.submissions ?? {};
  const lastRoundSubmissions = gameState.lastRoundSubmissions ?? {};
  const wins = gameState.wins ?? {};
  const currentUserId = String(room.currentUser?.id ?? "");
  const activePlayerIds = (gameState.activePlayers ?? []).map((id) => String(id));
  const candidatePlayerIds = (gameState.candidatePlayers ?? []).map((id) => String(id));
  const active = activePlayerIds.includes(currentUserId);
  const candidate = candidatePlayerIds.includes(currentUserId);
  const submitted = Object.prototype.hasOwnProperty.call(submissions, currentUserId);
  const moves = Number(room.config?.moveSet) === 5
    ? ["stone", "scissors", "paper", "lizard", "spock"]
    : ["stone", "scissors", "paper"];
  const visibleGroups = visibleRpsGroups(gameState.groups ?? [], currentUserId, room.config?.mode);
  return (
    <div className="game-room-grid">
      <section className="panel relation-panel">
        <h2>{TEXT.relationTitle}</h2>
        <ul className="relation-list">
          {relationLines(Number(room.config?.moveSet) === 5).map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ul>
      </section>

      <section className="panel game-room-panel">
        <div className="game-room-heading">
          <h2>{TEXT.battleArea}</h2>
          <span>{TEXT.round.replace("{round}", String(gameState.round ?? 1))}</span>
        </div>

        {gameState.endProposalText ? (
          <div className="proposal-banner">{gameState.endProposalText}</div>
        ) : null}

        <div className="battle-groups">
          {visibleGroups.map((group) => (
            <div className="battle-group" key={group.groupId}>
              <div className="battle-group-title">
                <strong>{room.config?.mode === "bracket" ? `${group.groupId}\u7ec4` : TEXT.freeForAll}</strong>
                {group.resultText ? <span>{group.resultText}</span> : null}
              </div>
              <div className="battle-player-list">
                {(group.players ?? []).map((player) => (
                  <div className="battle-player" key={String(player.userId)}>
                    <span>{rpsPlayerLabel(player)}</span>
                    {room.config?.mode === "free_for_all" && !player.bot ? (
                      <small>{TEXT.wins}: {(gameState.scores ?? {})[String(player.userId)] ?? 0}</small>
                    ) : null}
                    <strong>{moveDisplayFor(player, submissions, lastRoundSubmissions, group)}</strong>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="panel action-panel">
        <div className="game-room-heading">
          <h2>{TEXT.actionArea}</h2>
          <span>{rpsStatusText({ currentSeat, active, candidate, submitted })}</span>
        </div>

        <div className="move-grid">
          {moves.map((move) => (
            <button
              key={move}
              className={selectedMove === move || submitted && submissions[currentUserId] === move ? "active" : ""}
              disabled={!currentSeat || !active || submitted || gameState.phase === "finished"}
              onClick={() => setSelectedMove(move)}
            >
              {MOVE_LABELS[move]}
            </button>
          ))}
        </div>

        <button
          className="primary-button"
          disabled={!currentSeat || !active || submitted || !selectedMove || gameState.phase === "finished"}
          onClick={() => onSubmitMove(selectedMove)}
        >
          {TEXT.confirmMove}
        </button>
      </section>

      <section className="panel room-seat-panel compact">
        <SeatBoard owner={owner} waiting={false} seats={seats} showKick={false} />
      </section>

      <NoticePanel notices={room.notices ?? []} />
    </div>
  );
}

export { RpsRoom, useRpsSelection };

export default RpsRoom;
