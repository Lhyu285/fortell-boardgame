import assert from "node:assert/strict";
import { test } from "node:test";
import { loadMigrated, loadDispatcher } from "./support/load-desktops.mjs";
import { desktopProps, brassProps, incanProps, thingsProps, camelProps } from "./support/desktop-fixtures.mjs";
import { mountHooks, textOf } from "./support/hook-renderer.mjs";

const modules = loadMigrated(), dispatcher = await loadDispatcher();
globalThis.window = { confirm: () => true };
function button(view, label) {
  const found = view.all(node => node.type === "button" && textOf(node).includes(label));
  assert.equal(found.length, 1, `Expected one button containing ${label}`);
  return found[0];
}
function click(view, label) {
  const target = button(view, label); assert.ok(!target.props.disabled, `${label} is disabled`);
  view.act(() => target.props.onClick());
}

test("Incan decision choices keep exact action names and payloads", () => {
  const props = incanProps(); const calls = []; props.onAction = (...args) => calls.push(args);
  const view = mountHooks(modules.incan.IncanGoldRoom, props);
  click(view, "继续探险"); click(view, "结束探险"); click(view, "盗窃");
  assert.deepEqual(calls, [["choose_expedition_action", { choice: "continue" }], ["choose_expedition_action", { choice: "return" }],
    ["choose_expedition_action", { choice: "steal" }]]);
});
test("Incan automatic draw remains version-gated and only the designated active player sends it", () => {
  const props = incanProps("EXPLORATION", { lastDecision: { choices: { "1": "return", "2": "continue" } } });
  const calls = []; props.onAction = (...args) => calls.push(args);
  const view = mountHooks(modules.incan.IncanGoldRoom, props);
  assert.deepEqual(calls, [["draw_exploration_card"]]);
  view.update({ ...props }); assert.equal(calls.length, 1);
  view.update({ ...props, room: { ...props.room, gameState: { ...props.room.gameState, version: 11 } } });
  assert.equal(calls.length, 2);
  const spectator = incanProps("EXPLORATION", props.room.gameState, true); spectator.onAction = (...args) => calls.push(args);
  mountHooks(modules.incan.IncanGoldRoom, spectator); assert.equal(calls.length, 2);
});
test("Incan pending/submitted decisions remain disabled and expansion controls stay conditional", () => {
  const props = incanProps("DECISION", { pendingDecisions: { "1": true } });
  const view = mountHooks(modules.incan.IncanGoldRoom, props);
  assert.equal(button(view, "继续探险").props.disabled, true);
  view.update({ ...props, room: { ...props.room, config: { thievesExpansion: false } } });
  assert.equal(view.all(node => node.type === "button" && textOf(node).includes("盗窃")).length, 0);
});
test("Incan thief target selection still submits the chosen numeric user ID", () => {
  const props = incanProps("THEFT_TARGETS", { thieves: [1], targetCandidates: [2] }); const calls = [];
  props.onAction = (...args) => calls.push(args); const view = mountHooks(modules.incan.IncanGoldRoom, props);
  const select = view.all(node => node.type === "select")[0];
  view.act(() => select.props.onChange({ target: { value: "2" } }));
  const submit = view.all(node => node.type === "button" && node.props.onClick && /目标/.test(textOf(node))).at(-1);
  assert.ok(submit && !submit.props.disabled); view.act(() => submit.props.onClick());
  assert.deepEqual(calls, [["submit_theft_target", { targetUserId: 2 }]]);
});
test("Things pending-guess host judgment remains usable and preserves its payload", () => {
  const props = thingsProps("GUESSER_TURN", { hostPlayerId: 1,
    pendingGuess: { playerName: "B", word: { id: "guess", Chinese: "电脑", English: "computer" }, area: "SCENE" } });
  const calls = []; props.onAction = (...args) => calls.push(args);
  const view = mountHooks(modules.things.ThingsInRingsRoom, props);
  assert.ok(textOf(view.tree).includes("电脑/computer"));
  const correct = view.all(node => node.type === "button" && textOf(node) === "是")[0];
  assert.ok(correct); view.act(() => correct.props.onClick());
  assert.deepEqual(calls, [["host_judge", { correct: true }]]);
});
test("Things area modal shows full history while preview and placement retain their behavior", () => {
  const areas = modules.thingsHelpers.THINGS_AREAS;
  const placedWords = { SCENE: [{ id: "old", Chinese: "雨", English: "rain" }, { id: "new", Chinese: "相机", English: "camera" }] };
  const calls = [];
  const view = mountHooks(modules.things.ThingsAreaBoard, { placedWords, selectedWord: { id: "selected" }, canPlace: true,
    onPlace: area => calls.push(area) });
  const place = view.all(node => node.type === "button" && textOf(node) === "放入这里");
  assert.equal(place.length, areas.length);
  view.act(() => place[0].props.onClick()); assert.deepEqual(calls, ["SCENE"]);
  click(view, "查看全部"); assert.ok(textOf(view.tree).includes("雨/rain")); assert.ok(textOf(view.tree).includes("相机/camera"));
  click(view, "关闭"); assert.equal(view.all(node => node.props.role === "dialog").length, 0);
});
test("Camel track action and skip bet retain their exact request payloads", () => {
  const props = camelProps(); const calls = []; props.onAction = (...args) => calls.push(args);
  const view = mountHooks(modules.camel.CamelUpCardsRoom, props);
  click(view, "翻开竞赛牌"); assert.deepEqual(calls, [["draw_race_card", {}]]);
  view.update({ ...props, room: { ...props.room, gameState: { ...props.room.gameState, turn: { trackActionDone: true, betActionDone: false } } } });
  click(view, "跳过下注"); assert.deepEqual(calls.at(-1), ["skip_bet", {}]);
});
test("RPS selection resets on round changes and uses the existing submit-move callback", () => {
  const calls = []; const props = { ...desktopProps({ phase: "playing", round: 1, activePlayers: [1, 2] }, { moveSet: 3 }),
    gameType: "rps", onSubmitMove: move => calls.push(move) };
  const view = mountHooks(dispatcher.GameRoom, props);
  click(view, "石头"); click(view, "确定"); assert.deepEqual(calls, ["stone"]);
  view.update({ ...props, room: { ...props.room, gameState: { ...props.room.gameState, round: 2 } } });
  assert.equal(button(view, "确定").props.disabled, true);
});
test("Brass utility action still confirms and submits without changing its wire payload", () => {
  const props = brassProps({ availableActions: { actions: ["restart_turn", "end_turn"], resourceSources: {} } });
  const calls = []; props.onAction = (...args) => calls.push(args);
  const view = mountHooks(modules.brass.BrassRoom, props);
  click(view, "重新开始回合"); click(view, "结束回合");
  assert.deepEqual(calls, [["restart_turn", {}], ["end_turn", {}]]);
});
test("Brass resource-selection controls keep exact source keys and requirement indexes", () => {
  const selected = [], calls = [];
  const requirements = [{ type: "coal", amount: 2, label: "煤", freeAvailable: 2, marketAvailable: 2, sources: [
    { id: "coal-tile-1", type: "coal", sourceType: "industry", available: 2, label: "免费煤" },
    { id: "market:coal", type: "coal", sourceType: "market", available: 2, label: "市场煤" }
  ] }];
  const view = mountHooks(modules.brass.BrassResourceSelectionPanel, { requirements, activeIndex: 0, selected,
    onSelectRequirement: index => calls.push(["requirement", index]), onCycleSource: (...args) => calls.push(["source", ...args]), onClearRequirement() {} });
  const free = view.all(node => node.type === "button" && textOf(node).includes("免费煤"))[0];
  assert.ok(free && !free.props.disabled); view.act(() => free.props.onClick());
  assert.equal(calls.at(-1)[0], "source"); assert.equal(calls.at(-1)[1], requirements[0]);
  assert.equal(calls.at(-1)[2].id, "coal-tile-1");
  const market = view.all(node => node.type === "button" && textOf(node).includes("市场煤"))[0]; assert.equal(market.props.disabled, true);
});

test("Brass source counts still cycle through stock limits and submit explicit selections", () => {
  const source = { id: "coal-existing-slot", ownerName: "矿场A", city: "Dudley", amount: 3 };
  const option = { city: "Birmingham", industryType: "cotton_mill", slotIndex: 0, level: 2, cost: 5,
    coalCost: 3, ironCost: 0, cardIds: ["card-a"], coalSources: [source], ironSources: [] };
  const props = brassProps({ board: { industries: [], links: [], availableRoutes: [], citySlots: { Birmingham: [["cotton_mill"]] } },
    availableActions: { actions: ["build"], buildOptions: [option], resourceSources: {} } });
  const calls = []; props.onAction = (...args) => calls.push(args);
  const view = mountHooks(modules.brass.BrassRoom, props);
  const action = view.all(node => node.type === "button" && node.props.className?.includes("brass-action-choice") && textOf(node) === "建造")[0];
  view.act(() => action.props.onClick());
  const card = view.all(node => node.type === "button" && /^brass-card(?: |$)/.test(node.props.className ?? ""))[0];
  assert.ok(card); view.act(() => card.props.onClick());
  assert.ok(textOf(button(view, "矿场A")).includes("0/3"));
  for (let count = 1; count <= 4; count++) {
    click(view, "矿场A"); assert.ok(textOf(button(view, "矿场A")).includes(`${count % 4}/3`));
  }
  for (let count = 0; count < 3; count++) click(view, "矿场A");
  const submit = view.all(node => node.type === "button" && node.props.className === "primary-button" && textOf(node) === "建造")[0];
  assert.ok(submit && !submit.props.disabled); view.act(() => submit.props.onClick());
  assert.deepEqual(calls, [["build", { city: "Birmingham", industryType: "cotton_mill", slotIndex: 0, cardId: "card-a",
    resourceSelections: [{ type: "coal", sourceType: "industry", sourceId: "coal-existing-slot", requirementIndex: 0, amount: 3 }] }]]);
  view.update({ ...props, room: { ...props.room, gameState: { ...props.room.gameState, turn: { actionsTaken: 1, actionsRemaining: 1 } } } });
  assert.equal(view.all(node => node.props.className === "brass-resource-panel").length, 0);
});

test("Canceling confirmation still sends no Brass or Camel action", () => {
  const calls = [], props = brassProps({ availableActions: { actions: ["end_turn"], resourceSources: {} } });
  props.onAction = (...args) => calls.push(args);
  const view = mountHooks(modules.brass.BrassRoom, props);
  window.confirm = () => false;
  try {
    click(view, "结束回合");
    const camel = camelProps(); camel.onAction = props.onAction;
    click(mountHooks(modules.camel.CamelUpCardsRoom, camel), "翻开竞赛牌");
    assert.deepEqual(calls, []);
  } finally { window.confirm = () => true; }
});
