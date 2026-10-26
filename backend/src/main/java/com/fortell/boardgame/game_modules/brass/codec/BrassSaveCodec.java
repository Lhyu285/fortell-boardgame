package com.fortell.boardgame.game_modules.brass.codec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fortell.boardgame.game_modules.brass.model.BrassRuleBook;
import com.fortell.boardgame.game_modules.brass.model.BrassSnapshot;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/** Lossless compatibility boundary. It does not validate game legality or invoke rule services. */
public final class BrassSaveCodec {
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {};
    private static final int MAX_DEPTH = 128;
    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);

    public BrassSaveCodec() {
        // A malformed "false"/1 must not silently become a different boolean rule flag.
        mapper.coercionConfigFor(LogicalType.Boolean)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
    }

    /** Typed values plus privately owned wire shape (absent/null, numeric representation, unknown fields).
     * The preserved document is never exposed as a second mutable source of business truth.
     */
    public static final class Decoded<T> {
        private final T value;
        private final T baseline;
        private final Map<String, Object> original;
        private final Class<T> type;

        private Decoded(T value, T baseline, Map<String, Object> original, Class<T> type) {
            this.value = Objects.requireNonNull(value);
            this.baseline = baseline;
            this.original = original;
            this.type = type;
        }
        public T value() { return value; }
        public Decoded<T> withValue(T next) {
            if (!type.isInstance(next)) throw new IllegalArgumentException("Decoded value must remain " + type.getSimpleName());
            return new Decoded<>(next, baseline, original, type);
        }
    }

    public Decoded<BrassSnapshot> readState(Map<String, Object> json) { return read(json, BrassSnapshot.class); }
    public Map<String, Object> writeState(Decoded<BrassSnapshot> decoded) { return write(decoded); }
    public Decoded<BrassRuleBook> readRules(Map<String, Object> json) { return read(json, BrassRuleBook.class); }
    public Map<String, Object> writeRules(Decoded<BrassRuleBook> decoded) { return write(decoded); }

    private <T> Decoded<T> read(Map<String, Object> source, Class<T> type) {
        if (source == null) throw error("$", "Expected a JSON object");
        Map<String, Object> original = object(copy(source, "$", new IdentityHashMap<>(), 0, true));
        try {
            T value = mapper.convertValue(original, type);
            return new Decoded<>(value, value, original, type);
        } catch (IllegalArgumentException failure) {
            String path = "$";
            Throwable cause = failure.getCause();
            if (cause instanceof JsonMappingException mapping) for (var reference : mapping.getPath()) {
                path += reference.getFieldName() == null ? "[" + reference.getIndex() + "]" : "." + reference.getFieldName();
            }
            throw new CodecException(path, "Cannot decode " + type.getSimpleName(), failure);
        }
    }

    private <T> Map<String, Object> write(Decoded<T> decoded) {
        Objects.requireNonNull(decoded, "decoded");
        Map<String, Object> before = mapper.convertValue(decoded.baseline, OBJECT);
        Map<String, Object> after = mapper.convertValue(decoded.value, OBJECT);
        return object(merge(decoded.original, before, after, "$"));
    }

    /** Only changed, model-owned fields are written. Unchanged fields retain their exact old shape. */
    private Object merge(Object original, Object before, Object after, String path) {
        if (Objects.equals(before, after)) return mutableCopy(original, path);
        if (after instanceof Map<?, ?> current) {
            Map<String, Object> result = original instanceof Map<?, ?> ? object(mutableCopy(original, path)) : new LinkedHashMap<>();
            Map<String, Object> previous = before instanceof Map<?, ?> ? object(before) : Map.of();
            Map<String, Object> raw = original instanceof Map<?, ?> ? object(original) : Map.of();
            for (String key : previous.keySet()) if (!current.containsKey(key)) result.remove(key);
            for (var entry : current.entrySet()) {
                String key = (String) entry.getKey();
                if (!previous.containsKey(key) || !Objects.equals(previous.get(key), entry.getValue())) {
                    result.put(key, merge(raw.get(key), previous.get(key), entry.getValue(), path + "." + key));
                }
            }
            return result;
        }
        if (after instanceof List<?> current) {
            List<?> previous = before instanceof List<?> values ? values : List.of();
            List<?> raw = original instanceof List<?> values ? values : List.of();
            Map<String, Object> previousById = indexed(previous, path), rawById = indexed(raw, path);
            indexed(current, path); // Ambiguous identities must not silently copy another element's extension data.
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < current.size(); i++) {
                Object value = current.get(i);
                String id = identity(value);
                Object old = id == null ? at(previous, i) : previousById.get(id);
                Object wire = id == null ? at(raw, i) : rawById.get(id);
                result.add(merge(wire, old, value, path + "[" + i + "]"));
            }
            return result;
        }
        return mutableCopy(after, path);
    }

    private Map<String, Object> indexed(List<?> values, String path) {
        Map<String, Object> index = new LinkedHashMap<>();
        for (Object value : values) {
            String id = identity(value);
            if (id != null && index.putIfAbsent(id, value) != null) throw error(path, "Duplicate element identity " + id);
        }
        return index;
    }

    private String identity(Object value) {
        if (!(value instanceof Map<?, ?> map)) return null;
        for (String key : List.of("id", "userId", "key", "playerId")) {
            Object id = map.get(key);
            if (id != null && !String.valueOf(id).isBlank()) return key + ":" + id;
        }
        if (map.get("from") != null && map.get("to") != null) return "route:" + map.get("from") + "\u0000" + map.get("to");
        return null;
    }

    private static Object at(List<?> values, int index) { return index < values.size() ? values.get(index) : null; }
    private static Object mutableCopy(Object value, String path) { return copy(value, path, new IdentityHashMap<>(), 0, false); }

    private static Object copy(Object value, String path, IdentityHashMap<Object, Boolean> ancestors, int depth, boolean freeze) {
        if (depth > MAX_DEPTH) throw error(path, "JSON nesting exceeds " + MAX_DEPTH);
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Integer
                || value instanceof Long || value instanceof Short || value instanceof Byte
                || value instanceof BigInteger || value instanceof BigDecimal) return value;
        if (value instanceof Double number && Double.isFinite(number)) return number;
        if (value instanceof Float number && Float.isFinite(number)) return number;
        if (!(value instanceof Map<?, ?>) && !(value instanceof List<?>)) throw error(path, "Unsupported JSON value");
        if (ancestors.put(value, true) != null) throw error(path, "Cyclic JSON value");
        try {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (var entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) throw error(path, "JSON object key must be a string");
                    result.put(key, copy(entry.getValue(), path + "." + key, ancestors, depth + 1, freeze));
                }
                return freeze ? Collections.unmodifiableMap(result) : result;
            }
            List<Object> result = new ArrayList<>();
            List<?> list = (List<?>) value;
            for (int i = 0; i < list.size(); i++) result.add(copy(list.get(i), path + "[" + i + "]", ancestors, depth + 1, freeze));
            return freeze ? Collections.unmodifiableList(result) : result;
        } finally { ancestors.remove(value); }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) { return (Map<String, Object>) value; }
    private static CodecException error(String path, String message) { return new CodecException(path, message, null); }

    public static final class CodecException extends IllegalArgumentException {
        private final String path;
        private CodecException(String path, String message, Throwable cause) { super(message + " at " + path, cause); this.path = path; }
        public String path() { return path; }
    }
}
