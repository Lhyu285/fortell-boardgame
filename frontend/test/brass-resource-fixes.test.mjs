import assert from "node:assert/strict";
import { test } from "node:test";
import React from "react";
import fs from "node:fs";
import path from "node:path";
import { createHash } from "node:crypto";
import { parse } from "@babel/parser";
import { loadMigrated, loadBrassDraft, frontendRoot } from "./support/load-desktops.mjs";
import { mountHooks, textOf } from "./support/hook-renderer.mjs";
import { buildDraftProps, networkDraftProps, sellDraftProps, choose, card, select, resource, requirement, button, click } from "./support/brass-draft-fixtures.mjs";

const brass = loadMigrated().brass, draft = loadBrassDraft();
test("only the intended count block and merchant click priority differ from the pre-refactor source baselines", () => {
  const hash = value => createHash("sha256").update(value.replaceAll("\r\n", "\n")).digest("hex");
  const counterSource = fs.readFileSync(path.join(frontendRoot, "src/games/brass/draft/useBrassActionDraft.js"), "utf8").replaceAll("\r\n", "\n");
  const counterAst = parse(counterSource, { sourceType: "module" });
  const owner = counterAst.program.body.find(node => node.type === "FunctionDeclaration");
  const counter = owner.body.body.find(node => node.type === "FunctionDeclaration" && node.id.name === "cycleResourceSource");
  const originalCounterBlock = `const otherSelected = current
        .filter((item) => item.requirementIndex !== requirementIndex && matchesSource(item))
        .reduce((sum, item) => sum + Number(item.amount ?? 0), 0);
      const max = Math.min(Number(source.available ?? 0), Math.max(0,
        Number(requirement.amount ?? 0) - otherSelected));
      const currentAmount`;
  const rawCounter = counterSource.slice(counter.start, counter.end);
  const normalizedCounter = rawCounter.replace(/const occupiedElsewhere = current[\s\S]*?const currentAmount/, originalCounterBlock);
  assert.notEqual(normalizedCounter, rawCounter);
  const draftBaseline = JSON.parse(fs.readFileSync(path.join(frontendRoot, "test/fixtures/brass-desktop-baseline.json"), "utf8"));
  assert.equal(hash(normalizedCounter), draftBaseline.nestedHandlers.cycleResourceSource);
  const mapSource = fs.readFileSync(path.join(frontendRoot, "src/games/brass/components/BrassMap.jsx"), "utf8").replaceAll("\r\n", "\n");
  const mapAst = parse(mapSource, { sourceType: "module", plugins: ["jsx"] });
  const map = mapAst.program.body.find(node => node.type === "FunctionDeclaration" && node.id.name === "BrassMap");
  const rawMap = mapSource.slice(map.start, map.end);
  const normalizedMap = rawMap.replace(`onClick={() => merchantAvailable && !merchantSelected
                  ? onCitySlotClick(slot)
                  : resourceSource
                    ? onResourceSourceClick(resourceSource)
                    : onCitySlotClick(slot)}`, `onClick={() => resourceSource
                  ? onResourceSourceClick(resourceSource)
                  : onCitySlotClick(slot)}`);
  assert.notEqual(normalizedMap, rawMap);
  const desktopBaseline = JSON.parse(fs.readFileSync(path.join(frontendRoot, "test/fixtures/desktop-move-baseline.json"), "utf8"));
  assert.equal(hash(normalizedMap), desktopBaseline.declarations.find(item => item.name === "BrassMap").hash);
});
function probe(props = buildDraftProps()) {
  function Probe(input) {
    const value = draft.useBrassActionDraft({ ...input, onAction() {}, setMapInfo() {} });
    return React.createElement("output", { value });
  }
  const view = mountHooks(Probe, props);
  return { view, get value() { return view.tree.props.value; }, act(callback) { view.act(() => callback(view.tree.props.value)); } };
}
function mountRoom(props) {
  const calls = [];
  props.onAction = (...args) => calls.push(args);
  return { props, calls, view: mountHooks(brass.BrassRoom, props) };
}
function beginNetwork(props = networkDraftProps(true, true)) {
  const room = mountRoom(props);
  choose(room.view, "运输网"); card(room.view);
  select(room.view, 0, "BIRMINGHAM|COVENTRY"); select(room.view, 1, "COVENTRY|NUNEATON");
  return room;
}
function beginSale(props = sellDraftProps()) {
  const room = mountRoom(props);
  choose(room.view, "出售"); card(room.view);
  click(room.view, "伯明翰", node => node.props.className?.startsWith("brass-tile"));
  return room;
}
function marketClick(view, index = 0, allowedOnly = true) {
  const markets = view.all(node => node.type === "g" && node.props.className?.includes("brass-market-slot-overlay")
    && (!allowedOnly || node.props.className.includes("available")));
  assert.ok(markets[index], "expected a map market");
  const rect = view.all(node => node.type === "rect", markets[index])[0];
  view.act(() => rect.props.onClick());
}
function confirmed(callback) {
  const previous = globalThis.window;
  globalThis.window = { confirm: () => true };
  try { callback(); } finally { globalThis.window = previous; }
}

test("double-rail UI submits one stocked mine twice with separate requirement indexes and one beer", () => confirmed(() => {
  const room = beginNetwork();
  resource(room.view, "煤源A"); requirement(room.view, 1); resource(room.view, "煤源A");
  requirement(room.view, 2); resource(room.view, "自家酒");
  click(room.view, "运输网", node => node.props.className === "primary-button");
  assert.deepEqual(room.calls, [["network", { cardId: "card-a", from: "BIRMINGHAM", to: "COVENTRY",
    routes: [{ from: "BIRMINGHAM", to: "COVENTRY" }, { from: "COVENTRY", to: "NUNEATON" }],
    linkIds: ["BIRMINGHAM|COVENTRY", "COVENTRY|NUNEATON"], resourceSelections: [
      { type: "coal", sourceType: "industry", sourceId: "coal-slot", requirementIndex: 0, amount: 1 },
      { type: "coal", sourceType: "industry", sourceId: "coal-slot", requirementIndex: 1, amount: 1 },
      { type: "beer", sourceType: "industry", sourceId: "own-beer", requirementIndex: 2, amount: 1 }
    ] }]]);
}));

test("one remaining free coal is reserved for the first rail before explicit second-rail market purchase", () => confirmed(() => {
  const props = networkDraftProps(true, true);
  for (const route of props.room.gameState.availableActions.networkRoutePairs[0].routes) route.coalSources[0].amount = 1;
  const room = beginNetwork(props);
  assert.equal(button(room.view, "市场煤").props.disabled, true);
  resource(room.view, "煤源A"); requirement(room.view, 1);
  assert.equal(button(room.view, "煤源A", node => textOf(node).includes("0/1")).props.disabled, true);
  resource(room.view, "市场煤"); requirement(room.view, 2); resource(room.view, "自家酒");
  click(room.view, "运输网", node => node.props.className === "primary-button");
  assert.deepEqual(room.calls[0][1].resourceSelections.map(item => [item.sourceType, item.sourceId, item.requirementIndex, item.amount]),
    [["industry", "coal-slot", 0, 1], ["market", "market:coal", 1, 1], ["industry", "own-beer", 2, 1]]);
}));

test("different sources within one requirement share its demand cap and free resources still precede market", () => {
  const props = buildDraftProps();
  props.room.gameState.availableActions.buildOptions[0].coalCost = 3;
  props.room.gameState.availableActions.buildOptions[0].ironCost = 0;
  props.room.gameState.availableActions.buildOptions[0].coalSources.push({ id: "coal-second-slot", ownerName: "煤源B", city: "COVENTRY", amount: 3 });
  const mounted = probe(props);
  mounted.act(value => value.chooseAction("build")); mounted.act(value => value.toggleCard("card-a"));
  const cycle = index => mounted.act(value => value.cycleResourceSource(value.resourceRequirements[0], value.resourceRequirements[0].sources[index]));
  cycle(0);
  for (const amount of [1, 2, 0, 1]) {
    cycle(1);
    assert.equal(mounted.value.resourceSelections.find(item => item.sourceId === "coal-second-slot")?.amount ?? 0, amount);
    assert.ok(mounted.value.resourceSelections.reduce((sum, item) => sum + item.amount, 0) <= 3);
  }
  cycle(0); assert.equal(mounted.value.resourceSelections.find(item => item.sourceId === "coal-slot").amount, 2);
  assert.equal(mounted.value.resourceBlockReason, "");
  const controls = mountHooks(brass.BrassResourceSelectionPanel, { requirements: mounted.value.resourceRequirements, activeIndex: 0,
    selected: mounted.value.resourceSelections, onSelectRequirement() {}, onCycleSource() {}, onClearRequirement() {} });
  assert.equal(button(controls, "市场煤").props.disabled, false);
  cycle(1); // Full demand: selecting more of B cycles it to zero, unlocking demand for A, not overfilling.
  assert.equal(mounted.value.resourceSelections.reduce((sum, item) => sum + item.amount, 0), 2);
});

for (const type of ["coal", "iron", "beer"]) test(`${type}: inventory and demand limits remain independent across a bounded allocation matrix`, () => {
  const mounted = probe(); mounted.act(value => value.chooseAction("build"));
  const source = { id: "shared-slot", sourceType: "industry", type, available: 0 };
  let cases = 0;
  for (let stock = 0; stock <= 3; stock++) for (let demand = 1; demand <= 3; demand++) {
    for (let reserved = 0; reserved <= stock; reserved++) for (let supplied = 0; supplied <= demand; supplied++) {
      const current = [];
      if (reserved) current.push({ type, sourceType: "industry", sourceId: source.id, requirementIndex: 1, amount: reserved });
      if (supplied) current.push({ type, sourceType: "industry", sourceId: "other-slot", requirementIndex: 0, amount: supplied });
      mounted.act(value => value.setResourceSelections(current));
      const cap = Math.min(stock - reserved, demand - supplied);
      for (let clicks = 1; clicks <= cap + 1; clicks++) {
        mounted.act(value => value.cycleResourceSource({ type, amount: demand }, { ...source, available: stock }));
        const selected = mounted.value.resourceSelections.find(item => item.requirementIndex === 0 && item.sourceId === source.id)?.amount ?? 0;
        assert.equal(selected, clicks % (cap + 1));
        assert.ok(selected + reserved <= stock); assert.ok(selected + supplied <= demand);
        assert.deepEqual(mounted.value.resourceSelections.filter(item => item.sourceId !== source.id || item.requirementIndex !== 0), current);
      }
      cases++;
    }
  }
  assert.ok(cases > 80);
});

test("identical IDs of other resource/source types do not reserve this source; over-limit old counts cycle back to zero", () => {
  const mounted = probe(); mounted.act(value => value.chooseAction("build"));
  const untouched = [
    { type: "iron", sourceType: "industry", sourceId: "shared", requirementIndex: 1, amount: 3 },
    { type: "coal", sourceType: "market", sourceId: "shared", requirementIndex: 1, amount: 3 }
  ];
  mounted.act(value => value.setResourceSelections(untouched));
  mounted.act(value => value.cycleResourceSource({ type: "coal", amount: 1 }, { id: "shared", sourceType: "industry", available: 1 }));
  assert.equal(mounted.value.resourceSelections.at(-1).amount, 1);
  mounted.act(value => value.setResourceSelections([{ type: "coal", sourceType: "industry", sourceId: "shared", requirementIndex: 0, amount: 2 },
    { type: "coal", sourceType: "industry", sourceId: "shared", requirementIndex: 1, amount: 1 }]));
  mounted.act(value => value.cycleResourceSource({ type: "coal", amount: 1 }, { id: "shared", sourceType: "industry", available: 2 }));
  assert.deepEqual(mounted.value.resourceSelections, [{ type: "coal", sourceType: "industry", sourceId: "shared", requirementIndex: 1, amount: 1 }]);
});

for (const sourceId of ["own-beer", "other-beer", "merchant-oxford"]) test(`map destination selection reaches an unchanged sell payload using ${sourceId}`, () => confirmed(() => {
  const room = beginSale(); marketClick(room.view);
  assert.ok(textOf(room.view.tree).includes("贸易商酒 0/1"));
  const label = sourceId === "own-beer" ? "自家酒" : sourceId === "other-beer" ? "他人酒" : "贸易商酒";
  if (sourceId === "merchant-oxford") marketClick(room.view);
  else resource(room.view, label);
  click(room.view, "售卖选中产业");
  assert.deepEqual(room.calls, [["sell", { cardId: "card-a", tileId: "sale-0", merchantId: "merchant-oxford",
    resourceSelections: [{ type: "beer", sourceType: sourceId === "merchant-oxford" ? "merchant" : "industry", sourceId, requirementIndex: 0, amount: 1 }],
    freeDevelopIndustryType: undefined }]]);
}));

test("switching allowed merchants selects the new destination, clears old beer and never consumes new beer implicitly", () => confirmed(() => {
  const props = sellDraftProps();
  const other = { ...props.room.gameState.market.beerMerchants[0], id: "merchant-oxford-2", slotIndex: 1 };
  props.room.gameState.market.beerMerchants.push(other);
  for (const tile of props.room.gameState.availableActions.sellTiles) tile.merchantSources.push({ ...tile.merchantSources[0], ...other });
  const room = beginSale(props);
  marketClick(room.view, 0); resource(room.view, "自家酒");
  assert.equal(button(room.view, "售卖选中产业").props.disabled, false);
  marketClick(room.view, 1);
  assert.equal(button(room.view, "售卖选中产业").props.disabled, true);
  assert.ok(textOf(room.view.tree).includes("贸易商酒 0/1"));
  assert.ok(textOf(room.view.tree).includes("自家酒"));
  marketClick(room.view, 1); click(room.view, "售卖选中产业");
  assert.equal(room.calls[0][1].merchantId, "merchant-oxford-2");
  assert.equal(room.calls[0][1].resourceSelections[0].sourceId, "merchant-oxford-2");
}));

test("a valid merchant with no beer remains selectable for sale using brewery beer", () => confirmed(() => {
  const props = sellDraftProps();
  props.room.gameState.market.beerMerchants[0].beer = 0;
  props.room.gameState.availableActions.sellTiles.forEach(tile => { tile.merchantSources[0].beer = 0; });
  const room = beginSale(props); marketClick(room.view);
  assert.ok(!textOf(room.view.tree).includes("请在地图上选择售卖市场"));
  assert.equal(button(room.view, "贸易商酒").props.disabled, true);
  resource(room.view, "他人酒"); click(room.view, "售卖选中产业");
  assert.equal(room.calls[0][1].merchantId, "merchant-oxford"); assert.equal(room.calls[0][1].resourceSelections[0].sourceId, "other-beer");
}));

test("zero-beer-cost sale needs only an explicit destination, not an invented resource selection", () => confirmed(() => {
  const props = sellDraftProps(); props.room.gameState.availableActions.sellTiles.forEach(tile => { tile.saleBeerCost = 0; });
  const room = beginSale(props); marketClick(room.view);
  click(room.view, "售卖选中产业");
  assert.equal(room.calls[0][1].merchantId, "merchant-oxford"); assert.equal("resourceSelections" in room.calls[0][1], false);
  marketClick(room.view);
  assert.equal(button(room.view, "售卖选中产业").props.disabled, true, "without a beer requirement, repeat click still deselects the destination");
}));

test("clicking an unrelated/nonpermitted market does not replace the selected destination or erase chosen beer", () => confirmed(() => {
  const room = beginSale(); marketClick(room.view); resource(room.view, "自家酒");
  const blocked = room.view.all(node => node.type === "g" && node.props.className?.includes("brass-market-slot-overlay") && !node.props.className.includes("available"))[0];
  assert.ok(blocked); room.view.act(() => room.view.all(node => node.type === "rect", blocked)[0].props.onClick());
  click(room.view, "售卖选中产业");
  assert.equal(room.calls[0][1].merchantId, "merchant-oxford"); assert.equal(room.calls[0][1].resourceSelections[0].sourceId, "own-beer");
}));

test("changing the sale tile clears destination and beer until a permitted destination is selected again", () => {
  const room = beginSale(); marketClick(room.view); resource(room.view, "自家酒");
  const tiles = room.view.all(node => node.type === "button" && node.props.className?.startsWith("brass-tile"));
  room.view.act(() => tiles[1].props.onClick());
  assert.equal(button(room.view, "售卖选中产业").props.disabled, true);
  assert.ok(textOf(room.view.tree).includes("请在地图上选择售卖市场"));
  marketClick(room.view);
  assert.ok(textOf(room.view.tree).includes("贸易商酒 0/1")); assert.equal(button(room.view, "售卖选中产业").props.disabled, true);
});

test("one room's merchant choice and shared-mine allocation never change a second room", () => {
  const first = beginSale(), second = beginSale();
  marketClick(first.view); resource(first.view, "自家酒");
  assert.equal(button(first.view, "售卖选中产业").props.disabled, false);
  assert.ok(textOf(second.view.tree).includes("请在地图上选择售卖市场"));
  assert.equal(button(second.view, "售卖选中产业").props.disabled, true);
  const a = beginNetwork(), b = beginNetwork();
  resource(a.view, "煤源A"); requirement(a.view, 1); resource(a.view, "煤源A");
  assert.ok(textOf(b.view.tree).includes("煤源A BIRMINGHAM 煤矿场 0/2"));
  assert.deepEqual(b.calls, []);
});
