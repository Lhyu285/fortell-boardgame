import React, { Suspense } from "react";
import { getGameDefinition } from "../../games/registry.js";
import { useRpsSelection } from "../../games/rps/useRpsSelection.js";
import { TEXT } from "./roomText.js";

export default function GameDesktop(props) {
  // Preserve the old unconditional selection-hook lifecycle, without loading the RPS desktop.
  const selection = useRpsSelection(props.room.gameState ?? {});
  const game = getGameDefinition(props.gameType);
  const Room = game?.Room;
  if (!Room) return <section className="panel game-room-panel"><p className="muted-note">{TEXT.reserved}</p></section>;
  return (
    <Suspense fallback={<section className="panel game-room-panel"><p className="muted-note">{TEXT.loading}</p></section>}>
      <Room {...game.desktopProps({ ...props, selection })} />
    </Suspense>
  );
}
