import React from "react";
import { TEXT } from "./roomText.js";
import { SeatBoard } from "./RoomPanels.jsx";
import GameSettings from "./GameSettings.jsx";

function PrepareRoom({
  owner,
  waiting,
  seats,
  seatCount,
  currentSeat,
  canAddSeat,
  canRemoveSeat,
  canStart,
  onAddSeat,
  onRemoveSeat,
  onMoveSeat,
  onForceStandUp,
  onStandUp,
  onStartGame,
  gameType,
  config,
  onUpdateConfig
}) {
  return (
    <section className="panel room-seat-panel">
      {waiting ? (
        <div className="seat-count-toolbar" aria-label={TEXT.seatCount}>
          <span>
            {TEXT.seatCount}: {seatCount}
          </span>
          <div className="button-inline">
            <button className="secondary-button small-button" disabled={!owner || !canRemoveSeat} onClick={onRemoveSeat}>
              {TEXT.removeSeat}
            </button>
            <button className="secondary-button small-button" disabled={!owner || !canAddSeat} onClick={onAddSeat}>
              {TEXT.addSeat}
            </button>
          </div>
        </div>
      ) : null}

      <SeatBoard
        owner={owner}
        waiting={waiting}
        seats={seats}
        showKick={owner && waiting}
        onMoveSeat={onMoveSeat}
        onForceStandUp={onForceStandUp}
      />

      {currentSeat && waiting ? (
        <button className="secondary-button stand-button" onClick={onStandUp}>
          {TEXT.standUp}
        </button>
      ) : null}

      <button className="primary-button start-button" disabled={!canStart} onClick={onStartGame}>
        {TEXT.startGame}
      </button>

      {waiting ? <GameSettings gameType={gameType} config={config} onChange={onUpdateConfig} editable={owner} /> : null}
    </section>
  );
}

export { PrepareRoom };
export default PrepareRoom;
