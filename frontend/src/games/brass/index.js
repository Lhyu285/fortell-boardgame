export default {
  key: "brass", name: "工业革命：伯明翰", entryTitle: "伯明翰", seatBounds: { min: 2, max: 4 },
  commands: { versioned: true },
  createDefaults: () => ({ seatCount: 4, config: { tableName: "伯明翰占位桌" } }),
  loadRoom: () => import("./BrassRoom.jsx"),
  desktopProps: props => ({ room: props.room, seats: props.seats, currentSeat: props.currentSeat, onAction: props.onBrassAction })
};
