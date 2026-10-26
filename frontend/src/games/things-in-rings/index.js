import Settings from "./Settings.jsx";
export default {
  key: "thingsInRings", name: "环中物语", seatBounds: { min: 2, max: 6 }, Settings,
  createDefaults: () => ({ seatCount: 4, config: { difficulty: "hard", spectatorView: "god", customRules: { SCENE: "", WORD: "", ATTRIBUTE: "" } } }),
  loadRoom: () => import("./ThingsInRingsRoom.jsx"),
  desktopProps: props => ({ room: props.room, seats: props.seats, currentSeat: props.currentSeat, owner: props.owner, onAction: props.onThingsAction })
};
