import assert from "node:assert/strict";
import { brassProps } from "./desktop-fixtures.mjs";
import { mountHooks, textOf } from "./hook-renderer.mjs";

export function buildDraftProps() {
  const option = { city: "BIRMINGHAM", industryType: "cotton_mill", slotIndex: 0, level: 2, cost: 5,
    coalCost: 2, ironCost: 1, cardIds: ["card-a"],
    coalSources: [{ id: "coal-slot", ownerName: "煤源A", city: "DUDLEY", amount: 3 }],
    ironSources: [{ id: "iron-slot", ownerName: "铁源A", city: "COVENTRY", amount: 3 }] };
  return brassProps({ board: { industries: [], links: [], availableRoutes: [], citySlots: { BIRMINGHAM: [["cotton_mill"]] } },
    availableActions: { actions: ["build", "develop", "loan", "scout", "skip", "restart_turn", "end_turn", "maintain_era"],
      buildOptions: [option], resourceSources: {}, developIndustries: [{ industryType: "cotton_mill", ironSources: option.ironSources }] } });
}
export function networkDraftProps(double = true, shared = false) {
  const coal = { id: "coal-slot", ownerName: "煤源A", city: "BIRMINGHAM", amount: 2 };
  const first = { from: "BIRMINGHAM", to: "COVENTRY", coalSources: [coal] };
  const second = { from: "COVENTRY", to: "NUNEATON", coalSources: shared ? [coal] : [{ id: "coal-second-slot", ownerName: "煤源B", city: "COVENTRY", amount: 1 }] };
  return brassProps({ era: double ? "rail" : "canal", board: { industries: [], links: [], availableRoutes: [first, second] },
    availableActions: { actions: ["network"], networkRoutes: [first, second], networkRoutePairs: [{ routes: [first, second],
      beerSources: [{ id: "own-beer", ownerName: "自家酒", city: "WORCESTER", amount: 1 }] }], resourceSources: {} } });
}
export function sellDraftProps(inProgress = false) {
  const merchants = [{ id: "merchant-oxford", city: "OXFORD", blank: false, marketOpen: true, beer: 1, reward: "Income", slotIndex: 0 }];
  const beerSources = [{ id: "own-beer", ownerName: "自家酒", city: "WORCESTER", amount: 1 },
    { id: "other-beer", ownerName: "他人酒", city: "BIRMINGHAM", amount: 1 }];
  const tiles = [0, 1].map(index => ({ id: `sale-${index}`, ownerId: 1, ownerName: "A", city: "BIRMINGHAM", slotIndex: index,
    industryType: "cotton_mill", level: 1, flipped: false, saleBeerCost: 1, merchantSources: [{ ...merchants[0], beerSources }] }));
  return brassProps({ turn: { actionsTaken: 0, actionsRemaining: 2, sellInProgress: inProgress, sellCount: inProgress ? 1 : 0 },
    board: { industries: tiles, links: [], availableRoutes: [] }, market: { coal: [], iron: [], beerMerchants: merchants },
    availableActions: { actions: ["sell", "end_sell"], sellTiles: tiles, sellInProgress: inProgress, sellCount: inProgress ? 1 : 0, resourceSources: {} } });
}
export function button(view, label, predicate = () => true) {
  const matches = view.all(node => node.type === "button" && textOf(node).includes(label) && predicate(node));
  assert.ok(matches.length, `Missing button: ${label}`);
  return matches[0];
}
export function click(view, label, predicate) {
  const target = button(view, label, predicate);
  assert.ok(!target.props.disabled, `Disabled button: ${label}`);
  view.act(() => target.props.onClick());
}
export function choose(view, label) { click(view, label, node => node.props.className?.includes("brass-action-choice")); }
export function card(view, index = 0) {
  const target = view.all(node => node.type === "button" && /^brass-card(?: |$)/.test(node.props.className ?? ""))[index];
  assert.ok(target && !target.props.disabled); view.act(() => target.props.onClick());
}
export function select(view, index, value) {
  const target = view.all(node => node.type === "select")[index];
  assert.ok(target); view.act(() => target.props.onChange({ target: { value } }));
}
export function resource(view, label, count = 1) { for (let i = 0; i < count; i++) click(view, label, node => !node.props.disabled); }
export function requirement(view, index) {
  const target = view.all(node => node.type === "button" && /需要的[煤铁酒]/.test(textOf(node)))[index];
  assert.ok(target); view.act(() => target.props.onClick());
}
export function canonicalTree(node) {
  if (Array.isArray(node)) return node.map(canonicalTree);
  if (!node || typeof node !== "object") return node;
  return { type: node.type, props: Object.fromEntries(Object.entries(node.props).filter(([key]) => key !== "children" && !/^on[A-Z]/.test(key))),
    children: canonicalTree(node.children) };
}

export function draftScenarios() {
  const scenarios = [
    { name: "build-and-resource-reset", props: buildDraftProps, steps: [
      view => choose(view, "建造"), view => card(view),
      view => resource(view, "煤源A", 2), view => click(view, "需要的铁"), view => resource(view, "铁源A"),
      view => click(view, "建造", node => node.props.className === "primary-button"),
      (view, props) => view.update({ ...props, room: { ...props.room, gameState: { ...props.room.gameState, turn: { actionsTaken: 1, actionsRemaining: 1 } } } })
    ] },
    { name: "develop-twice-same-industry", props: buildDraftProps, steps: [view => choose(view, "研发"), view => card(view),
      view => click(view, "棉纺厂", node => node.props.className?.includes("small-button")),
      view => click(view, "棉纺厂", node => node.props.className?.includes("small-button")),
      view => resource(view, "铁源A", 2), view => click(view, "研发", node => node.props.className === "primary-button")
    ] },
    { name: "canal-network", props: () => networkDraftProps(false), steps: [view => choose(view, "运输网"), view => card(view),
      view => select(view, 0, "BIRMINGHAM|COVENTRY"), view => click(view, "运输网", node => node.props.className === "primary-button")
    ] },
    { name: "double-rail", props: networkDraftProps, steps: [view => choose(view, "运输网"), view => card(view),
      view => select(view, 0, "BIRMINGHAM|COVENTRY"), view => select(view, 1, "COVENTRY|NUNEATON"),
      view => resource(view, "煤源A"), view => requirement(view, 1), view => resource(view, "煤源B", 1),
      view => click(view, "同时修建两条铁路需要的酒"), view => resource(view, "自家酒"),
      view => click(view, "运输网", node => node.props.className === "primary-button")
    ] },
    { name: "sell-awaiting-market", props: sellDraftProps, steps: [view => choose(view, "出售"), view => card(view),
      view => click(view, "伯明翰", node => node.props.className?.startsWith("brass-tile"))
    ] },
    { name: "selling-without-new-card", props: () => sellDraftProps(true), steps: [view => choose(view, "出售"),
      view => click(view, "结束售卖行动")
    ] },
    { name: "card-hints", props: buildDraftProps, steps: [view => click(view, "卡牌提示表"), view => click(view, "关闭")] }
  ];
  for (const [action, label] of [["loan", "贷款"], ["scout", "侦查"], ["skip", "跳过"]]) scenarios.push({
    name: action, props: () => {
      const props = buildDraftProps();
      props.room.gameState.hands = { ...props.room.gameState.hands, "1": ["a", "b", "c"].map(id => ({ id, type: "location", key: "BIRMINGHAM" })) };
      return props;
    }, steps: [view => choose(view, label), view => card(view), ...(action === "scout" ? [view => card(view, 1), view => card(view, 2)] : []),
      view => click(view, label, node => !node.props.className?.includes("brass-action-choice"))]
  });
  for (const label of ["重新开始回合", "结束回合", "时代维护"]) scenarios.push({ name: label, props: buildDraftProps, steps: [view => click(view, label)] });
  return scenarios;
}
export function runDraftScenario(Component, scenario, observe) {
  const props = scenario.props(), actions = [], confirmations = [];
  props.onAction = (...args) => actions.push(args);
  const originalWindow = globalThis.window;
  globalThis.window = { confirm: message => { confirmations.push(message); return true; } };
  let view;
  try {
    view = mountHooks(Component, props);
    observe({ tree: canonicalTree(view.tree), actions: [...actions], confirmations: [...confirmations] });
    for (const step of scenario.steps) {
      step(view, props);
      observe({ tree: canonicalTree(view.tree), actions: [...actions], confirmations: [...confirmations] });
    }
  } finally { view?.unmount(); globalThis.window = originalWindow; }
}
