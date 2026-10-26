import React from "react";

function SettingsToggle({ checked, editable, label, description, onChange }) {
  return (
    <label className="settings-toggle-option">
      <input
        className="incan-toggle-input"
        type="checkbox"
        disabled={!editable}
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span className="incan-toggle-track" aria-hidden="true">
        <span className="incan-toggle-thumb" />
      </span>
      <span className="settings-toggle-copy">
        <strong>{label}</strong>
        {description ? <small>{description}</small> : null}
      </span>
    </label>
  );
}

export { SettingsToggle };
export default SettingsToggle;
