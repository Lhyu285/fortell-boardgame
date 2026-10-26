import React from "react";

const THINGS_AREAS = [
  { key: "SCENE", label: "\u60c5\u5883", short: "\u7ea2", parts: ["scene"] },
  { key: "WORD", label: "\u8bcd\u6c47", short: "\u9ec4", parts: ["word"] },
  { key: "ATTRIBUTE", label: "\u5c5e\u6027", short: "\u84dd", parts: ["attribute"] },
  { key: "SCENE_WORD", label: "\u60c5\u5883+\u8bcd\u6c47", short: "\u7ea2\u9ec4", parts: ["scene", "word"] },
  { key: "SCENE_ATTRIBUTE", label: "\u60c5\u5883+\u5c5e\u6027", short: "\u7ea2\u84dd", parts: ["scene", "attribute"] },
  { key: "WORD_ATTRIBUTE", label: "\u8bcd\u6c47+\u5c5e\u6027", short: "\u9ec4\u84dd", parts: ["word", "attribute"] },
  { key: "SCENE_WORD_ATTRIBUTE", label: "\u60c5\u5883+\u8bcd\u6c47+\u5c5e\u6027", short: "\u7ea2\u9ec4\u84dd", parts: ["scene", "word", "attribute"] },
  { key: "NONE", label: "\u65e0\u5173", short: "\u65e0\u5173", parts: ["none"] }
];

function coloredAreaLabel(area) {
  if (area.key === "NONE") {
    return <span>{area.label}</span>;
  }
  const labels = { scene: "情境", word: "词汇", attribute: "属性" };
  return area.parts.map((part, index) => (
    <span key={`${area.key}-${part}`}>
      {index > 0 ? "+" : ""}
      <span className={`area-${part}`}>{labels[part]}</span>
    </span>
  ));
}

function coloredAreaShortLabel(area) {
  if (area.key === "NONE") {
    return <span>{area.short}</span>;
  }
  const classByChar = { "R": "area-scene", "Y": "area-word", "B": "area-attribute" };
  return Array.from(area.short).map((char, index) => (
    <span key={`${area.key}-${char}-${index}`} className={classByChar[char] ?? ""}>
      {char}
    </span>
  ));
}

function thingsPhaseText(phase) {
  return {
    SELECTING_HOST: "选择主持人",
    HOST_INITIAL_PLACEMENT: "主持人布置",
    HOST_TURN: "主持人回合",
    GUESSER_TURN: "竞猜者回合",
    GAME_OVER: "游戏结束"
  }[phase] ?? "准备中";
}

function thingsPhaseIcon(phase) {
  return {
    SELECTING_HOST: "♛",
    HOST_INITIAL_PLACEMENT: "✦",
    HOST_TURN: "⌂",
    GUESSER_TURN: "?",
    GAME_OVER: "✓"
  }[phase] ?? "○";
}

function thingsPhaseHint(phase, currentPlayerId, players) {
  const currentPlayer = players.find((player) => String(player.userId) === String(currentPlayerId));
  if (phase === "SELECTING_HOST") return "房主正在选择本局主持人。";
  if (phase === "HOST_INITIAL_PLACEMENT") return "主持人正在为三个环放置提示词。";
  if (phase === "HOST_TURN") return "主持人可以放置提示词，或使用一次提示。";
  if (phase === "GUESSER_TURN") return currentPlayer ? `轮到${currentPlayer.username}从手牌中选择词语。` : "竞猜者请选择一张词语卡。";
  if (phase === "GAME_OVER") return "所有词语已经归类，查看本局推理结果。";
  return "等待游戏流程开始。";
}

function wordText(word) {
  if (!word) return "";
  const chinese = String(word.Chinese ?? "").trim();
  const english = String(word.English ?? "").trim();
  const note = String(word.note ?? "").trim();
  const base = english ? `${chinese}/${english}` : chinese;
  return note ? `${base}[${note}]` : base;
}

function ruleText(rule) {
  if (!rule) return "";
  const description = String(rule.description ?? rule.describe ?? "").trim();
  const note = String(rule.note ?? "").trim();
  return note ? `${description}[${note}]` : description;
}

function areaShort(areaKey) {
  return THINGS_AREAS.find((area) => area.key === areaKey)?.short ?? areaKey;
}

export { THINGS_AREAS, coloredAreaLabel, coloredAreaShortLabel, thingsPhaseText, thingsPhaseIcon, thingsPhaseHint, wordText, ruleText, areaShort };
