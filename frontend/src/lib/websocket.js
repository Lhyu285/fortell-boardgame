export function createRoomSocket(gameType, roomId, onMessage) {
  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  const socket = new WebSocket(`${protocol}://${window.location.host}/ws/rooms/${gameType}/${roomId}`);
  socket.addEventListener("message", (event) => {
    try {
      onMessage(JSON.parse(event.data));
    } catch {
      onMessage({ type: "invalid", payload: null });
    }
  });
  return socket;
}
