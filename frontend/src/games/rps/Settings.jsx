import React from "react";
import { TEXT } from "../../features/rooms/roomText.js";

function RpsSettings({ config, onChange, editable }) {
  const moveSet = String(config.moveSet ?? 3);
  const mode = config.mode ?? "free_for_all";
  const moveSetHint = moveSet === "5"
    ? "石头+剪刀+布+蜥蜴+史波克"
    : "石头+剪刀+布，经典模式";
  const modeHint = mode === "bracket" ? "两人一组对抗" : "所有人同台竞技";

  return (
    <div className="rps-settings">
      <strong>{TEXT.rpsSettings}</strong>
      <div className="room-settings-options">
        <label>
          {TEXT.moveSet}
          <select disabled={!editable} value={moveSet} onChange={(event) => onChange("moveSet", Number(event.target.value))}>
            <option value="3">{TEXT.moveSetThree}</option>
            <option value="5">{TEXT.moveSetFive}</option>
          </select>
          <small className="setting-hint">{moveSetHint}</small>
        </label>
        <label>
          {TEXT.rpsMode}
          <select disabled={!editable} value={mode} onChange={(event) => onChange("mode", event.target.value)}>
            <option value="free_for_all">{TEXT.freeForAll}</option>
            <option value="bracket">{TEXT.bracket}</option>
          </select>
          <small className="setting-hint">{modeHint}</small>
        </label>
      </div>
    </div>
  );
}

export { RpsSettings };
export default RpsSettings;
