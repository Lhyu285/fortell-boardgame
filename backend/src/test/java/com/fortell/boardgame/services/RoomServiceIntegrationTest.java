package com.fortell.boardgame.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fortell.boardgame.game_modules.GameModuleRegistry;
import com.fortell.boardgame.game_modules.brass.BrassGameModule;
import com.fortell.boardgame.game_modules.rps.RpsGameModule;
import com.fortell.boardgame.models.ApiException;
import com.fortell.boardgame.models.GameType;
import com.fortell.boardgame.models.RoomDtos;
import com.fortell.boardgame.models.RoomEntity;
import com.fortell.boardgame.models.UserAccount;
import com.fortell.boardgame.models.UserSummary;
import com.fortell.boardgame.repositories.RoomRepository;
import com.fortell.boardgame.repositories.UserRepository;
import com.fortell.boardgame.utils.JsonUtils;
import com.fortell.boardgame.websocket.RealtimeGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomServiceIntegrationTest {
    @TempDir
    Path tempDir;

    private RoomService roomService;
    private RoomRepository roomRepository;
    private UserRepository userRepository;
    private JsonUtils jsonUtils;
    private RecordingRealtimeGateway realtimeGateway;
    private UserSummary owner;
    private UserSummary guest;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("rooms.db"));
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        roomRepository = new RoomRepository(jdbcTemplate);
        userRepository = new UserRepository(jdbcTemplate);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        realtimeGateway = new RecordingRealtimeGateway();
        jsonUtils = new JsonUtils(objectMapper);
        roomService = new RoomService(
                roomRepository,
                userRepository,
                new GameModuleRegistry(List.of(new RpsGameModule(), new BrassGameModule())),
                jsonUtils,
                new BCryptPasswordEncoder(4),
                realtimeGateway
        );

        owner = createUser("owner");
        guest = createUser("guest");
    }

    @Test
    void roomLifecycleSupportsJoinSeatStandForceStartEndLeaveAndDismiss() {
        RoomDtos.RoomView created = createRoom("brass", "1234", "abcd", 2);
        assertEquals("WAITING", created.status());
        assertTrue(created.permissions().canLeaveRoom());
        assertEquals(1, created.roomState().occupiedSeatCount());
        assertTrue(created.seats().get(0).ownerSeat());

        RoomDtos.RoomView joined = roomService.joinRoom(guest, new RoomDtos.JoinRoomRequest("brass", "1234", "abcd"));
        assertEquals(1, joined.roomState().occupiedSeatCount());
        assertTrue(joined.permissions().canJoinSeat());

        RoomDtos.RoomView seated = roomService.moveSeat("brass", "1234", 1, guest);
        assertEquals(2, seated.roomState().occupiedSeatCount());
        assertTrue(seated.roomState().allSeatsOccupied());

        RoomDtos.RoomView standing = roomService.standUp("brass", "1234", guest);
        assertEquals(1, standing.roomState().occupiedSeatCount());
        roomService.moveSeat("brass", "1234", 1, guest);

        RoomDtos.RoomView forced = roomService.forceStandUp("brass", "1234", 1, owner);
        assertEquals(1, forced.roomState().occupiedSeatCount());
        roomService.moveSeat("brass", "1234", 1, guest);

        RoomDtos.RoomView started = roomService.startGame("brass", "1234", owner);
        assertEquals("IN_PROGRESS", started.status());
        assertTrue(started.permissions().canSubmitGameAction());

        RoomDtos.RoomView ended = roomService.endGame("brass", "1234", owner);
        assertEquals("WAITING", ended.status());
        assertEquals(2, ended.roomState().occupiedSeatCount());

        assertTrue(roomService.leaveRoom("brass", "1234", guest).success());
        assertEquals(1, roomService.getRoom("brass", "1234", owner).roomState().occupiedSeatCount());
        assertTrue(roomService.dismissRoom("brass", "1234", owner).success());
        assertEquals(1, realtimeGateway.dismissedRooms.size());
        assertEquals(HttpStatus.NOT_FOUND, apiFailure(() -> roomService.getRoom("brass", "1234", owner)).getStatus());
    }

    @Test
    void roomValidationRejectsInvalidInputInsufficientPlayersAndUnauthorizedOperations() {
        assertEquals(HttpStatus.BAD_REQUEST, apiFailure(() -> createRoom("brass", "12", "", 2)).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, apiFailure(() -> createRoom("brass", "1234", "abc", 2)).getStatus());

        createRoom("brass", "1234", "abcd", 2);
        assertEquals(HttpStatus.BAD_REQUEST, apiFailure(() ->
                roomService.joinRoom(guest, new RoomDtos.JoinRoomRequest("brass", "1234", "wrong"))).getStatus());
        roomService.joinRoom(guest, new RoomDtos.JoinRoomRequest("brass", "1234", "abcd"));

        assertEquals(HttpStatus.BAD_REQUEST, apiFailure(() -> roomService.startGame("brass", "1234", owner)).getStatus());
        assertEquals(HttpStatus.FORBIDDEN, apiFailure(() -> roomService.forceStandUp("brass", "1234", 0, guest)).getStatus());
        assertEquals(HttpStatus.FORBIDDEN, apiFailure(() -> roomService.dismissRoom("brass", "1234", guest)).getStatus());
    }

    @Test
    void leavePermissionRejectsActiveSeatedPlayersButAllowsSpectatorsAndFinishedGames() {
        RoomDtos.RoomView started = startTwoPlayerBrass();
        assertFalse(started.permissions().canLeaveRoom());
        assertFalse(roomService.getRoom("brass", "1234", guest).permissions().canLeaveRoom());
        assertEquals(HttpStatus.BAD_REQUEST, apiFailure(() ->
                roomService.leaveRoom("brass", "1234", guest)).getStatus());

        UserSummary spectator = createUser("spectator");
        RoomDtos.RoomView spectatorView = roomService.joinRoom(
                spectator,
                new RoomDtos.JoinRoomRequest("brass", "1234", "")
        );
        assertTrue(spectatorView.permissions().canLeaveRoom());
        assertTrue(roomService.leaveRoom("brass", "1234", spectator).success());

        for (String phase : List.of("finished", "FINISHED", "GAME_OVER")) {
            setGamePhase(phase);
            assertTrue(roomService.getRoom("brass", "1234", guest).permissions().canLeaveRoom());
        }
        assertTrue(roomService.leaveRoom("brass", "1234", guest).success());
    }

    @Test
    void onlyActiveSeatedNonOwnerPlayersCanProposeEndingTheGame() {
        startTwoPlayerBrass();
        RoomDtos.RoomView proposed = roomService.proposeEndGame("brass", "1234", guest);
        assertTrue(((List<?>) proposed.gameState().get("endProposals")).stream()
                .anyMatch(proposal -> String.valueOf(((Map<?, ?>) proposal).get("userId"))
                        .equals(String.valueOf(guest.id()))));

        UserSummary spectator = createUser("spectator");
        roomService.joinRoom(spectator, new RoomDtos.JoinRoomRequest("brass", "1234", ""));
        assertEquals(HttpStatus.BAD_REQUEST, apiFailure(() ->
                roomService.proposeEndGame("brass", "1234", spectator)).getStatus());

        setGamePhase("FINISHED");
        assertEquals(HttpStatus.BAD_REQUEST, apiFailure(() ->
                roomService.proposeEndGame("brass", "1234", guest)).getStatus());
    }

    @Test
    void versionedBrassActionsRejectStaleVersionsAndDuplicateClientActionIds() {
        RoomDtos.RoomView started = startTwoPlayerBrass();
        long version = ((Number) started.gameState().get("version")).longValue();
        long currentPlayerId = ((Number) started.gameState().get("currentPlayerId")).longValue();
        UserSummary actor = currentPlayerId == owner.id() ? owner : guest;
        String cardId = String.valueOf(((Map<?, ?>) ((List<?>) ((Map<?, ?>) started.gameState().get("hands"))
                .get(String.valueOf(actor.id()))).get(0)).get("id"));

        RoomDtos.RoomView afterAction = roomService.handleGameAction(
                "brass",
                "1234",
                "skip",
                Map.of("cardId", cardId),
                version,
                "action-1",
                actor
        );
        assertEquals(version + 1, ((Number) afterAction.gameState().get("version")).longValue());

        assertEquals(HttpStatus.CONFLICT, apiFailure(() -> roomService.handleGameAction(
                "brass", "1234", "skip", Map.of("cardId", cardId), version, "action-2", actor
        )).getStatus());
        assertEquals(HttpStatus.CONFLICT, apiFailure(() -> roomService.handleGameAction(
                "brass", "1234", "skip", Map.of("cardId", cardId), version + 1, "action-1", actor
        )).getStatus());
    }

    @Test
    void broadcastsRoomChangesAndShutdownDeletesAllRooms() {
        createRoom("rps", "2345", "", 2);
        roomService.joinRoom(guest, new RoomDtos.JoinRoomRequest("rps", "2345", ""));
        roomService.moveSeat("rps", "2345", 1, guest);
        assertTrue(realtimeGateway.roomBroadcasts.size() >= 3);
        assertTrue(roomRepository.findByGameTypeAndRoomId(GameType.RPS, "2345").isPresent());

        roomService.destroyAllRoomsOnShutdown();

        assertTrue(roomRepository.findByGameTypeAndRoomId(GameType.RPS, "2345").isEmpty());
    }

    private RoomDtos.RoomView startTwoPlayerBrass() {
        createRoom("brass", "1234", "", 2);
        roomService.joinRoom(guest, new RoomDtos.JoinRoomRequest("brass", "1234", ""));
        roomService.moveSeat("brass", "1234", 1, guest);
        return roomService.startGame("brass", "1234", owner);
    }

    private RoomDtos.RoomView createRoom(String gameType, String roomId, String password, int seatCount) {
        return roomService.createRoom(owner, new RoomDtos.CreateRoomRequest(
                gameType,
                roomId,
                password,
                seatCount,
                Map.of()
        ));
    }

    private UserSummary createUser(String username) {
        UserAccount account = userRepository.create(username, "hash");
        return new UserSummary(account.id(), account.username());
    }

    private void setGamePhase(String phase) {
        RoomEntity entity = roomRepository.findByGameTypeAndRoomId(GameType.BRASS, "1234").orElseThrow();
        Map<String, Object> state = jsonUtils.readMap(entity.gameStateJson());
        state.put("phase", phase);
        roomRepository.update(new RoomEntity(
                entity.id(), entity.gameType(), entity.roomId(), entity.ownerUserId(), entity.passwordHash(),
                entity.seatCount(), entity.status(), entity.configJson(), jsonUtils.write(state),
                entity.createdAt(), entity.updatedAt()
        ));
    }

    private ApiException apiFailure(Runnable action) {
        return assertThrows(ApiException.class, action::run);
    }

    private static final class RecordingRealtimeGateway implements RealtimeGateway {
        private final List<String> roomBroadcasts = new ArrayList<>();
        private final List<String> dismissedRooms = new ArrayList<>();

        @Override
        public void broadcastRoom(String gameType, String roomId,
                                  Function<UserSummary, RoomDtos.RoomView> roomViewFactory) {
            roomBroadcasts.add(gameType + ":" + roomId);
        }

        @Override
        public void broadcastDismissed(String gameType, String roomId, String message) {
            dismissedRooms.add(gameType + ":" + roomId);
        }
    }
}
