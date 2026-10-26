import Settings from "./Settings.jsx";
export default {
  key: "rps", name: "猜拳", seatBounds: { min: 2, max: 8 }, Settings,
  createDefaults: () => ({ seatCount: 4, config: { moveSet: 3, mode: "free_for_all" } }),
  loadRoom: () => import("./RpsRoom.jsx"),
  desktopProps: props => ({ room: props.room, seats: props.seats, currentSeat: props.currentSeat, owner: props.owner,
    onSubmitMove: props.onSubmitMove, selectedMove: props.selection.selectedMove, setSelectedMove: props.selection.setSelectedMove })
};
