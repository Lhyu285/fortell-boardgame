CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS rooms (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    game_type TEXT NOT NULL,
    room_id TEXT NOT NULL,
    owner_user_id INTEGER NOT NULL,
    password_hash TEXT,
    seat_count INTEGER NOT NULL,
    status TEXT NOT NULL,
    config_json TEXT NOT NULL,
    game_state_json TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(game_type, room_id)
);

CREATE TABLE IF NOT EXISTS room_seats (
    room_id INTEGER NOT NULL,
    seat_index INTEGER NOT NULL,
    user_id INTEGER,
    username TEXT,
    PRIMARY KEY(room_id, seat_index)
);

CREATE TABLE IF NOT EXISTS room_members (
    room_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    username TEXT NOT NULL,
    PRIMARY KEY(room_id, user_id)
);

CREATE TABLE IF NOT EXISTS game_snapshots (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    room_id INTEGER NOT NULL,
    version INTEGER NOT NULL,
    state_json TEXT NOT NULL,
    created_at TEXT NOT NULL,
    UNIQUE(room_id, version)
);

CREATE TABLE IF NOT EXISTS game_action_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    room_id INTEGER NOT NULL,
    version INTEGER NOT NULL,
    player_id INTEGER NOT NULL,
    action_type TEXT NOT NULL,
    action_payload_json TEXT NOT NULL,
    event_json TEXT NOT NULL,
    readable_message TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS client_action_dedup (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    room_id INTEGER NOT NULL,
    player_id INTEGER NOT NULL,
    client_action_id TEXT NOT NULL,
    state_version INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    UNIQUE(room_id, player_id, client_action_id)
);
