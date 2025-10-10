package com.aisocialgame.backend.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            String username,
            String email,
            String password,
            @JsonProperty("display_name") String displayName) {}

    public record TokenRequest(String username, String password) {}

    public record TokenResponse(String access, String refresh) {}

    public record RefreshRequest(String refresh) {}

    public record LogoutRequest(String refresh) {}

    public record UserProfile(
            long id,
            String username,
            String email,
            @JsonProperty("display_name") String displayName,
            String avatar,
            String bio) {}

    public record UserMembershipSnapshot(
            long roomId,
            String roomName,
            String roomCode,
            String status,
            String joinedAt,
            boolean isHost,
            boolean isAi,
            String aiStyle,
            String role,
            String word,
            boolean alive) {}

    public record UserOwnedRoomSnapshot(
            long id,
            String name,
            String code,
            String createdAt,
            String status) {}

    public record UserStatistics(int joinedRooms, int ownedRooms) {}

    public record UserExport(
            @JsonProperty("exported_at") Instant exportedAt,
            UserProfile profile,
            List<UserMembershipSnapshot> memberships,
            List<UserOwnedRoomSnapshot> ownedRooms,
            UserStatistics statistics) {}
}
