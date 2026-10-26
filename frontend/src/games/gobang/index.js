import Settings from "./Settings.jsx";
export default {
  key: "gobang", name: "五子棋", seatBounds: { min: 2, max: 2 }, Settings,
  createDefaults: () => ({ seatCount: 2, config: { ruleMode: "normal", firstHand: "random" } })
};
