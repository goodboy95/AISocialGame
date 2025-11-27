package com.aisocialgame.dto;

import com.aisocialgame.model.Room;
import com.aisocialgame.model.RoomSeat;

public class JoinRoomResult {
    private final Room room;
    private final RoomSeat seat;

    public JoinRoomResult(Room room, RoomSeat seat) {
        this.room = room;
        this.seat = seat;
    }

    public Room getRoom() {
        return room;
    }

    public RoomSeat getSeat() {
        return seat;
    }
}
