import React from "react";
import { getGameDefinition } from "../../games/registry.js";

export default function GameSettings({ gameType, config, editable, onChange }) {
  const Settings = getGameDefinition(gameType)?.Settings;
  return Settings ? <Settings config={config} editable={editable} onChange={onChange} /> : null;
}
