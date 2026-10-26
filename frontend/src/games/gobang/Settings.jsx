import React from "react";
import { TEXT } from "../../features/rooms/roomText.js";

function GobangSettings({ config, onChange, editable }) {
  return (
    <div className="gobang-settings">
      <strong>{TEXT.gobangSettings}</strong>
      <div className="room-settings-options">
        <label>
          {TEXT.gobangRuleMode}
          <select disabled={!editable} value={config.ruleMode ?? "normal"} onChange={(event) => onChange("ruleMode", event.target.value)}>
            <option value="normal">{TEXT.gobangNormal}</option>
            <option value="professional">{TEXT.gobangProfessional}</option>
          </select>
        </label>
        <label>
          {TEXT.gobangFirstHand}
          <select disabled={!editable} value={config.firstHand ?? "random"} onChange={(event) => onChange("firstHand", event.target.value)}>
            <option value="random">{TEXT.gobangRandom}</option>
            <option value="host_first">{TEXT.gobangHostFirst}</option>
            <option value="host_second">{TEXT.gobangHostSecond}</option>
          </select>
        </label>
      </div>
    </div>
  );
}

export { GobangSettings };
export default GobangSettings;
