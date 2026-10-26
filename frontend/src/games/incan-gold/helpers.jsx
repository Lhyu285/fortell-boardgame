

function incanCardTypeText(card) {
  if (!card) return "未知发现";
  if (card.type === "treasure") return "宝藏发现";
  if (card.type === "artifact") return "神器发现";
  if (card.type === "disaster") return "灾难发现";
  return "探险发现";
}

function incanPhaseText(phase) {
  return {
    EXPLORATION: "探险阶段",
    DECISION: "决策阶段",
    THEFT_TARGETS: "盗窃阶段",
    RETURN: "返回阶段",
    DISASTER: "灾难阶段",
    FINISHED: "游戏结束"
  }[phase] ?? phase;
}

function incanPhaseHint(phase, eventId) {
  if (phase === "EXPLORATION" && eventId === "double_challenge") {
    return "双重挑战：本次探险会连续翻开2张牌，依次结算后再进入决策。";
  }

  if (phase === "EXPLORATION") return "探险中：由仍在探险的玩家推进下一格。";
  if (phase === "DECISION") return "请完成选择；所有仍在探险的玩家提交后才会统一结算。";
  if (phase === "THEFT_TARGETS") return "盗贼身份已公开，所有盗贼提交目标后同时结算。";
  if (phase === "FINISHED") return "五轮探险完成，正在展示最终结果。";
  return "当前阶段将自动完成结算。";
}

function incanDisasterText(disaster) {
  return { collapse: "塌方", trap: "陷阱", serpent: "妖蛇", idol: "傀儡", swarm: "虫群" }[disaster] ?? disaster;
}

function incanCardText(card) {
  if (!card) return "";
  if (card.type === "treasure") return `宝藏${card.value}`;
  if (card.type === "artifact") return `神器${card.value}`;
  if (card.type === "disaster") {
    return { collapse: "塌方", trap: "陷阱", serpent: "妖蛇", idol: "傀儡", swarm: "虫群" }[card.disaster] ?? card.disaster;
  }
  return String(card.type ?? "");
}

function incanCardIcon(card) {
  if (!card) return "?";
  if (card.type === "treasure") return "◆";
  if (card.type === "artifact") return "✦";
  if (card.type === "disaster") return "!";
  return "?";
}

function incanEventIcon(eventId) {
  return {
    chupacabra: "☄",
    double_challenge: "Ⅱ",
    endless_wealth: "◆",
    triple_challenge: "Ⅲ",
    farewell: "↩"
  }[eventId] ?? "✧";
}

function incanEventClass(eventId) {
  const safeId = String(eventId ?? "default").replace(/[^a-z0-9_-]/gi, "");
  return `incan-event-${safeId || "default"}`;
}

function incanPlayerName(players, userId) {
  return players.find((player) => String(player.userId) === String(userId))?.username ?? `玩家${userId}`;
}

export { incanCardTypeText, incanPhaseText, incanPhaseHint, incanDisasterText, incanCardText, incanCardIcon, incanEventIcon, incanEventClass, incanPlayerName };
