

function isSeatOccupied(seat) {
  return seat?.isOccupied === true && displayName(seat?.user) !== "";
}

function displayName(user) {
  return String(user?.username ?? user?.name ?? "").trim();
}

export { isSeatOccupied, displayName };
