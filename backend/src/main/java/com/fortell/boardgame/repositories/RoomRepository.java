package com.fortell.boardgame.repositories;

import com.fortell.boardgame.models.GameType;
import com.fortell.boardgame.models.RoomEntity;
import com.fortell.boardgame.models.RoomMember;
import com.fortell.boardgame.models.RoomSeat;
import com.fortell.boardgame.models.RoomStatus;
import com.fortell.boardgame.models.UserSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class RoomRepository {
    private final JdbcTemplate jdbcTemplate;

    public RoomRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<RoomEntity> findByGameTypeAndRoomId(GameType gameType, String roomId) {
        List<RoomEntity> rows = jdbcTemplate.query(
                "select * from rooms where game_type = ? and room_id = ?",
                (resultSet, rowNum) -> new RoomEntity(
                        resultSet.getLong("id"),
                        GameType.valueOf(resultSet.getString("game_type")),
                        resultSet.getString("room_id"),
                        resultSet.getLong("owner_user_id"),
                        resultSet.getString("password_hash"),
                        resultSet.getInt("seat_count"),
                        RoomStatus.valueOf(resultSet.getString("status")),
                        resultSet.getString("config_json"),
                        resultSet.getString("game_state_json"),
                        Instant.parse(resultSet.getString("created_at")),
                        Instant.parse(resultSet.getString("updated_at"))
                ),
                gameType.name(),
                roomId
        );
        return rows.stream().findFirst();
    }

    public RoomEntity create(RoomEntity roomEntity) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    insert into rooms (
                        game_type, room_id, owner_user_id, password_hash, seat_count, status,
                        config_json, game_state_json, created_at, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, roomEntity.gameType().name());
            statement.setString(2, roomEntity.roomId());
            statement.setLong(3, roomEntity.ownerUserId());
            statement.setString(4, roomEntity.passwordHash());
            statement.setInt(5, roomEntity.seatCount());
            statement.setString(6, roomEntity.status().name());
            statement.setString(7, roomEntity.configJson());
            statement.setString(8, roomEntity.gameStateJson());
            statement.setString(9, roomEntity.createdAt().toString());
            statement.setString(10, roomEntity.updatedAt().toString());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        long id = key == null ? -1 : key.longValue();
        insertSeatRows(id, roomEntity.seatCount());
        return new RoomEntity(
                id,
                roomEntity.gameType(),
                roomEntity.roomId(),
                roomEntity.ownerUserId(),
                roomEntity.passwordHash(),
                roomEntity.seatCount(),
                roomEntity.status(),
                roomEntity.configJson(),
                roomEntity.gameStateJson(),
                roomEntity.createdAt(),
                roomEntity.updatedAt()
        );
    }

    public void update(RoomEntity roomEntity) {
        jdbcTemplate.update(
                """
                update rooms
                set owner_user_id = ?, password_hash = ?, seat_count = ?, status = ?,
                    config_json = ?, game_state_json = ?, updated_at = ?
                where id = ?
                """,
                roomEntity.ownerUserId(),
                roomEntity.passwordHash(),
                roomEntity.seatCount(),
                roomEntity.status().name(),
                roomEntity.configJson(),
                roomEntity.gameStateJson(),
                roomEntity.updatedAt().toString(),
                roomEntity.id()
        );
    }

    public void delete(long id) {
        jdbcTemplate.update("delete from client_action_dedup where room_id = ?", id);
        jdbcTemplate.update("delete from game_action_logs where room_id = ?", id);
        jdbcTemplate.update("delete from game_snapshots where room_id = ?", id);
        jdbcTemplate.update("delete from room_seats where room_id = ?", id);
        jdbcTemplate.update("delete from room_members where room_id = ?", id);
        jdbcTemplate.update("delete from rooms where id = ?", id);
    }

    public void deleteAllRooms() {
        jdbcTemplate.update("delete from client_action_dedup");
        jdbcTemplate.update("delete from game_action_logs");
        jdbcTemplate.update("delete from game_snapshots");
        jdbcTemplate.update("delete from room_seats");
        jdbcTemplate.update("delete from room_members");
        jdbcTemplate.update("delete from rooms");
    }

    public boolean clientActionExists(long roomDatabaseId, long playerId, String clientActionId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from client_action_dedup
                where room_id = ? and player_id = ? and client_action_id = ?
                """,
                Integer.class,
                roomDatabaseId,
                playerId,
                clientActionId
        );
        return count != null && count > 0;
    }

    public void insertClientAction(long roomDatabaseId, long playerId, String clientActionId,
                                   long stateVersion, Instant createdAt) {
        jdbcTemplate.update(
                """
                insert into client_action_dedup (
                    room_id, player_id, client_action_id, state_version, created_at
                ) values (?, ?, ?, ?, ?)
                """,
                roomDatabaseId,
                playerId,
                clientActionId,
                stateVersion,
                createdAt.toString()
        );
    }

    public void insertSnapshot(long roomDatabaseId, long version, String stateJson, Instant createdAt) {
        jdbcTemplate.update(
                """
                insert or replace into game_snapshots (
                    room_id, version, state_json, created_at
                ) values (?, ?, ?, ?)
                """,
                roomDatabaseId,
                version,
                stateJson,
                createdAt.toString()
        );
    }

    public void insertActionLog(long roomDatabaseId, long version, long playerId, String actionType,
                                String actionPayloadJson, String eventJson, String readableMessage,
                                Instant createdAt) {
        jdbcTemplate.update(
                """
                insert into game_action_logs (
                    room_id, version, player_id, action_type, action_payload_json,
                    event_json, readable_message, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                roomDatabaseId,
                version,
                playerId,
                actionType,
                actionPayloadJson,
                eventJson,
                readableMessage,
                createdAt.toString()
        );
    }

    public List<RoomSeat> listSeats(long roomDatabaseId) {
        return jdbcTemplate.query(
                "select seat_index, user_id, username from room_seats where room_id = ? order by seat_index",
                (resultSet, rowNum) -> {
                    long userId = resultSet.getLong("user_id");
                    boolean userIdWasNull = resultSet.wasNull();
                    return new RoomSeat(
                            resultSet.getInt("seat_index"),
                            userIdWasNull ? null : userId,
                            resultSet.getString("username")
                    );
                },
                roomDatabaseId
        );
    }

    public List<RoomMember> listMembers(long roomDatabaseId) {
        return jdbcTemplate.query(
                "select user_id, username from room_members where room_id = ? order by user_id",
                (resultSet, rowNum) -> new RoomMember(
                        resultSet.getLong("user_id"),
                        resultSet.getString("username")
                ),
                roomDatabaseId
        );
    }

    public void addMember(long roomDatabaseId, UserSummary user) {
        jdbcTemplate.update(
                "insert or ignore into room_members (room_id, user_id, username) values (?, ?, ?)",
                roomDatabaseId,
                user.id(),
                user.username()
        );
    }

    public void removeMember(long roomDatabaseId, long userId) {
        jdbcTemplate.update(
                "delete from room_members where room_id = ? and user_id = ?",
                roomDatabaseId,
                userId
        );
    }

    public void occupySeat(long roomDatabaseId, int seatIndex, UserSummary user) {
        jdbcTemplate.update(
                "update room_seats set user_id = ?, username = ? where room_id = ? and seat_index = ?",
                user.id(),
                user.username(),
                roomDatabaseId,
                seatIndex
        );
    }

    public void occupyBotSeat(long roomDatabaseId, int seatIndex, long botId, String botName) {
        jdbcTemplate.update(
                "update room_seats set user_id = ?, username = ? where room_id = ? and seat_index = ?",
                botId,
                botName,
                roomDatabaseId,
                seatIndex
        );
    }

    public void clearSeat(long roomDatabaseId, int seatIndex) {
        jdbcTemplate.update(
                "update room_seats set user_id = null, username = null where room_id = ? and seat_index = ?",
                roomDatabaseId,
                seatIndex
        );
    }

    public void clearSeatByUser(long roomDatabaseId, long userId) {
        jdbcTemplate.update(
                "update room_seats set user_id = null, username = null where room_id = ? and user_id = ?",
                roomDatabaseId,
                userId
        );
    }

    public void clearLegacyBotSeats(long roomDatabaseId) {
        jdbcTemplate.update(
                """
                update room_seats
                set user_id = null, username = null
                where room_id = ?
                  and (
                    user_id < 0
                    or username like 'Robot_%'
                  )
                """,
                roomDatabaseId
        );
    }

    public void resizeSeats(long roomDatabaseId, int currentSeatCount, int newSeatCount) {
        if (newSeatCount > currentSeatCount) {
            for (int seatIndex = currentSeatCount; seatIndex < newSeatCount; seatIndex++) {
                jdbcTemplate.update(
                        "insert into room_seats (room_id, seat_index, user_id, username) values (?, ?, null, null)",
                        roomDatabaseId,
                        seatIndex
                );
            }
        } else if (newSeatCount < currentSeatCount) {
            jdbcTemplate.update(
                    "delete from room_seats where room_id = ? and seat_index >= ?",
                    roomDatabaseId,
                    newSeatCount
            );
        }
    }

    public void replaceSeats(long roomDatabaseId, List<RoomSeat> seats) {
        jdbcTemplate.update("delete from room_seats where room_id = ?", roomDatabaseId);
        for (RoomSeat seat : seats) {
            jdbcTemplate.update(
                    "insert into room_seats (room_id, seat_index, user_id, username) values (?, ?, ?, ?)",
                    roomDatabaseId,
                    seat.seatIndex(),
                    seat.userId(),
                    seat.username()
            );
        }
    }

    private void insertSeatRows(long roomDatabaseId, int seatCount) {
        for (int seatIndex = 0; seatIndex < seatCount; seatIndex++) {
            jdbcTemplate.update(
                    "insert into room_seats (room_id, seat_index, user_id, username) values (?, ?, null, null)",
                    roomDatabaseId,
                    seatIndex
            );
        }
    }
}
