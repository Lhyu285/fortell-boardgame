export function createRoomSocket(gameType, roomId, onMessage, onStatus = () => {}) {
  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  let socket = null;
  let retryTimer = null;
  let retryAttempt = 0;
  let closed = false;

  function connect() {
    if (closed) return;
    onStatus(retryAttempt === 0 ? "connecting" : "reconnecting");
    socket = new WebSocket(`${protocol}://${window.location.host}/ws/rooms/${gameType}/${roomId}`);

    socket.addEventListener("open", () => {
      retryAttempt = 0;
      onStatus("connected");
    });
    socket.addEventListener("message", (event) => {
      try {
        onMessage(JSON.parse(event.data));
      } catch {
        onMessage({ type: "invalid", payload: null });
      }
    });
    socket.addEventListener("error", () => onStatus("error"));
    socket.addEventListener("close", () => {
      if (closed) return;
      const delay = Math.min(8000, 600 * (2 ** retryAttempt));
      retryAttempt += 1;
      onStatus("reconnecting");
      retryTimer = window.setTimeout(connect, delay);
    });
  }

  connect();
  return {
    close() {
      closed = true;
      if (retryTimer !== null) {
        window.clearTimeout(retryTimer);
      }
      socket?.close();
    }
  };
}
