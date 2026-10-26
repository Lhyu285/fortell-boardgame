import React from "react";
import { TEXT } from "../../features/rooms/roomText.js";

function ThingsSettings({ config, onChange, editable }) {
  const customRules = config.customRules ?? {};
  const difficulty = config.difficulty ?? "hard";
  const spectatorView = config.spectatorView ?? "god";
  const difficultyHints = {
    easy: "使用I级难度的规则",
    medium: "使用I级和II级难度的规则",
    hard: "使用全部三个级别难度的规则",
    custom: "主持人定义规则"
  };
  const spectatorHints = {
    god: "观战玩家可以看到本局游戏的规则",
    player: "观战玩家无法看到本局游戏的规则"
  };
  const difficultyHint = difficultyHints[difficulty] ?? difficultyHints.hard;
  const spectatorHint = spectatorHints[spectatorView] ?? spectatorHints.god;

  function updateCustomRule(field, value) {
    onChange("customRules", { ...customRules, [field]: value });
  }

  return (
    <div className="things-settings">
      <strong>{TEXT.thingsSettings}</strong>
      <div className="room-settings-options">
        <label>
          {TEXT.difficulty}
          <select disabled={!editable} value={difficulty} onChange={(event) => onChange("difficulty", event.target.value)}>
            <option value="easy">{TEXT.easy}</option>
            <option value="medium">{TEXT.medium}</option>
            <option value="hard">{TEXT.hard}</option>
            <option value="custom">{TEXT.custom}</option>
          </select>
          <small className="setting-hint">{difficultyHint}</small>
        </label>
        <label>
          {TEXT.spectatorView}
          <select disabled={!editable} value={spectatorView} onChange={(event) => onChange("spectatorView", event.target.value)}>
            <option value="god">{TEXT.godView}</option>
            <option value="player">{TEXT.playerView}</option>
          </select>
          <small className="setting-hint">{spectatorHint}</small>
        </label>
        {difficulty === "custom" ? (
          <div className="things-custom-rules">
            <input disabled={!editable} value={customRules.SCENE ?? ""} onChange={(event) => updateCustomRule("SCENE", event.target.value)} placeholder={TEXT.sceneRule} />
            <input disabled={!editable} value={customRules.WORD ?? ""} onChange={(event) => updateCustomRule("WORD", event.target.value)} placeholder={TEXT.wordRule} />
            <input disabled={!editable} value={customRules.ATTRIBUTE ?? ""} onChange={(event) => updateCustomRule("ATTRIBUTE", event.target.value)} placeholder={TEXT.attributeRule} />
          </div>
        ) : null}
      </div>
    </div>
  );
}

export { ThingsSettings };
export default ThingsSettings;
