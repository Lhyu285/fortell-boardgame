import assert from "node:assert/strict";
import { test } from "node:test";
import fs from "node:fs";
import path from "node:path";
import { createHash } from "node:crypto";
import { parse } from "@babel/parser";
import React from "react";
import { loadRoomWorkflow, frontendRoot } from "./support/load-desktops.mjs";
import { mountHooks } from "./support/hook-renderer.mjs";
import { prepareProps } from "./support/settings-fixtures.mjs";

const workflow = loadRoomWorkflow();
const flushPromises = async () => { for (let i = 0; i < 5; i++) await Promise.resolve(); };
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
function table({ gameType = "brass", roomId = "123456", start = true } = {}) {
  const gets = [], writes = [], sockets = [], changes = [], exits = [];
  const transport = {
    get(path) { const wait = deferred(); gets.push({ path, ...wait }); return wait.promise; },
    post(path, body) { const wait = deferred(); writes.push({ method: "POST", path, body, ...wait }); return wait.promise; },
    remove(path) { const wait = deferred(); writes.push({ method: "DELETE", path, ...wait }); return wait.promise; },
    socket(type, id, message, status) {
      const socket = { type, id, message, status, closes: 0, close() { socket.closes++; status("error"); } };
      sockets.push(socket); return socket;
    }
  };
  const session = workflow.createRoomSession({ gameType, roomId, transport, onChange: value => changes.push(value), onDismissed: () => exits.push(roomId) });
  const view = (patch = {}) => ({ gameType, roomId, status: "WAITING", currentUser: { id: 1 }, owner: { id: 1 },
    seatCount: 4, seats: [], config: {}, gameState: { version: 1 }, ...patch });
  const commands = workflow.createRoomCommands(session, { gameType, protocol: workflow.getGameDefinition(gameType)?.commands,
    confirm: () => true, newActionId: () => `${gameType}-${roomId}-${writes.length}` });
  if (start) session.start();
  return { gameType, roomId, session, commands, view, gets, writes, sockets, changes, exits, transport };
}
async function ready(options, patch = {}) {
  const fixture = table(options);
  fixture.gets[0].resolve(fixture.view(patch)); await flushPromises();
  return fixture;
}

test("session owns one socket and the existing initial/reconnect GET paths, without polling", async () => {
  const f = await ready();
  assert.equal(f.sockets.length, 1);
  assert.deepEqual([f.sockets[0].type, f.sockets[0].id], ["brass", "123456"]);
  assert.equal(f.gets[0].path, "/api/rooms/brass/123456");
  f.session.start(); assert.equal(f.sockets.length, 1);
  f.sockets[0].status("reconnecting"); assert.equal(f.session.getSnapshot().connectionState, "reconnecting");
  f.sockets[0].status("connected"); assert.equal(f.gets.length, 2);
  f.gets[1].resolve(f.view({ gameState: { version: 2 } })); await flushPromises();
  assert.equal(f.session.getSnapshot().room.gameState.version, 2);
  f.session.close(); f.session.close();
  assert.equal(f.sockets[0].closes, 1);
  const before = f.changes.length;
  f.sockets[0].status("connected"); f.sockets[0].message({ type: "room.dismissed" });
  assert.equal(f.changes.length, before); assert.equal(f.gets.length, 2); assert.deepEqual(f.exits, []);
});

test("initial failures remain visible, WS can recover, and reconnect GET failure keeps the connection indicator", async () => {
  const f = table();
  f.gets[0].reject(new Error("load failed")); await flushPromises();
  assert.equal(f.session.getSnapshot().error, "load failed");
  assert.equal(f.session.getSnapshot().room, null);
  f.sockets[0].message({ type: "room.snapshot", payload: f.view() });
  assert.ok(f.session.getSnapshot().room);
  f.sockets[0].status("connected");
  f.gets[1].reject(new Error("refresh failed")); await flushPromises();
  assert.equal(f.session.getSnapshot().connectionState, "connected");
  assert.equal(f.session.getSnapshot().error, "load failed");
});

test("late initial GET errors do not overwrite a successfully received WS room", async () => {
  const f = table();
  f.sockets[0].message({ type: "room.snapshot", payload: f.view() });
  f.gets[0].reject(new Error("stale failure")); await flushPromises();
  assert.equal(f.session.getSnapshot().error, "");
  assert.ok(f.session.getSnapshot().room);
});

test("wrong-room/game snapshots, unknown messages and lower versions cannot replace current room state", async () => {
  const f = await ready({}, { gameState: { version: 10 } });
  for (const payload of [null, [], f.view({ roomId: "654321" }), f.view({ gameType: "rps" }), f.view({ gameState: { version: 9 } })]) {
    f.sockets[0].message({ type: "room.snapshot", payload });
  }
  f.sockets[0].message({ type: "invalid", payload: null });
  f.sockets[0].message(null);
  assert.equal(f.session.getSnapshot().room.gameState.version, 10);
  f.sockets[0].message({ type: "room.snapshot", payload: f.view({ gameState: { version: 10 }, config: { changed: true } }) });
  assert.equal(f.session.getSnapshot().room.config.changed, true, "equal-version WS settings updates remain supported");
});

for (const gameType of ["brass", "rps"]) {
  test(`${gameType}: in-flight GET/command responses do not roll back a newer WS snapshot`, async () => {
    const gameState = gameType === "brass" ? { version: 5 } : { round: 5 };
    const f = await ready({ gameType }, { gameState });
    f.sockets[0].status("connected");
    const request = f.commands.standUp();
    const newer = f.view({ gameState: gameType === "brass" ? { version: 6 } : { round: 6 }, config: { latest: true } });
    f.sockets[0].message({ type: "room.snapshot", payload: newer });
    f.gets[1].resolve(f.view({ gameState })); f.writes[0].resolve(f.view({ gameState }));
    await request; await flushPromises();
    assert.equal(f.session.getSnapshot().room, newer);
    if (gameType === "brass") {
      const next = f.commands.standUp();
      f.sockets[0].message({ type: "room.snapshot", payload: f.view({ gameState: { version: 7 } }) });
      f.writes[1].resolve(f.view({ gameState: { version: 8 } })); await next;
      assert.equal(f.session.getSnapshot().room.gameState.version, 8);
    }
  });
}

test("closed sessions ignore pending GET/command success or failure, leave navigation and stale handlers", async () => {
  for (const fail of [false, true]) {
    const f = table();
    const leave = f.commands.leaveRoom();
    f.session.close(); const before = f.changes.length;
    if (fail) { f.gets[0].reject(new Error("old GET")); f.writes[0].reject(new Error("old leave")); }
    else { f.gets[0].resolve(f.view()); f.writes[0].resolve({ success: true }); }
    await leave; await flushPromises();
    assert.equal(f.changes.length, before); assert.deepEqual(f.exits, []);
    f.commands.moveSeat(0); f.commands.submitAction("skip");
    assert.equal(f.writes.length, 1, "old closures cannot dispatch after cleanup");
  }
});

test("ending/restarting a game permits version reset without accepting old responses from the previous game", async () => {
  for (const resetViaSocket of [false, true]) {
    const f = await ready({}, { status: "IN_PROGRESS", gameState: { version: 50 } });
    const old = f.commands.submitAction("skip");
    const end = f.commands.endGame();
    const waiting = f.view({ gameState: { version: 0 } });
    if (resetViaSocket) f.sockets[0].message({ type: "room.snapshot", payload: waiting });
    f.writes[1].resolve(waiting); await end;
    assert.equal(f.session.getSnapshot().room.status, "WAITING");
    assert.equal(f.session.getSnapshot().room.gameState.version, 0);
    const start = f.commands.startGame();
    f.writes[2].resolve(f.view({ status: "IN_PROGRESS", gameState: { version: 0 } })); await start;
    f.writes[0].resolve(f.view({ status: "IN_PROGRESS", gameState: { version: 51 } })); await old;
    assert.equal(f.session.getSnapshot().room.gameState.version, 0);
  }
});

test("fresh reconnect GET recovers a restart missed offline, even when the new game's version is lower", async () => {
  const f = await ready({}, { status: "IN_PROGRESS", gameState: { version: 50 } });
  const old = f.commands.submitAction("skip");
  f.sockets[0].status("reconnecting"); f.sockets[0].status("connected");
  f.gets[1].resolve(f.view({ status: "IN_PROGRESS", gameState: { version: 0 } })); await flushPromises();
  f.writes[0].resolve(f.view({ status: "IN_PROGRESS", gameState: { version: 51 } })); await old;
  assert.equal(f.session.getSnapshot().room.gameState.version, 0);
});

test("a restarted Incan game clears the previous pending guard, and old failures/finalizers cannot touch the new action", async () => {
  const f = await ready({ gameType: "incan_gold" }, { status: "IN_PROGRESS", gameState: { version: 50 } });
  const old = f.commands.submitAction("draw_exploration_card");
  f.sockets[0].message({ type: "room.snapshot", payload: f.view({ gameState: { version: 0 } }) });
  assert.equal(f.session.getSnapshot().actionPending, false);
  f.sockets[0].message({ type: "room.snapshot", payload: f.view({ status: "IN_PROGRESS", gameState: { version: 0 } }) });
  const next = f.commands.submitAction("draw_exploration_card");
  assert.equal(f.writes.length, 2);
  f.writes[0].reject(new Error("previous game failure")); await old;
  assert.equal(f.session.getSnapshot().error, "");
  assert.equal(f.session.getSnapshot().actionPending, true);
  f.writes[1].resolve(f.view({ status: "IN_PROGRESS", gameState: { version: 1 } })); await next;
  assert.equal(f.session.getSnapshot().actionPending, false);
});

test("effect setup/cleanup/setup uses a new generation: old socket, HTTP and pending finalizers stay inert", async () => {
  const f = await ready({ gameType: "incan_gold" });
  const oldAction = f.commands.submitAction("draw_exploration_card");
  f.session.close(); f.session.start();
  f.gets[1].resolve(f.view({ gameState: { version: 20 } })); await flushPromises();
  const newAction = f.commands.submitAction("choose_expedition_action", { choice: "return" });
  f.sockets[0].message({ type: "room.dismissed" }); f.sockets[0].status("connected");
  f.writes[0].reject(new Error("old action failed")); await oldAction;
  assert.equal(f.session.getSnapshot().actionPending, true);
  assert.equal(f.session.getSnapshot().room.gameState.version, 20);
  assert.equal(f.session.getSnapshot().error, ""); assert.deepEqual(f.exits, []);
  f.writes[1].resolve(f.view({ gameState: { version: 21 } })); await newAction;
  assert.equal(f.session.getSnapshot().actionPending, false);
});

test("room dismissal and successful leave/delete navigate exactly once and close only their own subscription", async () => {
  for (const action of ["message", "leaveRoom", "dismissRoom"]) {
    const f = await ready();
    if (action === "message") f.sockets[0].message({ type: "room.dismissed" });
    else { const pending = f.commands[action](); f.writes[0].resolve({ success: true }); await pending; }
    f.sockets[0].message({ type: "room.dismissed" });
    assert.deepEqual(f.exits, [f.roomId]); assert.equal(f.sockets[0].closes, 1);
  }
});

test("failed leave/delete stay in the room, expose the error and do not close its socket", async () => {
  for (const action of ["leaveRoom", "dismissRoom"]) {
    const f = await ready(); const request = f.commands[action]();
    f.writes[0].reject(new Error("permission denied")); await request;
    assert.deepEqual(f.exits, []); assert.equal(f.sockets[0].closes, 0);
    assert.equal(f.session.getSnapshot().error, "permission denied");
  }
});

for (const gameType of ["brass", "incan_gold", "rps", "camel_up_cards", "thingsInRings", "gobang"]) {
  test(`${gameType}: exact existing action envelope, numeric version, unique ID and fresh snapshot at invocation`, async () => {
    const f = await ready({ gameType }, { gameState: { version: "7" } });
    const payload = { resourceSelections: [{ sourceId: "existing-slot", amount: 1 }], merchantId: "Oxford" };
    const before = JSON.stringify(payload);
    const pending = f.commands.submitAction("build", payload);
    const versioned = ["brass", "incan_gold"].includes(gameType);
    assert.deepEqual(f.writes[0].body, { type: "build", payload, ...(versioned ? { stateVersion: 7, clientActionId: `${gameType}-123456-0` } : {}) });
    assert.equal(f.writes[0].path, `/api/rooms/${gameType}/123456/actions`);
    f.writes[0].resolve(f.view({ gameState: { version: 8 } })); await pending;
    const next = f.commands.submitAction("end_turn");
    assert.deepEqual(f.writes[1].body, { type: "end_turn", payload: {}, ...(versioned ? { stateVersion: 8, clientActionId: `${gameType}-123456-1` } : {}) });
    f.writes[1].resolve(f.view({ gameState: { version: 9 } })); await next;
    assert.equal(JSON.stringify(payload), before);
  });
}

test("Incan duplicate guard is synchronous, clears on error, and never retries a versioned command automatically", async () => {
  const f = await ready({ gameType: "incan_gold" });
  const first = f.commands.submitAction("draw_exploration_card");
  assert.equal(f.session.getSnapshot().actionPending, true);
  assert.equal(f.commands.submitAction("draw_exploration_card"), undefined);
  assert.equal(f.writes.length, 1);
  f.writes[0].reject(new Error("stale stateVersion")); await first;
  assert.equal(f.session.getSnapshot().actionPending, false);
  assert.equal(f.session.getSnapshot().error, "stale stateVersion");
  assert.equal(f.writes.length, 1);
  const next = f.commands.submitAction("draw_exploration_card");
  assert.notEqual(f.writes[0].body.clientActionId, f.writes[1].body.clientActionId);
  f.writes[1].resolve(f.view({ gameState: { version: 2 } })); await next;
  assert.equal(f.session.getSnapshot().error, "");
});

test("Brass and other games retain their existing submission policy rather than acquiring an Incan-only guard", async () => {
  const f = await ready();
  const first = f.commands.submitAction("skip"), second = f.commands.submitAction("skip");
  assert.equal(f.writes.length, 2); assert.equal(f.session.getSnapshot().actionPending, false);
  f.writes[1].resolve(f.view({ gameState: { version: 2 } })); await second;
  f.writes[0].reject(new Error("duplicate action")); await first;
  assert.equal(f.session.getSnapshot().room.gameState.version, 2);
});

test("seat/config/start/end/proposal/move endpoints and confirmations retain original payloads", async () => {
  const f = await ready({ gameType: "rps" }, { config: { mode: "free_for_all", custom: { keep: true } } });
  const steps = [
    [() => f.commands.moveSeat(2), "/seat", { seatIndex: 2 }],
    [() => f.commands.standUp(), "/stand", {}],
    [() => f.commands.forceStandUp(1), "/seats/1/stand", {}],
    [() => f.commands.changeSeatCount(1), "/config", { config: { mode: "free_for_all", custom: { keep: true }, seatCount: 5 } }],
    [() => f.commands.updateGameConfig("mode", "bracket"), "/config", { config: { mode: "bracket", custom: { keep: true } } }],
    [() => f.commands.startGame(), "/start", {}],
    [() => f.commands.endGame(), "/end", {}],
    [() => f.commands.proposeEndGame(), "/propose-end", {}],
    [() => f.commands.submitMove("stone"), "/actions", { type: "submit_move", payload: { move: "stone" } }]
  ];
  for (const [invoke, suffix, body] of steps) {
    const pending = invoke(), write = f.writes.at(-1);
    assert.equal(write.path, `/api/rooms/rps/123456${suffix}`); assert.equal(write.method, "POST"); assert.deepEqual(write.body, body);
    write.resolve(f.view({ config: { mode: "free_for_all", custom: { keep: true } } })); await pending;
  }
  for (const method of ["startGame", "endGame"]) {
    const prompts = [];
    const cancel = workflow.createRoomCommands(f.session, { gameType: "rps", confirm: message => { prompts.push(message); return false; } });
    cancel[method](); assert.equal(f.writes.length, steps.length); assert.equal(prompts.length, 1);
    if (method === "startGame") assert.equal(prompts[0], "Start game?");
  }
  const leave = f.commands.leaveRoom();
  assert.equal(f.writes.at(-1).path, "/api/rooms/rps/123456/leave"); assert.deepEqual(f.writes.at(-1).body, {});
  f.writes.at(-1).resolve({}); await leave;
  const deletion = await ready(); const dismissed = deletion.commands.dismissRoom();
  assert.equal(deletion.writes[0].method, "DELETE"); assert.equal(deletion.writes[0].path, "/api/rooms/brass/123456");
  assert.equal("body" in deletion.writes[0], false); deletion.writes[0].resolve({}); await dismissed;
});

test("seat limits and occupied-seat lower bound reject locally; latest settings merge preserves unknown fields", async () => {
  const f = await ready({ gameType: "brass" });
  f.commands.changeSeatCount(1); assert.equal(f.writes.length, 0); assert.ok(f.session.getSnapshot().error);
  const seats = Array.from({ length: 4 }, (_, i) => ({ isOccupied: true, user: { id: i + 1, username: `P${i}` } }));
  f.sockets[0].message({ type: "room.snapshot", payload: f.view({ seats }) });
  f.commands.changeSeatCount(-1); assert.equal(f.writes.length, 0);
  f.sockets[0].message({ type: "room.snapshot", payload: f.view({ seatCount: 2 }) });
  f.commands.changeSeatCount(-1); assert.equal(f.writes.length, 0);
  f.sockets[0].message({ type: "room.snapshot", payload: f.view({ config: { recentlyChanged: 42, customRules: { WORD: "w" } } }) });
  const update = f.commands.updateGameConfig("difficulty", "easy");
  assert.deepEqual(f.writes[0].body, { config: { recentlyChanged: 42, customRules: { WORD: "w" }, difficulty: "easy" } });
  f.writes[0].resolve(f.view()); await update;
});

test("common preparation model retains nested/legacy seats, permissions and finished-state handling", () => {
  const props = prepareProps("rps");
  const room = { currentUser: { id: 1 }, owner: { id: 1 }, status: "WAITING", seatCount: 2, seats: props.seats, permissions: { canLeaveRoom: true } };
  assert.equal(workflow.deriveRoomModel(room, "rps").canStart, true);
  assert.equal(workflow.deriveRoomModel({ ...room, currentUser: { id: 2 } }, "rps").canStart, false);
  assert.equal(workflow.deriveRoomModel({ ...room, status: "IN_PROGRESS" }, "rps").canAddSeat, false);
  assert.equal(workflow.deriveRoomModel({ ...room, seats: [] }, "rps").canStart, false);
  assert.equal(workflow.deriveRoomModel({ ...room, permissions: {} }, "rps").canLeaveRoom, false);
  assert.equal(workflow.deriveRoomModel({ ...room, roomState: { seatCount: 2, seats: props.seats }, seats: [] }, "rps").canStart, true);
  assert.equal(workflow.deriveRoomModel({ ...room, gameState: { phase: "GAME_OVER" } }, "rps").gameFinished, true);
});

test("100 same-game sessions isolate sockets, GETs, pending actions, failures, exits and out-of-order replies", async () => {
  const rooms = await Promise.all(Array.from({ length: 100 }, (_, i) => ready({ gameType: "incan_gold", roomId: String(100000 + i) }, { gameState: { version: i } })));
  const requests = rooms.map(f => f.commands.submitAction("choose_expedition_action", { choice: "continue" }));
  for (const [i, f] of rooms.entries()) {
    assert.equal(f.writes.length, 1); assert.equal(f.session.getSnapshot().actionPending, true);
    assert.equal(f.writes[0].path, `/api/rooms/incan_gold/${100000 + i}/actions`);
    assert.equal(f.writes[0].body.stateVersion, i);
    assert.equal(f.commands.submitAction("draw_exploration_card"), undefined);
  }
  rooms[0].session.close();
  for (let i = 99; i >= 0; i--) {
    const f = rooms[i];
    f.sockets[0].message({ type: "room.snapshot", payload: f.view({ gameState: { version: i + 10 } }) });
    if (i === 42) f.writes[0].reject(new Error("only room 42"));
    else f.writes[0].resolve(f.view({ gameState: { version: i + 1 } }));
    await requests[i];
  }
  for (const [i, f] of rooms.entries()) {
    assert.equal(f.session.getSnapshot().room.roomId, String(100000 + i));
    if (i === 0) continue;
    assert.equal(f.session.getSnapshot().room.gameState.version, i + 10);
    assert.equal(f.session.getSnapshot().error, i === 42 ? "only room 42" : "");
    assert.equal(f.session.getSnapshot().actionPending, false);
  }
  rooms[50].sockets[0].message({ type: "room.dismissed" });
  rooms.forEach((f, i) => { assert.equal(f.exits.length, i === 50 ? 1 : 0); f.session.close(); });
  assert.ok(rooms.every(f => f.sockets[0].closes === 1));
});

test("hook adapters own and clean their real session/command instances without module-global state", async () => {
  const originalFetch = globalThis.fetch, originalSocket = globalThis.WebSocket, originalWindow = globalThis.window;
  const gets = [], sockets = [], navigations = [];
  globalThis.window = { location: { protocol: "http:", host: "example.test" } };
  globalThis.fetch = path => { const wait = deferred(); gets.push({ path, ...wait }); return wait.promise; };
  globalThis.WebSocket = class {
    constructor(url) { this.url = url; this.handlers = {}; this.closed = false; sockets.push(this); }
    addEventListener(name, handler) { this.handlers[name] = handler; }
    close() { this.closed = true; this.handlers.close?.(); }
  };
  const navigate = (...args) => navigations.push(args);
  function Probe() {
    const state = workflow.useRoomSession("brass", "123456", navigate);
    const commands = workflow.useRoomCommands(state.session, "brass");
    return React.createElement("output", { state, commands });
  }
  const view = mountHooks(Probe, {});
  try {
    assert.equal(sockets[0].url, "ws://example.test/ws/rooms/brass/123456");
    assert.equal(gets[0].path, "/api/rooms/brass/123456");
    gets[0].resolve({ ok: true, text: async () => JSON.stringify({ gameType: "brass", roomId: "123456", gameState: { version: 1 } }) });
    await flushPromises(); view.act(() => {});
    assert.equal(view.tree.props.state.room.roomId, "123456");
    const commands = view.tree.props.commands;
    view.update({}); assert.equal(view.tree.props.commands, commands);
    view.unmount(); assert.equal(sockets[0].closed, true);
    sockets[0].handlers.message({ data: JSON.stringify({ type: "room.dismissed" }) });
    assert.deepEqual(navigations, []);
  } finally { globalThis.fetch = originalFetch; globalThis.WebSocket = originalSocket; globalThis.window = originalWindow; }
});

test("100 sessions through the real HTTP/socket adapters preserve credentials, reconnect paths and timer cleanup", async () => {
  const originalFetch = globalThis.fetch, originalSocket = globalThis.WebSocket, originalWindow = globalThis.window;
  const sockets = [], requests = [], timers = new Map(); let nextTimer = 0;
  globalThis.window = { location: { protocol: "https:", host: "example.test" },
    setTimeout(callback, delay) { const id = ++nextTimer; timers.set(id, { callback, delay }); return id; },
    clearTimeout(id) { timers.delete(id); } };
  globalThis.fetch = async (path, options) => {
    requests.push({ path, options });
    const [, , , gameType, roomId] = path.split("/");
    return { ok: true, text: async () => JSON.stringify({ gameType, roomId, status: "IN_PROGRESS", gameState: { version: 1 } }) };
  };
  globalThis.WebSocket = class {
    constructor(url) { this.url = url; this.handlers = {}; sockets.push(this); }
    addEventListener(name, handler) { this.handlers[name] = handler; }
    close() { this.handlers.close?.(); }
  };
  const sessions = [];
  try {
    for (let i = 0; i < 100; i++) sessions.push(workflow.createRoomSession({ gameType: "brass", roomId: String(100000 + i) }));
    sessions.forEach(session => session.start());
    assert.equal(sockets.length, 100);
    sockets.forEach((socket, i) => {
      assert.equal(socket.url, `wss://example.test/ws/rooms/brass/${100000 + i}`);
      socket.handlers.open();
    });
    await flushPromises();
    assert.equal(requests.length, 200, "one initial GET and one open refresh per room, no poll loop");
    assert.ok(requests.every(request => request.options.credentials === "include"));
    sockets.forEach(socket => socket.handlers.close());
    assert.equal(timers.size, 100); assert.ok([...timers.values()].every(timer => timer.delay === 600));
    sessions.slice(0, 50).forEach(session => session.close());
    assert.equal(timers.size, 50);
    for (const [id, timer] of [...timers]) { timers.delete(id); timer.callback(); }
    assert.equal(sockets.length, 150, "closed rooms never reconnect; remaining rooms reconnect independently");
    sockets.slice(100).forEach(socket => socket.handlers.open()); await flushPromises();
    assert.equal(requests.length, 250);
    const commands = workflow.createRoomCommands(sessions[75], { gameType: "brass", protocol: workflow.getGameDefinition("brass").commands, newActionId: () => "test-id" });
    await commands.submitAction("skip", { cardId: "card-a" });
    const sent = requests.at(-1);
    assert.equal(sent.path, "/api/rooms/brass/100075/actions");
    assert.equal(sent.options.method, "POST");
    assert.deepEqual(JSON.parse(sent.options.body), { type: "skip", payload: { cardId: "card-a" }, stateVersion: 1, clientActionId: "test-id" });
  } finally {
    sessions.forEach(session => session.close()); assert.equal(timers.size, 0);
    globalThis.fetch = originalFetch; globalThis.WebSocket = originalSocket; globalThis.window = originalWindow;
  }
});

const baseline = JSON.parse(fs.readFileSync(path.join(frontendRoot, "test/fixtures/room-workflow-baseline.json"), "utf8"));
const hash = value => createHash("sha256").update(value.replaceAll("\r\n", "\n")).digest("hex");
function sourceOf(relative) {
  const source = fs.readFileSync(path.join(frontendRoot, relative), "utf8");
  const ast = parse(source, { sourceType: "module", plugins: ["jsx"] });
  const declarations = new Map(ast.program.body.map(statement => statement.declaration ?? statement)
    .filter(node => ["FunctionDeclaration", "ClassDeclaration"].includes(node.type)).map(node => [node.id.name, node]));
  return { source, declarations, ast };
}
for (const [name, expected] of Object.entries(baseline.declarations)) {
  const file = ["PrepareRoom", "GameErrorBoundary"].includes(name) ? `src/features/rooms/${name}.jsx`
    : name === "GameRoom" ? "src/pages/RoomPage.jsx" : name === "createClientActionId" ? "src/features/rooms/roomCommands.js" : "src/features/rooms/roomModel.js";
  test(`unchanged extracted workflow declaration: ${name}`, () => {
    const { source, declarations } = sourceOf(file); const node = declarations.get(name);
    assert.ok(node); assert.equal(hash(source.slice(node.start, node.end)), expected);
  });
}
test("room markup, action wiring and title effect statements match pre-extraction source", () => {
  const { source, declarations } = sourceOf("src/pages/RoomPage.jsx");
  const statements = declarations.get("RoomContent").body.body;
  const renderStart = statements.findIndex(node => node.type === "IfStatement");
  assert.deepEqual(statements.slice(renderStart).map(node => hash(source.slice(node.start, node.end))), baseline.rendering);
  const title = statements.find(node => node.type === "ExpressionStatement" && source.slice(node.start, node.end).includes("document.title"));
  assert.equal(hash(source.slice(title.start, title.end)), baseline.titleEffect);
  assert.ok(source.includes("key={JSON.stringify([gameType, roomId])}"), "route changes must remount all per-room state and game drafts");
  assert.doesNotMatch(source, /lib\/(api|websocket)/, "room page must not own transport");
});
