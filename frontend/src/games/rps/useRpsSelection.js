import { useEffect, useState } from "react";

// Kept at the dispatcher lifecycle during this migration; each mounted room owns its hooks.
function useRpsSelection(gameState) {
  const [selectedMove, setSelectedMove] = useState("");
  useEffect(() => {
    setSelectedMove("");
  }, [gameState.round]);
  return { selectedMove, setSelectedMove };
}
export { useRpsSelection };
