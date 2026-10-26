package com.fortell.boardgame.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortell.boardgame.game_modules.GameModuleRegistry;
import com.fortell.boardgame.game_modules.brass.BrassGameModule;
import com.fortell.boardgame.models.*;
import com.fortell.boardgame.repositories.*;
import com.fortell.boardgame.utils.JsonUtils;
import com.fortell.boardgame.websocket.RealtimeGateway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.sqlite.SQLiteDataSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class RoomActionAtomicityTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;
    FailingRepository repository;
    RoomService service;
    JsonUtils json = new JsonUtils(new ObjectMapper());
    UserSummary owner, guest;
    int broadcasts;

    @BeforeEach void setup() {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + temp.resolve("atomic.db"));
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        repository = new FailingRepository(jdbc);
        UserRepository users = new UserRepository(jdbc);
        UserAccount a = users.create("owner", "hash"), b = users.create("guest", "hash");
        owner = new UserSummary(a.id(), a.username()); guest = new UserSummary(b.id(), b.username());
        RealtimeGateway gateway = new RealtimeGateway() {
            public void broadcastRoom(String type, String id, Function<UserSummary, RoomDtos.RoomView> factory) {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Broadcast must follow commit");
                RoomDtos.RoomView view = factory.apply(owner);
                Map<String, Object> persisted = json.readMap(entity().gameStateJson());
                assertEquals(persisted.get("version"), view.gameState().get("version"));
                broadcasts++;
            }
            public void broadcastDismissed(String type, String id, String message) {}
        };
        service = new RoomService(repository, users, new GameModuleRegistry(List.of(new BrassGameModule())),
                json, new BCryptPasswordEncoder(4), gateway);
        service.createRoom(owner, new RoomDtos.CreateRoomRequest("brass", "190812", "", 2, Map.of()));
        service.joinRoom(guest, new RoomDtos.JoinRoomRequest("brass", "190812", ""));
        service.moveSeat("brass", "190812", 1, guest);
    }

    @ParameterizedTest @EnumSource(Stage.class)
    void anyWriteFailureRollsBackStateSnapshotsEventsDedupAndBroadcast(Stage stage) {
        RoomDtos.RoomView started = service.startGame("brass", "190812", owner);
        String before = entity().gameStateJson();
        List<Integer> counts = counts(); int broadcastBefore = broadcasts;
        UserSummary actor = actor(started.gameState());
        Map<String, Object> payload = skipPayload(started.gameState(), actor);
        repository.failure = stage;
        assertThrows(IllegalStateException.class, () -> service.handleGameAction("brass", "190812", "skip", payload, 0L, "retry", actor));
        assertEquals(before, entity().gameStateJson()); assertEquals(counts, counts()); assertEquals(broadcastBefore, broadcasts);
        repository.failure = null;
        assertEquals(1L, ((Number) service.handleGameAction("brass", "190812", "skip", payload, 0L, "retry", actor).gameState().get("version")).longValue());
    }

    @Test void failedStartDoesNotPublishOrSavePartiallyStartedGame() {
        String before = entity().gameStateJson(); int broadcastsBefore = broadcasts;
        repository.failure = Stage.SNAPSHOT;
        assertThrows(IllegalStateException.class, () -> service.startGame("brass", "190812", owner));
        assertEquals(RoomStatus.WAITING, entity().status()); assertEquals(before, entity().gameStateJson());
        assertEquals(List.of(0, 0, 0), counts()); assertEquals(broadcastsBefore, broadcasts);
    }

    @Test void eachEventDescribesItsOwnOperationAndRestartPreservesHistoryAndVersions() {
        RoomDtos.RoomView started = service.startGame("brass", "190812", owner);
        UserSummary actor = actor(started.gameState());
        Map<String, Object> startingHands = service.getRoom("brass", "190812", actor).gameState();
        RoomDtos.RoomView skipped = service.handleGameAction("brass", "190812", "skip", skipPayload(started.gameState(), actor), 0L, "skip", actor);
        RoomDtos.RoomView restarted = service.handleGameAction("brass", "190812", "restart_turn", Map.of(), 1L, "restart", actor);
        assertEquals(startingHands.get("hands"), restarted.gameState().get("hands"));
        assertEquals(2L, ((Number) restarted.gameState().get("version")).longValue());
        RoomDtos.RoomView again = service.handleGameAction("brass", "190812", "skip", skipPayload(restarted.gameState(), actor), 2L, "again", actor);
        service.handleGameAction("brass", "190812", "end_turn", Map.of(), 3L, "end", actor);
        Map<String, Object> next = json.readMap(entity().gameStateJson());
        Map<?, ?> nextSnapshot = (Map<?, ?>) next.get("turnStartSnapshot");
        assertEquals(4L, ((Number) nextSnapshot.get("version")).longValue());
        List<Map<String, Object>> rows = jdbc.queryForList("select action_type,event_json,version from game_action_logs order by version");
        assertEquals(List.of("skip", "restart_turn", "skip", "end_turn"), rows.stream().map(row -> row.get("action_type")).toList());
        for (Map<String, Object> row : rows) {
            Map<String, Object> event = json.readMap((String) row.get("event_json"));
            assertEquals(row.get("action_type"), event.get("type"));
            assertEquals(((Number) row.get("version")).longValue(), ((Number) event.get("afterVersion")).longValue());
            assertEquals(1, event.get("schemaVersion"));
        }
        assertEquals(List.of(5, 4, 4), counts());
        List<Integer> before = counts(); int broadcastsBefore = broadcasts;
        assertThrows(ApiException.class, () -> service.handleGameAction("brass", "190812", "skip", skipPayload(again.gameState(), actor), 0L, "stale", actor));
        assertThrows(ApiException.class, () -> service.handleGameAction("brass", "190812", "skip", Map.of(), 4L, "skip", actor));
        assertEquals(before, counts()); assertEquals(broadcastsBefore, broadcasts);
        assertEquals(1L, ((Number) skipped.gameState().get("version")).longValue());
    }

    @Test void immutableSnapshotRejectsReplacement() {
        service.startGame("brass", "190812", owner);
        String original = jdbc.queryForObject("select state_json from game_snapshots where version=0", String.class);
        assertThrows(RuntimeException.class, () -> repository.insertImmutableSnapshot(entity().id(), 0, "{}", Instant.now()));
        assertEquals(original, jdbc.queryForObject("select state_json from game_snapshots where version=0", String.class));
    }

    @Test void playerAndSpectatorViewsNeverExposePrivatePersistenceState() {
        service.startGame("brass", "190812", owner);
        UserAccount account = new UserRepository(jdbc).create("watcher", "hash");
        UserSummary watcher = new UserSummary(account.id(), account.username());
        service.joinRoom(watcher, new RoomDtos.JoinRoomRequest("brass", "190812", ""));
        Map<String, Object> authoritative = json.readMap(entity().gameStateJson());
        for (UserSummary viewer : List.of(owner, guest, watcher)) {
            Map<String, Object> visible = service.getRoom("brass", "190812", viewer).gameState();
            assertFalse(visible.containsKey("turnStartSnapshot"));
            Map<?, ?> hands = (Map<?, ?>) visible.get("hands");
            for (var entry : hands.entrySet()) {
                List<?> cards = (List<?>) entry.getValue();
                if (!entry.getKey().equals(String.valueOf(viewer.id()))) {
                    assertTrue(cards.stream().allMatch(card -> card.equals(Map.of("hidden", true))));
                } else assertEquals(((Map<?, ?>) authoritative.get("hands")).get(entry.getKey()), cards);
            }
        }
        assertTrue(authoritative.containsKey("turnStartSnapshot"));
    }

    @Test void concurrentRequestsAtSameVersionAcceptExactlyOneOperation() throws Exception {
        RoomDtos.RoomView started = service.startGame("brass", "190812", owner);
        UserSummary actor = actor(started.gameState()); Map<String, Object> payload = skipPayload(started.gameState(), actor);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch gate = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String id = "parallel-" + i;
                futures.add(pool.submit(() -> { gate.await(); try {
                    service.handleGameAction("brass", "190812", "skip", payload, 0L, id, actor); return true;
                } catch (ApiException expected) { return false; } }));
            }
            gate.countDown(); int accepted = 0;
            for (Future<Boolean> future : futures) if (future.get(15, TimeUnit.SECONDS)) accepted++;
            assertEquals(1, accepted); assertEquals(List.of(2, 1, 1), counts());
        }
    }

    RoomEntity entity() { return repository.findByGameTypeAndRoomId(GameType.BRASS, "190812").orElseThrow(); }
    UserSummary actor(Map<String, Object> state) { return ((Number) state.get("currentPlayerId")).longValue() == owner.id() ? owner : guest; }
    @SuppressWarnings("unchecked") Map<String, Object> skipPayload(Map<String, Object> state, UserSummary actor) {
        Map<String, Object> ownView = service.getRoom("brass", "190812", actor).gameState();
        List<Map<String, Object>> hand = (List<Map<String, Object>>) ((Map<?, ?>) ownView.get("hands")).get(String.valueOf(actor.id()));
        return Map.of("cardId", hand.getFirst().get("id"));
    }
    List<Integer> counts() {
        return List.of(jdbc.queryForObject("select count(*) from game_snapshots", Integer.class),
                jdbc.queryForObject("select count(*) from game_action_logs", Integer.class),
                jdbc.queryForObject("select count(*) from client_action_dedup", Integer.class));
    }
    enum Stage { STATE, DEDUP, SNAPSHOT, EVENT }
    static final class FailingRepository extends RoomRepository {
        Stage failure;
        FailingRepository(JdbcTemplate jdbc) { super(jdbc); }
        void fail(Stage stage) { if (failure == stage) throw new IllegalStateException("Injected " + stage + " failure"); }
        @Override public void update(RoomEntity room) { super.update(room); fail(Stage.STATE); }
        @Override public void insertClientAction(long room, long actor, String id, long version, Instant time) {
            super.insertClientAction(room, actor, id, version, time); fail(Stage.DEDUP);
        }
        @Override public void insertImmutableSnapshot(long room, long version, String state, Instant time) {
            super.insertImmutableSnapshot(room, version, state, time); fail(Stage.SNAPSHOT);
        }
        @Override public void insertActionLog(long room, long version, long actor, String type, String payload, String event, String message, Instant time) {
            super.insertActionLog(room, version, actor, type, payload, event, message, time); fail(Stage.EVENT);
        }
    }
}
