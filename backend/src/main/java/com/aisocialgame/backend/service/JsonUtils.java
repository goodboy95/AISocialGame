package com.aisocialgame.backend.service;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

public final class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonUtils() {}

    public static String toJson(Map<String, Object> map) {
        if (map == null) {
            return "{}";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化 JSON", e);
        }
    }

    public static String toJsonObject(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化 JSON", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法解析 JSON", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法解析 JSON", e);
        }
    }

    public static <T> T convert(Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(value, TypeFactory.defaultInstance().constructType(type));
    }
}
