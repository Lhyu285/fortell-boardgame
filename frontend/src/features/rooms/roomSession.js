import { apiGet, apiPost, apiDelete } from "../../lib/api.js";
import { createRoomSocket } from "../../lib/websocket.js";

export function initialRoomSessionState() {
  return { room: null, error: "", connectionState: "connecting", actionPending: false };
}

function versionOf(room) {
  const value = room?.gameState?.version;
  return value !== undefined && value !== null && Number.isFinite(Number(value)) ? Number(value) : null;
}

/** An instance belongs to one mounted room, never to a game or a module-level cache. */
export function createRoomSession({ gameType, roomId, onChange = () => {}, onDismissed = () => {},
  transport = { get: apiGet, post: apiPost, remove: apiDelete, socket: createRoomSocket } }) {
  const path = `/api/rooms/${gameType}/${roomId}`;
  let state = initialRoomSessionState();
  let socket = null, active = false, generation = 0, revision = 0, lifecycle = 0;
  const current = token => active && generation === token;
  const stamp = () => ({ revision, lifecycle });
  const commandToken = () => active ? `${generation}:${lifecycle}` : null;
  function publish(patch, token = generation) {
    if (!current(token)) return;
    state = { ...state, ...patch };
    onChange(state);
  }
  function acceptRoom(room, token, observed = null, authoritative = false) {
    if (!current(token) || !room || typeof room !== "object" || Array.isArray(room)) return false;
    if (room.gameType != null && room.gameType !== gameType) return false;
    if (room.roomId != null && String(room.roomId) !== String(roomId)) return false;
    if (observed && observed.lifecycle !== lifecycle) return false;
    const incoming = versionOf(room), existing = versionOf(state.room);
    const sameStatus = room.status === state.room?.status;
    // Versions can legitimately reset after ending/restarting a game. A fresh GET
    // also recovers a lifecycle change missed while disconnected.
    if (sameStatus && incoming !== null && existing !== null && incoming < existing
      && !(authoritative && observed?.revision === revision)) return false;
    // A snapshot arrived after this HTTP request began. An unversioned or equal-version
    // response cannot prove it is newer, so retain the already received snapshot.
    if (observed && observed.revision !== revision && sameStatus
      && !(incoming !== null && existing !== null && incoming > existing)) return false;
    const reset = state.room && (!sameStatus || incoming !== null && existing !== null && incoming < existing);
    if (reset) lifecycle++;
    revision++;
    publish({ room, ...(reset ? { actionPending: false } : {}) }, token);
    return true;
  }
  function close() {
    active = false;
    generation++;
    const previous = socket;
    socket = null;
    previous?.close();
  }
  function depart(token) {
    if (!current(token)) return;
    close();
    onDismissed();
  }
  async function refresh(token, initial = false) {
    const observed = stamp();
    try {
      const room = await transport.get(path);
      if (acceptRoom(room, token, observed, true) && initial) publish({ error: "" }, token);
    } catch (exception) {
      // Reconnect refresh failures do not replace the primary WebSocket indicator.
      if (initial && current(token) && revision === observed.revision) publish({ error: exception.message }, token);
    }
  }
  async function request(send, exit = false) {
    const token = generation, observed = stamp();
    if (!current(token)) return;
    try {
      const room = await send();
      if (!current(token)) return;
      if (exit) { depart(token); return; }
      if (observed.lifecycle !== lifecycle) return;
      acceptRoom(room, token, observed);
      publish({ error: "" }, token);
    } catch (exception) {
      if (observed.lifecycle === lifecycle) publish({ error: exception.message }, token);
    }
  }
  return {
    start() {
      if (active) return;
      active = true;
      const token = ++generation;
      revision = 0;
      lifecycle = 0;
      publish(initialRoomSessionState(), token);
      socket = transport.socket(gameType, roomId, message => {
        if (!current(token) || !message || typeof message !== "object") return;
        if (message.type === "room.snapshot") acceptRoom(message.payload, token);
        if (message.type === "room.dismissed") depart(token);
      }, status => {
        if (!current(token)) return;
        publish({ connectionState: status }, token);
        if (status === "connected") void refresh(token);
      });
      void refresh(token, true);
    },
    close,
    getSnapshot: () => state,
    token: commandToken,
    setError: error => publish({ error }),
    setActionPending: (actionPending, token) => {
      if (token === commandToken()) publish({ actionPending });
    },
    post: (suffix, body, exit = false) => request(() => transport.post(path + suffix, body), exit),
    dismiss: () => request(() => transport.remove(path), true)
  };
}
