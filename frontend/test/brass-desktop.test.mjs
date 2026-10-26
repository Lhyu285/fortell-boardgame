import assert from "node:assert/strict";
import { test } from "node:test";
import fs from "node:fs";
import path from "node:path";
import { createHash } from "node:crypto";
import { parse } from "@babel/parser";
import React from "react";
import { frontendRoot, loadMigrated, loadBrassDraft } from "./support/load-desktops.mjs";
import { mountHooks, textOf } from "./support/hook-renderer.mjs";
import { buildDraftProps, networkDraftProps, sellDraftProps, draftScenarios, runDraftScenario,
  choose, card, select, resource, requirement, button, click } from "./support/brass-draft-fixtures.mjs";

const brass = loadMigrated().brass, draft = loadBrassDraft();
const baseline = JSON.parse(fs.readFileSync(path.join(frontendRoot, "test/fixtures/brass-desktop-baseline.json"), "utf8"));
const hash = value => createHash("sha256").update(value.replaceAll("\r\n", "\n")).digest("hex");
function read(relative) {
  const source = fs.readFileSync(path.join(frontendRoot, relative), "utf8");
  const ast = parse(source, { sourceType: "module", plugins: ["jsx"] });
  const declarations = new Map(ast.program.body.map(node => node.declaration ?? node)
    .filter(node => node.type === "FunctionDeclaration").map(node => [node.id.name, node]));
  return { source, ast, declarations };
}
const hook = read("src/games/brass/draft/useBrassActionDraft.js"), model = read("src/games/brass/draft/buildBrassActionModel.js"), entry = read("src/games/brass/BrassRoom.jsx");
test("all draft handlers except the explicitly repaired resource counter remain mechanically preserved", () => {
  const nodes = hook.declarations.get("useBrassActionDraft").body.body.filter(node => node.type === "FunctionDeclaration");
  const retained = Object.fromEntries(Object.entries(baseline.nestedHandlers).filter(([name]) => name !== "cycleResourceSource"));
  assert.deepEqual(Object.fromEntries(nodes.filter(node => node.id.name !== "cycleResourceSource")
    .map(node => [node.id.name, hash(hook.source.slice(node.start, node.end))])), retained);
});
test("original draft/UI state defaults and normalization effects keep their exact code and order", () => {
  const states = (document, name) => document.declarations.get(name).body.body
    .filter(node => node.type === "VariableDeclaration" && node.declarations[0].init?.callee?.name === "useState")
    .map(node => hash(document.source.slice(node.start, node.end)));
  assert.deepEqual([...states(hook, "useBrassActionDraft"), ...states(entry, "BrassRoom")], baseline.states);
  assert.deepEqual(hook.declarations.get("useBrassActionDraft").body.body.filter(node => node.type === "ExpressionStatement")
    .map(node => hash(hook.source.slice(node.start, node.end))), baseline.effects);
});
test("derived action model preserves the original candidate, resource, validation and map calculations", () => {
  const body = model.declarations.get("buildBrassActionModel").body.body;
  assert.deepEqual(body.slice(1, -1).map(node => hash(model.source.slice(node.start, node.end))), baseline.modelStatements);
});
for (const [name, expected] of Object.entries(baseline.extractedJSX)) test(`original action control markup and inline payload handlers: ${name}`, () => {
  const file = read(baseline.locations[name]);
  const jsx = file.declarations.get(name).body.body[0].argument;
  assert.equal(hash(file.source.slice(jsx.start, jsx.end)), expected);
});
for (const [relative, expected] of Object.entries(baseline.protected)) test(`unchanged refactor boundary: ${relative}`, () => {
  assert.equal(hash(fs.readFileSync(path.join(frontendRoot, relative), "utf8")), expected);
});
for (const scenario of draftScenarios()) test(`pre-split interactive rendering, confirmation and action trace: ${scenario.name}`, () => {
  const frames = [];
  runDraftScenario(brass.BrassRoom, scenario, frame => frames.push(hash(JSON.stringify(frame))));
  assert.deepEqual(frames, baseline.scenarios[scenario.name]);
});

function mountDraft(props) {
  const calls = [], infos = [];
  function Probe(input) {
    const value = draft.useBrassActionDraft({ ...input, onAction: (...args) => calls.push(args), setMapInfo: value => infos.push(value) });
    return React.createElement("output", { value });
  }
  const view = mountHooks(Probe, props);
  return { view, calls, infos, get value() { return view.tree.props.value; }, act(callback) { view.act(() => callback(view.tree.props.value)); } };
}

test("derived candidate queries are pure and retain authoritative empty sources and stage-specific double rail options", () => {
  const props = networkDraftProps();
  const state = { selectedCards: ["card-a"], selectedTileId: "", buildCity: "BIRMINGHAM", buildIndustry: "cotton_mill", buildSlotIndex: "",
    networkRoute: "BIRMINGHAM|COVENTRY", secondNetworkRoute: "COVENTRY|NUNEATON", selectedMerchantId: "", resourceSelections: [],
    resourceRequirementIndex: 0, developIndustries: [], activeAction: "network" };
  const before = JSON.stringify({ props, state });
  const result = draft.buildBrassActionModel({ room: props.room, currentSeat: props.currentSeat, draft: state });
  assert.deepEqual(result.resourceRequirements.map(item => item.type), ["coal", "coal", "beer"]);
  assert.equal(result.resourceRequirements[0].sources[0].id, "coal-slot");
  assert.equal(result.resourceRequirements[1].sources[0].id, "coal-second-slot");
  assert.equal(result.resourceRequirements[2].sources[0].id, "own-beer");
  assert.equal(JSON.stringify({ props, state }), before);
});

test("snapshot refresh without a completed action preserves a valid draft; turn/action/round changes reset it", () => {
  const props = buildDraftProps(); const mounted = mountDraft(props);
  mounted.act(value => value.chooseAction("build")); mounted.act(value => value.toggleCard("card-a"));
  mounted.act(value => value.cycleResourceSource(value.resourceRequirements[0], value.resourceRequirements[0].sources[0]));
  mounted.view.update({ ...props, room: { ...props.room, gameState: { ...props.room.gameState, version: 11 } } });
  assert.equal(mounted.value.activeAction, "build"); assert.deepEqual(mounted.value.selectedCards, ["card-a"]);
  assert.equal(mounted.value.resourceSelections.length, 1);
  for (const patch of [{ round: 3 }, { currentPlayerId: 2 }, { turn: { actionsTaken: 1, actionsRemaining: 1 } }]) {
    mounted.view.update(props); mounted.act(value => value.chooseAction("build"));
    mounted.act(value => value.toggleCard("card-a"));
    mounted.view.update({ ...props, room: { ...props.room, gameState: { ...props.room.gameState, ...patch } } });
    assert.equal(mounted.value.activeAction, ""); assert.deepEqual(mounted.value.selectedCards, []);
    assert.deepEqual(mounted.value.resourceSelections, []); assert.deepEqual(mounted.value.developIndustries, []);
  }
});

test("resource availability or action changes clear selections and reset the active requirement", () => {
  const props = buildDraftProps(); const mounted = mountDraft(props);
  mounted.act(value => value.chooseAction("build")); mounted.act(value => value.toggleCard("card-a"));
  mounted.act(value => value.setResourceRequirementIndex(1));
  mounted.act(value => value.cycleResourceSource(value.resourceRequirements[1], value.resourceRequirements[1].sources[0]));
  assert.equal(mounted.value.resourceSelections[0].requirementIndex, 1);
  const options = props.room.gameState.availableActions;
  mounted.view.update({ ...props, room: { ...props.room, gameState: { ...props.room.gameState, availableActions: { ...options,
    buildOptions: options.buildOptions.map(option => ({ ...option, ironSources: [] })) } } } });
  assert.deepEqual(mounted.value.resourceSelections, []); assert.equal(mounted.value.resourceRequirementIndex, 0);
  assert.deepEqual(mounted.value.buildIronSources, []);
  mounted.act(value => value.chooseAction("loan")); assert.deepEqual(mounted.value.resourceRequirements, []);
});

test("double rail can allocate two units from the same stocked mine to different requirements", () => {
  const props = networkDraftProps(true, true); const mounted = mountDraft(props);
  mounted.act(value => value.chooseAction("network")); mounted.act(value => value.toggleCard("card-a"));
  mounted.act(value => value.setNetworkRoute("BIRMINGHAM|COVENTRY"));
  mounted.act(value => value.setSecondNetworkRoute("COVENTRY|NUNEATON"));
  mounted.act(value => value.cycleResourceSource(value.resourceRequirements[0], value.resourceRequirements[0].sources[0]));
  mounted.act(value => value.setResourceRequirementIndex(1));
  mounted.act(value => value.cycleResourceSource(value.resourceRequirements[1], value.resourceRequirements[1].sources[0]));
  assert.deepEqual(mounted.value.resourceSelections.filter(item => item.type === "coal"), [
    { type: "coal", sourceType: "industry", sourceId: "coal-slot", requirementIndex: 0, amount: 1 },
    { type: "coal", sourceType: "industry", sourceId: "coal-slot", requirementIndex: 1, amount: 1 }
  ]);
  mounted.act(value => value.setResourceRequirementIndex(2));
  mounted.act(value => value.cycleResourceSource(value.resourceRequirements[2], value.resourceRequirements[2].sources[0]));
  assert.equal(mounted.value.networkBlockReason, "");
});

test("the first permitted merchant map click sets the sale destination without consuming its beer", () => {
  const props = sellDraftProps(); const view = mountHooks(brass.BrassRoom, props);
  choose(view, "出售"); card(view);
  click(view, "伯明翰", node => node.props.className?.startsWith("brass-tile"));
  const market = view.all(node => node.type === "g" && node.props.className?.includes("brass-market-slot-overlay") && node.props.className.includes("available"))[0];
  assert.ok(market, "a permitted market is highlighted");
  const target = view.all(node => node.type === "rect", market)[0];
  view.act(() => target.props.onClick());
  assert.equal(button(view, "售卖选中产业").props.disabled, true);
  assert.ok(!textOf(view.tree).includes("请在地图上选择售卖市场"));
  assert.ok(textOf(view.tree).includes("自家酒"));
  assert.ok(textOf(view.tree).includes("贸易商酒 0/1"));
  const selectedMarket = view.all(node => node.type === "g" && node.props.className?.includes("brass-market-slot-overlay") && node.props.className.includes("selected"))[0];
  view.act(() => view.all(node => node.type === "rect", selectedMarket)[0].props.onClick());
  assert.equal(button(view, "售卖选中产业").props.disabled, false);
  assert.ok(textOf(view.tree).includes("贸易商酒 1/1"));
});

test("remaining resource UI behavior retains integer cycling, zero/disabled handling and independent requirement clearing", () => {
  const mounted = mountDraft(buildDraftProps());
  mounted.act(value => value.chooseAction("build")); mounted.act(value => value.toggleCard("card-a"));
  const source = () => mounted.value.resourceRequirements[0].sources[0];
  for (const amount of [1, 2, 0]) {
    mounted.act(value => value.cycleResourceSource(value.resourceRequirements[0], source()));
    assert.equal(mounted.value.resourceSelections[0]?.amount ?? 0, amount);
  }
  mounted.act(value => value.cycleResourceSource(value.resourceRequirements[0], { ...source(), disabled: true }));
  mounted.act(value => value.cycleResourceSource(null, source()));
  mounted.act(value => value.cycleResourceSource(value.resourceRequirements[0], { ...source(), available: 0 }));
  assert.deepEqual(mounted.value.resourceSelections, []);
  mounted.act(value => value.cycleResourceSource(value.resourceRequirements[0], source()));
  mounted.act(value => value.setResourceRequirementIndex(1));
  mounted.act(value => value.cycleResourceSource(value.resourceRequirements[1], value.resourceRequirements[1].sources[0]));
  mounted.act(value => value.clearResourceRequirement(0));
  assert.equal(mounted.value.resourceSelections.length, 1); assert.equal(mounted.value.resourceSelections[0].requirementIndex, 1);
});

test("100 independent Brass draft instances do not share cards, resource counts, action changes or reset state", () => {
  const props = Array.from({ length: 100 }, (_, index) => {
    const input = buildDraftProps(); input.room.roomId = String(100000 + index); return input;
  });
  const rooms = props.map(mountDraft);
  try {
    rooms[42].act(value => value.chooseAction("build")); rooms[42].act(value => value.toggleCard("card-a"));
    rooms[42].act(value => value.cycleResourceSource(value.resourceRequirements[0], value.resourceRequirements[0].sources[0]));
    rooms[72].act(value => value.chooseAction("scout")); rooms[72].act(value => value.toggleCard("card-a"));
    for (const [index, room] of rooms.entries()) {
      assert.deepEqual(room.value.selectedCards, [42, 72].includes(index) ? ["card-a"] : []);
      assert.equal(room.value.resourceSelections.length, index === 42 ? 1 : 0);
      assert.equal(room.value.activeAction, index === 42 ? "build" : index === 72 ? "scout" : "");
    }
    rooms[42].view.update({ ...props[42], room: { ...props[42].room, gameState: { ...props[42].room.gameState, round: 3 } } });
    assert.deepEqual(rooms[42].value.selectedCards, []); assert.deepEqual(rooms[42].value.resourceSelections, []);
    assert.deepEqual(rooms[72].value.selectedCards, ["card-a"]); assert.equal(rooms[72].value.activeAction, "scout");
  } finally { rooms.forEach(room => room.view.unmount()); }
});

test("canceled or failed submissions retain the draft, and adjacent completed develop actions start clean", () => {
  const props = buildDraftProps(); const mounted = mountDraft(props);
  const previousWindow = globalThis.window; globalThis.window = { confirm: () => false };
  try {
    mounted.act(value => value.chooseAction("develop")); mounted.act(value => value.toggleCard("card-a"));
    mounted.act(value => value.toggleDevelopIndustry("cotton_mill"));
    mounted.act(value => value.cycleResourceSource(value.resourceRequirements[0], value.resourceRequirements[0].sources[0]));
    mounted.act(value => value.confirmed("confirm", "develop", { cardId: "card-a" }));
    assert.deepEqual(mounted.calls, []);
    mounted.view.update({ ...props }); // An unsuccessful request produces no completed-action snapshot.
    assert.deepEqual(mounted.value.developIndustries, ["cotton_mill"]);
    assert.equal(mounted.value.resourceSelections.length, 1);
    for (const actionsTaken of [1, 2]) {
      mounted.view.update({ ...props, room: { ...props.room, gameState: { ...props.room.gameState, turn: { actionsTaken, actionsRemaining: 2 - actionsTaken } } } });
      assert.deepEqual(mounted.value.selectedCards, []); assert.deepEqual(mounted.value.developIndustries, []);
      assert.deepEqual(mounted.value.resourceSelections, []);
      if (actionsTaken === 1) { mounted.act(value => value.chooseAction("develop")); mounted.act(value => value.toggleDevelopIndustry("cotton_mill")); }
    }
  } finally { globalThis.window = previousWindow; }
});

for (const sourceId of ["own-beer", "other-beer", "merchant-oxford"]) test(`sell payload from a selected merchant/draft retains beer source and target identity: ${sourceId}`, () => {
  const props = sellDraftProps(); const mounted = mountDraft(props);
  const previousWindow = globalThis.window; globalThis.window = { confirm: () => true };
  try {
    mounted.act(value => value.chooseAction("sell")); mounted.act(value => value.toggleCard("card-a"));
    mounted.act(value => value.setSelectedTileId("sale-0"));
    mounted.act(value => value.setSelectedMerchantId("merchant-oxford"));
    assert.deepEqual(mounted.value.resourceRequirements[0].sources.map(item => item.id), ["own-beer", "other-beer", "merchant-oxford"]);
    mounted.act(value => value.cycleResourceSource(value.resourceRequirements[0], value.resourceRequirements[0].sources.find(item => item.id === sourceId)));
    assert.equal(mounted.value.sellBlockReason, "");
    const controls = mountHooks(draft.BrassControlPanel, mounted.value);
    click(controls, "售卖选中产业");
    const expectedSourceType = sourceId.startsWith("merchant") ? "merchant" : "industry";
    assert.deepEqual(mounted.calls, [["sell", { cardId: "card-a", tileId: "sale-0", merchantId: "merchant-oxford",
      resourceSelections: [{ type: "beer", sourceType: expectedSourceType, sourceId, requirementIndex: 0, amount: 1 }], freeDevelopIndustryType: undefined }]]);
  } finally { globalThis.window = previousWindow; }
});

test("continuing a sale does not require a second action card; end_sell retains its separate payload", () => {
  const props = sellDraftProps(true); const mounted = mountDraft(props);
  const previousWindow = globalThis.window; globalThis.window = { confirm: () => true };
  try {
    mounted.act(value => value.chooseAction("sell")); mounted.act(value => value.setSelectedTileId("sale-1"));
    mounted.act(value => value.setSelectedMerchantId("merchant-oxford"));
    mounted.act(value => value.cycleResourceSource(value.resourceRequirements[0], value.resourceRequirements[0].sources[0]));
    const controls = mountHooks(draft.BrassControlPanel, mounted.value);
    click(controls, "售卖选中产业"); click(controls, "结束售卖行动");
    assert.equal(mounted.calls[0][1].cardId, undefined);
    assert.equal(mounted.calls[0][1].tileId, "sale-1"); assert.deepEqual(mounted.calls[1], ["end_sell", {}]);
    const updated = { ...props, room: { ...props.room, gameState: { ...props.room.gameState,
      availableActions: { ...props.room.gameState.availableActions, sellTiles: [props.room.gameState.availableActions.sellTiles[0]] } } } };
    mounted.view.update(updated);
    assert.equal(mounted.value.selectedTileId, ""); assert.equal(mounted.value.selectedMerchantId, "");
    assert.deepEqual(mounted.value.resourceSelections, []);
  } finally { globalThis.window = previousWindow; }
});

test("mobile personal-board information and document listeners remain isolated and clean up on unmount", () => {
  const listeners = new Set(), originalWindow = globalThis.window, originalDocument = globalThis.document;
  globalThis.window = { matchMedia: () => ({ matches: true }) };
  globalThis.document = { addEventListener: (type, callback) => listeners.add(callback), removeEventListener: (type, callback) => listeners.delete(callback) };
  const props = buildDraftProps(); const players = props.room.gameState.players;
  const board = { ...props.room.gameState.playerBoards, "1": { cotton_mill: [{ id: "personal-1", industryType: "cotton_mill", level: 1, canDevelop: true }] } };
  const mount = () => mountHooks(draft.BrassPlayerBoards, { players, playerBoards: board, stats: {}, hands: {}, currentPlayerId: "1", currentUserId: "1",
    initialTurnOrder: [1, 2], turnOrder: [1, 2], activeAction: "", selectedIndustries: [], onDevelopTile: assert.fail });
  const first = mount(), second = mount();
  try {
    for (const view of [first, second]) {
      const tile = view.all(node => node.props["data-brass-board-tile-touch"])[0]; assert.ok(tile);
      view.act(() => tile.props.onPointerDown({ preventDefault() {}, stopPropagation() {} }));
      assert.equal(view.all(node => node.props.role === "status").length, 1);
    }
    assert.equal(listeners.size, 2);
    first.unmount(); assert.equal(listeners.size, 1);
    assert.equal(second.all(node => node.props.role === "status").length, 1);
    second.act(() => [...listeners][0]({ target: { closest: () => false } }));
    assert.equal(listeners.size, 0); assert.equal(second.all(node => node.props.role === "status").length, 0);
  } finally { second.unmount(); globalThis.window = originalWindow; globalThis.document = originalDocument; }
});
