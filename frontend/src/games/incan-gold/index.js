import Settings from "./Settings.jsx";
export default {
  key: "incan_gold", name: "印加宝藏", seatBounds: { min: 2, max: 8 }, Settings,
  commands: { versioned: true, blockWhilePending: true },
  createDefaults: () => ({ seatCount: 4, config: { thievesExpansion: false, eventsExpansion: false } }),
  loadRoom: () => import("./IncanGoldRoom.jsx"),
  desktopProps: props => ({ room: props.room, seats: props.seats, currentSeat: props.currentSeat,
    onAction: props.onIncanAction, actionPending: props.actionPending })
};
