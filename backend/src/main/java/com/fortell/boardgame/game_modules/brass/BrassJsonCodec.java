package com.fortell.boardgame.game_modules.brass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Json Codec rules; all working state is passed explicitly. */
final class BrassJsonCodec {
    private BrassJsonCodec() {}

    /** Read-only access for value-object decoding; callers must not mutate this view. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> viewMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    static <T> T immutableData(T value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(key, immutableData(item)));
            return (T) java.util.Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(item -> copy.add(immutableData(item)));
            return (T) java.util.Collections.unmodifiableList(copy);
        }
        return value;
    }

    static List<Map<String, Object>> listCopy(List<Map<String, Object>> value) {
        List<Map<String, Object>> copied = new ArrayList<>();
        for (Map<String, Object> item : value) {
            copied.add(new LinkedHashMap<>(item));
        }
        return copied;
    }

    static Map<String, Object> staticMapOf(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    static List<Map<String, Object>> staticListOf(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> copied = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    copied.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
            return copied;
        }
        return new ArrayList<>();
    }

    static List<Object> staticObjectList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    static int parseStaticInteger(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    static int firstNumber(String value, int fallback) {
        String digits = value.replaceAll("\\D+", " ").trim();
        if (digits.isBlank()) {
            return fallback;
        }
        return parseInteger(digits.split(" ")[0], fallback);
    }

    static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }

    static int parseInteger(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    static Map<String, Object> mapOf(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    static List<Map<String, Object>> listOf(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> copied = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    copied.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
            return copied;
        }
        return new ArrayList<>();
    }

    static List<Object> objectList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    static List<Long> longList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(BrassJsonCodec::asLong).toList();
        }
        return new ArrayList<>();
    }

    static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return new ArrayList<>();
    }

    static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copied = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copied.put(String.valueOf(entry.getKey()), deepCopy(entry.getValue()));
            }
            return copied;
        }
        if (value instanceof List<?> list) {
            List<Object> copied = new ArrayList<>();
            for (Object item : list) {
                copied.add(deepCopy(item));
            }
            return copied;
        }
        return value;
    }
}
