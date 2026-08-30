package com.fortell.boardgame.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JsonUtils {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public JsonUtils(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize JSON", exception);
        }
    }

    public Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize JSON", exception);
        }
    }
}
