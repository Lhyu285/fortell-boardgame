import React from "react";
import { TEXT } from "../../features/rooms/roomText.js";
import SettingsToggle from "../../features/rooms/SettingsToggle.jsx";

function IncanGoldSettings({ config, onChange, editable }) {
  return (
    <div className="incan-settings">
      <strong>{TEXT.incanSettings}</strong>
      <div className="incan-settings-options">
        <SettingsToggle
          checked={config.thievesExpansion === true}
          editable={editable}
          label={TEXT.thievesExpansion}
          description="允许玩家在决策阶段进行盗窃"
          onChange={(enabled) => onChange("thievesExpansion", enabled)}
        />
        <SettingsToggle
          checked={config.eventsExpansion === true}
          editable={editable}
          label={TEXT.eventsExpansion}
          description="每轮公开一张不重复的事件牌，为当轮带来一些独特的变化"
          onChange={(enabled) => onChange("eventsExpansion", enabled)}
        />
      </div>
    </div>
  );
}

export { IncanGoldSettings };
export default IncanGoldSettings;
