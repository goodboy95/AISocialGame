package com.aisocialgame.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.aisocialgame.backend.dto.RoomDtos;
import com.aisocialgame.backend.entity.GameSession;

@Component
public class GameSessionMapper {

    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;

    public RoomDtos.GameSessionSnapshot toSnapshot(GameSession session) {
        Map<String, Object> state = new HashMap<>(JsonUtils.fromJson(session.getStateJson()));
        RoomDtos.SessionTimer timer = extractTimer(session, state);
        return new RoomDtos.GameSessionSnapshot(
                session.getId(),
                session.getEngine(),
                session.getPhase(),
                session.getRoundNumber(),
                session.getCurrentPlayerId(),
                session.getStatus().name().toLowerCase(),
                formatter.format(session.getStartedAt()),
                formatter.format(session.getUpdatedAt()),
                session.getDeadlineAt() != null ? formatter.format(session.getDeadlineAt()) : null,
                timer,
                state);
    }

    @SuppressWarnings("unchecked")
    private RoomDtos.SessionTimer extractTimer(GameSession session, Map<String, Object> state) {
        Object rawTimer = state.get("timer");
        if (!(rawTimer instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> timerMap = new HashMap<>();
        map.forEach((key, value) -> timerMap.put(String.valueOf(key), value));
        String phase = stringValue(timerMap, "phase", session.getPhase());
        long duration = numberValue(timerMap, "duration", 0L);
        String expiresAt = session.getDeadlineAt() != null ? formatter.format(session.getDeadlineAt()) : null;
        Map<String, Object> defaultAction = mapValue(timerMap, "defaultAction", "default_action");
        String description = stringValue(timerMap, "description", null);
        Map<String, Object> metadata = mapValue(timerMap, "metadata");
        if (duration <= 0 && session.getDeadlineAt() != null) {
            duration = Math.max(Duration.between(session.getUpdatedAt(), session.getDeadlineAt()).toSeconds(), 0);
        }
        return new RoomDtos.SessionTimer(
                phase,
                duration,
                expiresAt,
                defaultAction != null ? Collections.unmodifiableMap(defaultAction) : null,
                description,
                metadata != null ? Collections.unmodifiableMap(metadata) : null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Map<?, ?> raw) {
                Map<String, Object> normalized = new HashMap<>();
                raw.forEach((itemKey, itemValue) -> normalized.put(String.valueOf(itemKey), itemValue));
                return normalized;
            }
        }
        return null;
    }

    private String stringValue(Map<String, Object> source, String key, String fallback) {
        Object value = source.get(key);
        if (value == null) {
            return fallback;
        }
        String result = String.valueOf(value);
        return result.isBlank() ? fallback : result;
    }

    private long numberValue(Map<String, Object> source, String key, long fallback) {
        Object value = source.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
