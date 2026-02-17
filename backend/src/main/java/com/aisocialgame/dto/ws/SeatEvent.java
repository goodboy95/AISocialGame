package com.aisocialgame.dto.ws;

import com.aisocialgame.model.RoomSeat;

public record SeatEvent(
        String type,
        RoomSeat seat
) {
}
