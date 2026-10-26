import { TEXT } from "../../features/rooms/roomText.js";

const MOVE_LABELS = {
  stone: "\u77f3\u5934",
  scissors: "\u526a\u5200",
  paper: "\u5e03",
  lizard: "\u8725\u8734",
  spock: "\u53f2\u6ce2\u514b"
};

function relationLines(fiveMoves) {
  const lines = [
    "\u77f3\u5934 > \u526a\u5200",
    "\u526a\u5200 > \u5e03",
    "\u5e03 > \u77f3\u5934"
  ];
  if (!fiveMoves) {
    return lines;
  }
  return [
    "\u77f3\u5934 > \u526a\u5200\u3001\u8725\u8734",
    "\u526a\u5200 > \u5e03\u3001\u8725\u8734",
    "\u5e03 > \u77f3\u5934\u3001\u53f2\u6ce2\u514b",
    "\u8725\u8734 > \u53f2\u6ce2\u514b\u3001\u5e03",
    "\u53f2\u6ce2\u514b > \u77f3\u5934\u3001\u526a\u5200"
  ];
}

function visibleRpsGroups(groups, currentUserId, mode) {
  if (mode !== "bracket") {
    return groups;
  }
  const currentGroup = groups.find((group) =>
    (group.players ?? []).some((player) => String(player.userId) === String(currentUserId))
    && group.status !== "finished"
  );
  return currentGroup ? [currentGroup] : groups;
}

function rpsPlayerLabel(player) {
  if (player.bot) {
    return "\u673a\u5668\u4eba";
  }
  if (typeof player.seatIndex === "number") {
    return `${player.seatIndex + 1}\u53f7\u73a9\u5bb6`;
  }
  return String(player.username ?? "");
}

function moveDisplayFor(player, submissions, lastRoundSubmissions, group) {
  const key = String(player.userId);
  const currentMove = submissions[key];
  if (currentMove) {
    if (group.status === "finished") {
      return MOVE_LABELS[currentMove] ?? currentMove;
    }
    return player.bot ? TEXT.waitingChoice : TEXT.completedChoice;
  }
  const lastMove = lastRoundSubmissions[key];
  if (lastMove) {
    return MOVE_LABELS[lastMove] ?? lastMove;
  }
  return TEXT.waitingChoice;
}

function rpsStatusText({ currentSeat, active, candidate, submitted }) {
  if (!currentSeat) {
    return TEXT.spectator;
  }
  if (candidate || !active) {
    return TEXT.candidate;
  }
  if (submitted) {
    return TEXT.rpsSubmitted;
  }
  return TEXT.selectMove;
}

export { MOVE_LABELS, relationLines, visibleRpsGroups, rpsPlayerLabel, moveDisplayFor, rpsStatusText };
