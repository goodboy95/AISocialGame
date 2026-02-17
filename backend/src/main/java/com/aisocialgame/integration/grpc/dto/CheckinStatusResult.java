package com.aisocialgame.integration.grpc.dto;

import java.time.Instant;

public record CheckinStatusResult(
        boolean checkedInToday,
        Instant lastCheckinDate,
        long tokensGrantedToday
) {
}
