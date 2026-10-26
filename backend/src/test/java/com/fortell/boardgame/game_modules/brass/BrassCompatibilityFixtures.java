package com.fortell.boardgame.game_modules.brass;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortell.boardgame.models.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fixed, synthetic saves; never reads a live room or the player's database. */
final class BrassCompatibilityFixtures {
    static final ObjectMapper JSON = new ObjectMapper();
    static final BrassGameModule MODULE = new BrassGameModule();
    static final RoomEntity ROOM = new RoomEntity(1, GameType.BRASS, "180026", 1, null, 2,
            RoomStatus.IN_PROGRESS, "{}", "{}", Instant.EPOCH, Instant.EPOCH);
    static final List<RoomSeat> SEATS = List.of(new RoomSeat(0, 1L, "A"), new RoomSeat(1, 2L, "B"));

    record Scenario(String name, Map<String, Object> state, List<Map<String, Object>> commands) {
        @Override public String toString() { return name; }
    }

    static Stream<Scenario> scenarios() throws IOException {
        List<Map<String, Object>> specifications;
        try (var input = Objects.requireNonNull(BrassCompatibilityFixtures.class.getResourceAsStream("/brass/compatibility/scenarios.json"))) {
            specifications = JSON.readValue(input, new TypeReference<>() {});
        }
        List<Scenario> cases = new ArrayList<>();
        for (var spec : specifications) {
            Map<String, Object> state = opening();
            state.put("hands", new LinkedHashMap<>(Map.of("1", new ArrayList<>(List.of(new LinkedHashMap<>(Map.of(
                    "id", "fixture-card-1", "type", "location", "key", "Birmingham", "city", "Birmingham", "name", "伯明翰")))),
                    "2", new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("id", "fixture-card-2", "type", "location", "key", "Oxford", "city", "Oxford", "name", "牛津")))))));
            merge(state, mapOf(spec.get("patch")));
            if (spec.containsKey("checkpoint")) {
                Map<String, Object> checkpoint = mapOf(deepCopy(state));
                checkpoint.remove("turnStartSnapshot");
                merge(checkpoint, mapOf(spec.get("checkpoint")));
                state.put("turnStartSnapshot", checkpoint);
            }
            BrassStateProjection.updateMaintenanceFlag(state);
            if (spec.containsKey("actions")) assertEquals(spec.get("actions"), viewMap(state.get("availableActions")).get("actions"));
            cases.add(new Scenario((String) spec.get("name"), state, listOf(spec.get("commands"))));
        }
        return cases.stream();
    }

    static Map<String, Object> opening() throws IOException {
        try (var input = Objects.requireNonNull(BrassCompatibilityFixtures.class.getResourceAsStream("/brass/opening-query-baseline.json"))) {
            return JSON.readValue(input, new TypeReference<>() {});
        }
    }

    static Map<String, Object> reload(Map<String, Object> state) throws IOException {
        return JSON.readValue(JSON.writeValueAsBytes(state), new TypeReference<>() {});
    }

    static void assertJsonEquals(Object expected, Object actual) throws IOException {
        var left = JSON.readTree(JSON.writeValueAsBytes(expected));
        var right = JSON.readTree(JSON.writeValueAsBytes(actual));
        assertTrue(left.equals(right), () -> "JSON differs at " + firstDifference(left, right, "$"));
    }

    static String digest(Object value) throws Exception {
        Object json = JSON.readValue(JSON.writeValueAsBytes(value), Object.class);
        byte[] canonical = JSON.writeValueAsBytes(sorted(json));
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(canonical));
    }

    private static Object sorted(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put((String) key, sorted(item)));
            return sorted;
        }
        if (value instanceof List<?> list) return list.stream().map(BrassCompatibilityFixtures::sorted).toList();
        return value;
    }

    private static String firstDifference(com.fasterxml.jackson.databind.JsonNode left, com.fasterxml.jackson.databind.JsonNode right, String path) {
        if (left == null || right == null || left.getNodeType() != right.getNodeType()) return path;
        if (left.isObject()) {
            Set<String> keys = new LinkedHashSet<>(); left.fieldNames().forEachRemaining(keys::add); right.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) if (!Objects.equals(left.get(key), right.get(key))) return firstDifference(left.get(key), right.get(key), path + "." + key);
        }
        if (left.isArray()) for (int i = 0; i < Math.max(left.size(), right.size()); i++) {
            if (!Objects.equals(left.get(i), right.get(i))) return firstDifference(left.get(i), right.get(i), path + "[" + i + "]");
        }
        return path + " (" + left + " vs " + right + ")";
    }

    static com.fortell.boardgame.game_modules.GameTransitionResult act(Map<String, Object> state, Map<String, Object> command) {
        long id = asLong(state.get("currentPlayerId"));
        var result = MODULE.onTransition(ROOM, SEATS, MODULE.defaultConfig(), state,
                new UserSummary(id, id == 1 ? "A" : "B"), (String) command.get("type"), mapOf(command.get("payload")), List.of());
        result.state().put("version", asLong(state.get("version")) + 1);
        return result;
    }

    static void assertSubset(Map<String, Object> expected, Map<String, Object> actual) {
        for (var entry : expected.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?>) assertSubset(viewMap(entry.getValue()), viewMap(actual.get(entry.getKey())));
            else assertEquals(JSON.valueToTree(entry.getValue()), JSON.valueToTree(actual.get(entry.getKey())), entry.getKey());
        }
    }

    @SuppressWarnings("unchecked")
    static void merge(Map<String, Object> target, Map<String, Object> patch) {
        patch.forEach((key, value) -> {
            if (value instanceof Map<?, ?> && target.get(key) instanceof Map<?, ?>) merge((Map<String, Object>) target.get(key), viewMap(value));
            else target.put(key, deepCopy(value));
        });
    }
}
