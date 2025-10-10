package com.aisocialgame.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aisocialgame.backend.entity.Room;
import com.aisocialgame.backend.entity.RoomPlayer;
import com.aisocialgame.backend.entity.UserAccount;

public interface RoomPlayerRepository extends JpaRepository<RoomPlayer, Long> {
    List<RoomPlayer> findByRoomOrderBySeatNumberAsc(Room room);

    Optional<RoomPlayer> findByRoomAndUser(Room room, UserAccount user);

    int countByRoom(Room room);

    Optional<RoomPlayer> findFirstByRoomOrderBySeatNumberDesc(Room room);
}
