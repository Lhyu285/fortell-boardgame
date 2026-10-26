import React, { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import PageShell from "../components/PageShell";
import RuleModal from "../components/RuleModal";
import { TEXT } from "../features/rooms/roomText.js";
import PrepareRoom from "../features/rooms/PrepareRoom.jsx";
import GameErrorBoundary from "../features/rooms/GameErrorBoundary.jsx";
import GameDesktop from "../features/rooms/GameDesktop.jsx";
import { useRoomSession } from "../features/rooms/useRoomSession.js";
import { useRoomCommands } from "../features/rooms/useRoomCommands.js";
import { deriveRoomModel, roomCurrentPlayerName, hasCurrentUserProposed } from "../features/rooms/roomModel.js";
import { gameName } from "../games/registry.js";

export default function RoomPage() {
  const navigate = useNavigate();
  const { gameType, roomId } = useParams();
  return <RoomContent key={JSON.stringify([gameType, roomId])} gameType={gameType} roomId={roomId} navigate={navigate} />;
}

function RoomContent({ gameType, roomId, navigate }) {
  const { room, error, connectionState, actionPending: incanActionPending, session } = useRoomSession(gameType, roomId, navigate);
  const [rulesOpen, setRulesOpen] = useState(false);
  const { seats, currentSeat, seatCount, owner, waiting, inProgress, canStart, canLeaveRoom,
    gameFinished, canAddSeat, canRemoveSeat } = deriveRoomModel(room, gameType);
  // Keep desktop prop names as compatibility aliases; every game uses the same command service.
  const { moveSeat, standUp, forceStandUp, changeSeatCount, updateGameConfig, startGame, submitMove,
    submitAction: submitThingsAction, submitAction: submitCamelAction, submitAction: submitIncanAction,
    submitAction: submitBrassAction, proposeEndGame, endGame, leaveRoom, dismissRoom } = useRoomCommands(session, gameType);

  useEffect(() => {
    const gameName = labelFor(gameType);
    const currentPlayerName = roomCurrentPlayerName(room, seats);
    document.title = currentPlayerName ? `${currentPlayerName}的回合 · ${gameName}` : gameName;
    return () => {
      document.title = "Boardgame";
    };
  }, [gameType, room, seats]);

  if (error && !room) {
    return <div className="center-page">{error}</div>;
  }

  if (!room) {
    return <div className="center-page">{TEXT.loading}</div>;
  }

  return (
    <PageShell
      className={`page-shell-room ${gameType === "brass" && inProgress ? "page-shell-brass" : ""}`.trim()}
      title={labelFor(gameType)}
      subtitle={`房间号 #${room.roomId}`}
      actions={
        <div className="button-inline">
          <button className="ghost-button" onClick={() => setRulesOpen(true)}>
            {TEXT.rules}
          </button>
          {inProgress && owner ? (
            <button className="danger-button" onClick={endGame}>
              {gameFinished ? TEXT.returnPrepare : TEXT.endGame}
            </button>
          ) : null}
          {inProgress && !gameFinished && !owner && currentSeat ? (
            <button className="secondary-button" onClick={proposeEndGame}>
              {hasCurrentUserProposed(room) ? TEXT.cancelProposeEnd : TEXT.proposeEnd}
            </button>
          ) : null}
          <button className="ghost-button" disabled={!canLeaveRoom} onClick={leaveRoom}>
            {TEXT.leave}
          </button>
          {owner ? (
            <button className="danger-button" onClick={dismissRoom}>
              {TEXT.dismiss}
            </button>
          ) : null}
        </div>
      }
    >
      {error ? <div className="form-error room-error">{error}</div> : null}
      <div className={`room-connection-status room-connection-${connectionState}`} role="status">
        <span aria-hidden="true" />
        {connectionState === "connected" ? TEXT.connectionConnected
          : connectionState === "reconnecting" ? TEXT.connectionReconnecting
            : connectionState === "error" ? TEXT.connectionError
              : TEXT.connectionConnecting}
      </div>

      {waiting ? (
        <PrepareRoom
          owner={owner}
          waiting={waiting}
          seats={seats}
          seatCount={seatCount}
          currentSeat={currentSeat}
          canAddSeat={canAddSeat}
          canRemoveSeat={canRemoveSeat}
          canStart={canStart}
          onAddSeat={() => changeSeatCount(1)}
          onRemoveSeat={() => changeSeatCount(-1)}
          onMoveSeat={moveSeat}
          onForceStandUp={forceStandUp}
          onStandUp={standUp}
          onStartGame={startGame}
          gameType={gameType}
          config={room.config ?? {}}
          onUpdateConfig={updateGameConfig}
        />
      ) : (
        <GameErrorBoundary gameType={gameType}>
          <GameRoom
            gameType={gameType}
            room={room}
            seats={seats}
            currentSeat={currentSeat}
            owner={owner}
            onSubmitMove={submitMove}
            onThingsAction={submitThingsAction}
            onCamelAction={submitCamelAction}
            onIncanAction={submitIncanAction}
            onBrassAction={submitBrassAction}
            actionPending={incanActionPending}
          />
        </GameErrorBoundary>
      )}
      <RuleModal
        gameType={gameType}
        title={`${labelFor(gameType)} Rules`}
        open={rulesOpen}
        onClose={() => setRulesOpen(false)}
      />
    </PageShell>
  );
}

function GameRoom({ gameType, room, seats, currentSeat, owner, onSubmitMove, onThingsAction, onCamelAction, onIncanAction, onBrassAction, actionPending }) {
  return <GameDesktop gameType={gameType} room={room} seats={seats} currentSeat={currentSeat} owner={owner}
    onSubmitMove={onSubmitMove} onThingsAction={onThingsAction} onCamelAction={onCamelAction}
    onIncanAction={onIncanAction} onBrassAction={onBrassAction} actionPending={actionPending} />;
}

function labelFor(gameType) {
  return gameName(gameType);
}
