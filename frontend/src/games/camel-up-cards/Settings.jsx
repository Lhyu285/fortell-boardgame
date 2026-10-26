import React from "react";
import { TEXT } from "../../features/rooms/roomText.js";
import SettingsToggle from "../../features/rooms/SettingsToggle.jsx";

function CamelSettings({ config, onChange, editable }) {
  const expansion = config.expansion ?? "shortcut_fennec";
  const shortcutEnabled = expansion === "shortcut" || expansion === "shortcut_fennec";
  const fennecEnabled = expansion === "fennec" || expansion === "shortcut_fennec";

  function updateExpansion(type, enabled) {
    const nextShortcut = type === "shortcut" ? enabled : shortcutEnabled;
    const nextFennec = type === "fennec" ? enabled : fennecEnabled;
    const nextExpansion = nextShortcut && nextFennec
      ? "shortcut_fennec"
      : nextShortcut
        ? "shortcut"
        : nextFennec
          ? "fennec"
          : "none";
    onChange("expansion", nextExpansion);
  }

  return (
    <div className="camel-settings">
      <strong>{TEXT.camelSettings}</strong>
      <div className="room-settings-options settings-toggle-options">
        <SettingsToggle
          checked={shortcutEnabled}
          editable={editable}
          label={TEXT.enableShortcutExpansion}
          description="发现赛道上的捷径，让骆驼跑的更快"
          onChange={(enabled) => updateExpansion("shortcut", enabled)}
        />
        <SettingsToggle
          checked={fennecEnabled}
          editable={editable}
          label={TEXT.enableFennecExpansion}
          description="利用耳廓狐吓退骆驼，让骆驼倒退"
          onChange={(enabled) => updateExpansion("fennec", enabled)}
        />
      </div>
    </div>
  );
}

export { CamelSettings };
export default CamelSettings;
