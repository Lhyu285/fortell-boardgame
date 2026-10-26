import assert from "node:assert/strict";
import { test } from "node:test";
import fs from "node:fs";
import path from "node:path";
import { createHash } from "node:crypto";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { parse } from "@babel/parser";
import { loadRegistry, loadDispatcher, loadRoomWorkflow, warmGameRooms, frontendRoot } from "./support/load-desktops.mjs";
import { settingsCases, prepareProps } from "./support/settings-fixtures.mjs";
import { desktopProps } from "./support/desktop-fixtures.mjs";
import { mountHooks, textOf } from "./support/hook-renderer.mjs";

const registry = loadRegistry();
const dispatcher = await loadDispatcher();
const baseline = JSON.parse(fs.readFileSync(path.join(frontendRoot, "test/fixtures/game-registry-baseline.json"), "utf8"));
const hash = text => createHash("sha256").update(text.replaceAll("\r\n", "\n")).digest("hex");
const markup = (component, props) => renderToStaticMarkup(React.createElement(component, props));
const expectedDefaults = {
  brass: { seatCount: 4, config: { tableName: "伯明翰占位桌" } },
  incan_gold: { seatCount: 4, config: { thievesExpansion: false, eventsExpansion: false } },
  camel_up_cards: { seatCount: 4, config: { expansion: "shortcut_fennec" } },
  thingsInRings: { seatCount: 4, config: { difficulty: "hard", spectatorView: "god", customRules: { SCENE: "", WORD: "", ATTRIBUTE: "" } } },
  rps: { seatCount: 4, config: { moveSet: 3, mode: "free_for_all" } },
  gobang: { seatCount: 2, config: { ruleMode: "normal", firstHand: "random" } }
};

test("registry retains all existing case-sensitive URL keys, seat bounds and fallback behavior", async () => {
  assert.deepEqual(registry.registeredGames().map(game => game.key), Object.keys(expectedDefaults));
  const bounds = { brass: [2, 4], incan_gold: [2, 8], camel_up_cards: [2, 6], thingsInRings: [2, 6], rps: [2, 8], gobang: [2, 2] };
  for (const [key, [min, max]] of Object.entries(bounds)) assert.deepEqual(registry.gameSeatBounds(key), { min, max });
  for (const key of ["__proto__", "constructor", "thingsinrings", "unknown_game", undefined]) {
    assert.equal(registry.getGameDefinition(key), null);
    assert.deepEqual(registry.createGameDefaults(key), { seatCount: 4, config: {} });
    assert.deepEqual(registry.gameSeatBounds(key), { min: 2, max: 2 });
    assert.equal(await registry.preloadGameRoom(key), null);
    assert.equal(markup(registry.GameSettings, { gameType: key, config: {} }), "");
  }
  assert.equal(registry.gameName("brass", true), "伯明翰");
  assert.equal(registry.gameName("brass"), "工业革命：伯明翰");
  assert.deepEqual(Object.keys(expectedDefaults).map(key => registry.gameName(key)),
    ["工业革命：伯明翰", "印加宝藏", "狂野骆驼：卡牌版", "环中物语", "猜拳", "五子棋"]);
  assert.equal(registry.getGameDefinition("gobang").Room, null);
  assert.equal(registry.getGameDefinition("brass").Settings, null);
});

for (const [key, defaults] of Object.entries(expectedDefaults)) {
  test(`${key}: 100 default configurations are independent and retain the existing request schema`, () => {
    const rooms = Array.from({ length: 100 }, () => registry.createGameDefaults(key));
    for (const room of rooms) assert.deepEqual(room, defaults);
    rooms[0].config.testOnly = true;
    if (key === "thingsInRings") rooms[0].config.customRules.SCENE = "room 0 only";
    for (const room of rooms.slice(1)) assert.deepEqual(room, defaults);
    const game = registry.getGameDefinition(key);
    assert.ok(Object.isFrozen(game) && Object.isFrozen(game.seatBounds));
  });
}

const definition = { key: "future_game", name: "Future", seatBounds: { min: 2, max: 8 }, createDefaults: () => ({ seatCount: 4, config: {} }) };
test("invalid and duplicate registrations fail at startup rather than silently overwrite games", () => {
  for (const value of [null, { ...definition, key: "" }, { ...definition, key: "  " }, { ...definition, createDefaults: null },
    { ...definition, seatBounds: { min: 0, max: 8 } }, { ...definition, seatBounds: { min: 4, max: 2 } },
    { ...definition, seatBounds: { min: 2.5, max: 8 } }, { ...definition, loadRoom: true },
    { ...definition, desktopProps: {} }, { ...definition, commands: { versioned: "true" } },
    { ...definition, commands: { blockWhilePending: 1 } }]) assert.throws(() => registry.createGameRegistry([value]));
  assert.throws(() => registry.createGameRegistry([definition, definition]), /Duplicate/);
  assert.ok(Object.isFrozen(registry.registeredGames()));
});

test("100 parallel requests share only one lazy code load; registration and settings never preload desktops", async () => {
  let calls = 0;
  const sharedDefaults = { seatCount: 4, config: { nested: [{ value: 1 }] } };
  const module = { default: () => React.createElement("p", null, "future") };
  const local = registry.createGameRegistry([{ ...definition, createDefaults: () => sharedDefaults,
    loadRoom: async () => { calls++; return module; } }]);
  const game = local.get("future_game");
  assert.equal(calls, 0);
  const copies = [local.createDefaults("future_game"), local.createDefaults("future_game")];
  copies[0].config.nested[0].value = 10;
  assert.equal(copies[1].config.nested[0].value, 1);
  const pending = Array.from({ length: 100 }, () => game.preloadRoom());
  assert.ok(pending.every(promise => promise === pending[0]));
  assert.ok((await Promise.all(pending)).every(result => result === module));
  assert.equal(calls, 1);
  assert.equal(local.get("future_game").Room, game.Room);
});

test("desktop import failures and missing component exports reject without loading another game", async () => {
  for (const loadRoom of [async () => { throw new Error("chunk unavailable"); }, async () => ({}), async () => null]) {
    const game = registry.createGameRegistry([{ ...definition, loadRoom }]).get(definition.key);
    await assert.rejects(game.preloadRoom(), /chunk unavailable|Invalid game desktop/);
    await assert.rejects(game.preloadRoom());
  }
});

test("registration snapshots loader/default factory references instead of sharing the caller's mutable definition", async () => {
  const module = { default: () => null };
  const input = { ...definition, loadRoom: () => Promise.resolve(module) };
  const game = registry.createGameRegistry([input]).get(input.key);
  input.key = "changed"; input.loadRoom = () => Promise.reject(new Error("wrong loader")); input.createDefaults = () => ({ invalid: true });
  input.seatBounds = { min: 1, max: 1 };
  assert.equal(game.key, "future_game");
  assert.deepEqual(game.seatBounds, { min: 2, max: 8 });
  assert.deepEqual(game.createDefaults(), { seatCount: 4, config: {} });
  assert.equal(await game.preloadRoom(), module);
});

test("lazy loading retains the existing loading panel and unknown games retain their placeholder", async () => {
  const props = { ...desktopProps(), gameType: "rps" };
  assert.match(markup(registry.GameDesktop, props), /加载房间中/);
  assert.match(markup(registry.GameDesktop, { ...props, gameType: "gobang" }), /游戏界面预留中/);
  assert.match(markup(registry.GameDesktop, { ...props, gameType: "unknown_game" }), /游戏界面预留中/);
  await warmGameRooms(registry);
  assert.doesNotMatch(markup(registry.GameDesktop, props), /加载房间中/);
});

for (const item of settingsCases()) {
  test(`settings HTML matches pre-registry baseline: ${item.name}`, () => {
    const actual = markup(registry.GameSettings, { gameType: item.gameType, config: item.config, editable: item.editable, onChange() {} });
    assert.equal(hash(actual), baseline.settings[item.name]);
  });
}
for (const gameType of [...Object.keys(expectedDefaults), "unknown_game"]) {
  for (const owner of [false, true]) test(`waiting room HTML and settings position unchanged: ${gameType}/${owner}`, () => {
    const props = prepareProps(gameType, {}, owner);
    assert.equal(hash(markup(dispatcher.PrepareRoom, props)), baseline.prepare[`${gameType}-${owner}`]);
    const view = mountHooks(dispatcher.PrepareRoom, { ...props, waiting: false });
    assert.equal(view.all(node => ["input", "select"].includes(node.type)).length, 0);
  });
}

test("read-only guests still see every choice and cannot operate the native controls", () => {
  for (const gameType of Object.keys(expectedDefaults)) {
    const view = mountHooks(registry.GameSettings, { gameType, config: { ...registry.createGameDefaults(gameType).config, difficulty: "custom" }, editable: false });
    for (const control of view.all(node => ["input", "select"].includes(node.type))) assert.equal(control.props.disabled, true);
  }
});

test("Camel's two switches preserve all four legacy expansion strings", () => {
  const combinations = { none: [false, false], shortcut: [true, false], fennec: [false, true], shortcut_fennec: [true, true] };
  for (const [expansion, values] of Object.entries(combinations)) for (const index of [0, 1]) {
    const changes = [];
    const view = mountHooks(registry.GameSettings, { gameType: "camel_up_cards", config: { expansion }, editable: true, onChange: (...args) => changes.push(args) });
    const inputs = view.all(node => node.type === "input");
    assert.deepEqual(inputs.map(node => node.props.checked), values);
    view.act(() => inputs[index].props.onChange({ target: { checked: !values[index] } }));
    const next = [...values]; next[index] = !next[index];
    const expected = Object.keys(combinations).find(key => combinations[key].every((value, i) => value === next[i]));
    assert.deepEqual(changes, [["expansion", expected]]);
  }
});

test("settings preserve numeric, boolean, nested and enum payload types, with no renamed config fields", () => {
  const changes = [];
  const mount = (gameType, config = registry.createGameDefaults(gameType).config) => mountHooks(registry.GameSettings,
    { gameType, config, editable: true, onChange: (...args) => changes.push(args) });
  const rps = mount("rps");
  const selects = rps.all(node => node.type === "select");
  rps.act(() => selects[0].props.onChange({ target: { value: "5" } }));
  rps.act(() => selects[1].props.onChange({ target: { value: "bracket" } }));
  const incan = mount("incan_gold");
  incan.all(node => node.type === "input").forEach(input => incan.act(() => input.props.onChange({ target: { checked: true } })));
  const things = mount("thingsInRings", { difficulty: "custom", customRules: { SCENE: "s", WORD: "w", ATTRIBUTE: "a", extension: "keep" } });
  things.act(() => things.all(node => node.type === "input")[1].props.onChange({ target: { value: "new" } }));
  const gobang = mount("gobang");
  gobang.act(() => gobang.all(node => node.type === "select")[0].props.onChange({ target: { value: "professional" } }));
  assert.deepEqual(changes, [["moveSet", 5], ["mode", "bracket"], ["thievesExpansion", true], ["eventsExpansion", true],
    ["customRules", { SCENE: "s", WORD: "new", ATTRIBUTE: "a", extension: "keep" }], ["ruleMode", "professional"]]);
});

function nestedFunctions(relative) {
  const source = fs.readFileSync(path.join(frontendRoot, relative), "utf8");
  const ast = parse(source, { sourceType: "module", plugins: ["jsx"] });
  const page = ast.program.body.find(node => node.type === "ExportDefaultDeclaration").declaration;
  const functions = Object.fromEntries(page.body.body.filter(node => node.type === "FunctionDeclaration").map(node => [node.id.name, node]));
  return { source, functions };
}
// Stage 3 deliberately replaces callback bodies with session/command services.
// Keep the captured stage-2 baseline; exact wire behavior is tested in room-workflow.test.mjs.

function invokeHandler(relative, name, dependencies, args = []) {
  const { source, functions } = nestedFunctions(relative);
  const node = functions[name];
  return new Function(...Object.keys(dependencies), `return (${source.slice(node.start, node.end)});`)(...Object.values(dependencies))(...args);
}
for (const [gameType, defaults] of Object.entries(expectedDefaults)) {
  test(`${gameType}: create/join URLs and JSON body remain unchanged`, async () => {
    const calls = [], navigations = [], submitting = [];
    const dependencies = { gameType, roomId: "123456", password: "pass1", validateCreate: () => true, passwordValid: true,
      ROOM_ID_PATTERN: /^[0-9]{3,8}$/, createGameDefaults: registry.createGameDefaults,
      apiPost: async (...args) => { calls.push(args); return { roomId: "123456" }; },
      navigate: route => navigations.push(route), setSubmitting: value => submitting.push(value), setError: assert.fail };
    await invokeHandler("src/pages/GameLandingPage.jsx", "createRoom", dependencies);
    await invokeHandler("src/pages/GameLandingPage.jsx", "enterRoom", dependencies);
    assert.deepEqual(calls, [["/api/rooms", { gameType, roomId: "123456", password: "pass1", ...defaults }],
      ["/api/rooms/join", { gameType, roomId: "123456", password: "pass1" }]]);
    assert.deepEqual(navigations, [`/${gameType}/123456`, `/${gameType}/123456`]);
    assert.deepEqual(submitting, [true, false, true, false]);
  });
}
test("unified config writer preserves unknown config fields and uses the existing room-specific endpoint", () => {
  const calls = [], config = { unknownExtension: { value: 1 }, thievesExpansion: false };
  const workflow = loadRoomWorkflow();
  const session = { getSnapshot: () => ({ room: { config } }), post: (suffix, body) => calls.push([`/api/rooms/incan_gold/123456${suffix}`, body]) };
  workflow.createRoomCommands(session, { gameType: "incan_gold" }).updateGameConfig("thievesExpansion", true);
  assert.deepEqual(calls, [["/api/rooms/incan_gold/123456/config", { config: { ...config, thievesExpansion: true } }]]);
  assert.equal(config.thievesExpansion, false);
});

test("each registered desktop receives only its existing callback and pending/owner props", () => {
  const input = { ...desktopProps(), actionPending: true, selection: { selectedMove: "stone", setSelectedMove() {} },
    onBrassAction() {}, onIncanAction() {}, onThingsAction() {}, onCamelAction() {} };
  for (const [key, callback] of [["brass", "onBrassAction"], ["incan_gold", "onIncanAction"],
    ["camel_up_cards", "onCamelAction"], ["thingsInRings", "onThingsAction"]]) {
    const props = registry.getGameDefinition(key).desktopProps(input);
    assert.equal(props.onAction, input[callback]);
    assert.equal(props.room, input.room);
    assert.equal(props.currentSeat, input.currentSeat);
    assert.equal(props.seats, input.seats);
    if (key === "incan_gold") assert.equal(props.actionPending, true);
    if (key === "thingsInRings" || key === "camel_up_cards") assert.equal(props.owner, input.owner);
  }
  const rps = registry.getGameDefinition("rps").desktopProps(input);
  assert.equal(rps.onSubmitMove, input.onSubmitMove);
  assert.equal(rps.setSelectedMove, input.selection.setSelectedMove);
  assert.equal(rps.selectedMove, "stone");
});

test("100 mounted rooms of the same game keep selections, round resets and submit callbacks isolated", () => {
  const calls = [];
  const props = Array.from({ length: 100 }, (_, i) => {
    const sample = desktopProps({ round: 1, activePlayers: [1, 2], submissions: {} });
    return { ...sample, room: { ...sample.room, roomId: String(100000 + i) }, gameType: "rps",
      onSubmitMove: move => calls.push({ room: i, move }) };
  });
  const rooms = props.map(item => mountHooks(dispatcher.GameRoom, item));
  const buttons = view => view.all(node => node.type === "button");
  const rock = buttons(rooms[42]).find(node => textOf(node).includes("石头"));
  rooms[42].act(() => rock.props.onClick());
  const submit = view => buttons(view).find(node => node.props.className === "primary-button");
  assert.equal(submit(rooms[42]).props.disabled, false);
  rooms.forEach((view, i) => { if (i !== 42) assert.equal(submit(view).props.disabled, true); });
  rooms[42].act(() => submit(rooms[42]).props.onClick());
  assert.deepEqual(calls, [{ room: 42, move: "stone" }]);
  rooms[42].update({ ...props[42], room: { ...props[42].room, gameState: { ...props[42].room.gameState, round: 2 } } });
  assert.equal(submit(rooms[42]).props.disabled, true);
  rooms.forEach(view => view.unmount());
});
