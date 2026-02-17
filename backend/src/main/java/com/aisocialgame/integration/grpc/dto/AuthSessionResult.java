package com.aisocialgame.integration.grpc.dto;

public record AuthSessionResult(
        ExternalUserProfile user,
        String accessToken,
        String sessionId
) {
}
