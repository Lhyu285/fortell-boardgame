import Settings from "./Settings.jsx";
export default {
  key: "camel_up_cards", name: "狂野骆驼：卡牌版", seatBounds: { min: 2, max: 6 }, Settings,
  createDefaults: () => ({ seatCount: 4, config: { expansion: "shortcut_fennec" } }),
  loadRoom: () => import("./CamelUpCardsRoom.jsx"),
  desktopProps: props => ({ room: props.room, seats: props.seats, currentSeat: props.currentSeat, owner: props.owner, onAction: props.onCamelAction })
};
