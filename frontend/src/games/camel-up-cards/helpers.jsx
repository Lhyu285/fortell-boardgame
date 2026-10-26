import { TEXT } from "../../features/rooms/roomText.js";

const CAMEL_COLORS = {
  red: "\u7ea2",
  yellow: "\u9ec4",
  blue: "\u84dd",
  green: "\u7eff",
  purple: "\u7d2b",
  black: "\u9ed1"
};

function camelPlayerName(players, userId) {
  return players.find((player) => String(player.userId) === String(userId))?.username ?? "";
}

function camelRaceCardText(card) {
  if (!card) return "";
  return `${CAMEL_COLORS[card.color] ?? card.color}${card.steps}`;
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function camelBetCardText(card) {
  if (!card) return "";
  const color = CAMEL_COLORS[card.color] ?? card.color;
  const payouts = Array.isArray(card.payouts) ? card.payouts.join("/") : "";
  if (card.type === "leg_winner") return `${color}${TEXT.legWinner}(${payouts})`;
  if (card.type === "leg_middle") return `${color}${TEXT.legMiddle}(${payouts})`;
  if (card.type === "final_winner") return `${color}${TEXT.finalWinner}(${payouts})`;
  if (card.type === "final_loser") return `${color}${TEXT.finalLoser}(${payouts})`;
  return `${color}(${payouts})`;
}

function camelTokenStatusText(status) {
  if (status === "disabled") return "未启用";
  if (status === "available") return "未使用";
  if (status === "used") return "已使用";
  return status ?? "未启用";
}

function camelTokenAt(tokens, position) {
  const names = [];
  if (tokens.shortcut?.status === "placed" && Number(tokens.shortcut.position) === position) {
    names.push("捷径");
  }
  if (tokens.fennec?.status === "placed" && Number(tokens.fennec.position) === position) {
    names.push("耳廓狐");
  }
  return names.join(", ");
}

export { CAMEL_COLORS, camelPlayerName, camelRaceCardText, asArray, camelBetCardText, camelTokenStatusText, camelTokenAt };
