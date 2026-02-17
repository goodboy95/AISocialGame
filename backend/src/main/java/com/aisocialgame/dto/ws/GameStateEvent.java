package com.aisocialgame.dto.ws;

public record GameStateEvent(
        String type,
        String phase,
        int round,
        Integer currentSeat,
        Object payload
) {
}
