import { useEffect, useMemo, useState } from "react";
import { createRoomSession, initialRoomSessionState } from "./roomSession.js";

export function useRoomSession(gameType, roomId, navigate) {
  const [snapshot, setSnapshot] = useState(initialRoomSessionState);
  const session = useMemo(() => createRoomSession({ gameType, roomId, onChange: setSnapshot,
    onDismissed: () => navigate(`/${gameType}`, { replace: true }) }), [gameType, roomId, navigate]);
  useEffect(() => {
    session.start();
    return () => session.close();
  }, [session]);
  return { ...snapshot, session };
}
