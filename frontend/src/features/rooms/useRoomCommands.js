import { useMemo } from "react";
import { getGameDefinition } from "../../games/registry.js";
import { createRoomCommands } from "./roomCommands.js";

export function useRoomCommands(session, gameType) {
  return useMemo(() => createRoomCommands(session, { gameType, protocol: getGameDefinition(gameType)?.commands }), [session, gameType]);
}
