package com.aisocialgame.backend.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.aisocialgame.backend.entity.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByCodeIgnoreCase(String code);

    Page<Room> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Room> findByStatus(Room.Status status, Pageable pageable);

    Page<Room> findByIsPrivate(boolean isPrivate, Pageable pageable);

    Page<Room> findByNameContainingIgnoreCaseAndStatusAndIsPrivate(
            String name, Room.Status status, boolean isPrivate, Pageable pageable);
}
