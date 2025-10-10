package com.aisocialgame.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class RoomDtos {

    private RoomDtos() {}

    public record RoomOwner(long id, String username, String displayName) {}

    public record RoomPlayer(
            long id,
            @JsonProperty("user_id") Long userId,
            String username,
            String displayName,
            int seatNumber,
            boolean isHost,
            boolean isAi,
            boolean isActive,
            String joinedAt,
            String role,
            String word,
            boolean isAlive,
            boolean hasUsedSkill,
            @JsonInclude(JsonInclude.Include.NON_NULL) String aiStyle) {}

    public record SessionTimer(
            String phase,
            long duration,
            String expiresAt,
            Map<String, Object> defaultAction,
            String description,
            Map<String, Object> metadata) {}

    public record GameSessionSnapshot(
            long id,
            String engine,
            String phase,
            int round,
            Long currentPlayerId,
            String status,
            String startedAt,
            String updatedAt,
            String deadlineAt,
            SessionTimer timer,
            Map<String, Object> state) {}

    public record RoomListItem(
            long id,
            String name,
            String code,
            RoomOwner owner,
            String status,
            String statusDisplay,
            String phase,
            String phaseDisplay,
            String engine,
            int maxPlayers,
            int currentRound,
            boolean isPrivate,
            int playerCount,
            String createdAt,
            String updatedAt) {}

    public record RoomDetail(
            long id,
            String name,
            String code,
            RoomOwner owner,
            String status,
            String statusDisplay,
            String phase,
            String phaseDisplay,
            String engine,
            int maxPlayers,
            int currentRound,
            boolean isPrivate,
            int playerCount,
            String createdAt,
            String updatedAt,
            Map<String, Object> config,
            List<RoomPlayer> players,
            boolean isMember,
            boolean isOwner,
            GameSessionSnapshot gameSession) {}

    public record PaginatedRooms(
            long count,
            String next,
            String previous,
            List<RoomListItem> results) {}

    public record CreateRoomRequest(
            String name,
            @JsonProperty("max_players") int maxPlayers,
            @JsonProperty("is_private") boolean isPrivate,
            Map<String, Object> config) {}

    public record JoinByCodeRequest(String code) {}

    public record AddAiRequest(
            String style,
            @JsonProperty("display_name") String displayName) {}

    public record KickPlayerRequest(@JsonProperty("player_id") long playerId) {}
}
