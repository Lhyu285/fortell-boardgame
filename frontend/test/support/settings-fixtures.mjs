import { desktopProps } from "./desktop-fixtures.mjs";
export function settingsCases() {
  return [
    ...[3, 5].flatMap(moveSet => ["free_for_all", "bracket"].map(mode => ({ gameType: "rps", component: "RpsSettings", config: { moveSet, mode } }))),
    ...["easy", "medium", "hard", "custom"].map(difficulty => ({ gameType: "thingsInRings", component: "ThingsSettings",
      config: { difficulty, spectatorView: "player", customRules: { SCENE: "测试情境", WORD: "测试词汇", ATTRIBUTE: "测试属性" } } })),
    { gameType: "thingsInRings", component: "ThingsSettings", config: { difficulty: "hard", spectatorView: "god" } },
    ...["none", "shortcut", "fennec", "shortcut_fennec"].map(expansion => ({ gameType: "camel_up_cards", component: "CamelSettings", config: { expansion } })),
    ...[false, true].flatMap(thievesExpansion => [false, true].map(eventsExpansion => ({ gameType: "incan_gold", component: "IncanGoldSettings", config: { thievesExpansion, eventsExpansion } }))),
    ...["normal", "professional"].map(ruleMode => ({ gameType: "gobang", component: "GobangSettings", config: { ruleMode, firstHand: "host_first" } }))
  ].flatMap((item, index) => [true, false].map(editable => ({ ...item, editable, name: `${index}-${item.gameType}-${editable ? "host" : "guest"}` })));
}
export function prepareProps(gameType, config = {}, owner = true) {
  const sample = desktopProps();
  return { gameType, config, owner, waiting: true, seats: sample.seats, seatCount: 2, currentSeat: sample.currentSeat,
    canAddSeat: owner, canRemoveSeat: false, canStart: owner, onAddSeat() {}, onRemoveSeat() {}, onMoveSeat() {}, onForceStandUp() {},
    onStandUp() {}, onStartGame() {}, onUpdateConfig() {},
    onUpdateRpsConfig() {}, onUpdateThingsConfig() {}, onUpdateCamelConfig() {}, onUpdateGobangConfig() {}, onUpdateIncanConfig() {} };
}
