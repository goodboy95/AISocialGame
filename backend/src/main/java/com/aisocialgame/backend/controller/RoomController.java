package com.aisocialgame.backend.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aisocialgame.backend.dto.RoomDtos;
import com.aisocialgame.backend.entity.Room;
import com.aisocialgame.backend.entity.UserAccount;
import com.aisocialgame.backend.service.AuthService;
import com.aisocialgame.backend.service.RoomService;

@RestController
@RequestMapping("/rooms")
public class RoomController {

    private final RoomService roomService;
    private final AuthService authService;

    public RoomController(RoomService roomService, AuthService authService) {
        this.roomService = roomService;
        this.authService = authService;
    }

    @GetMapping("/")
    public RoomDtos.PaginatedRooms listRooms(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "is_private", required = false) Boolean isPrivate,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "page_size", required = false, defaultValue = "20") int pageSize) {
        Page<Room> rooms = roomService.listRooms(search, status, isPrivate, PageRequest.of(Math.max(page - 1, 0), pageSize));
        return new RoomDtos.PaginatedRooms(
                rooms.getTotalElements(),
                null,
                null,
                rooms.getContent().stream().map(roomService::toListItem).toList());
    }

    @PostMapping("/")
    public ResponseEntity<RoomDtos.RoomDetail> createRoom(@RequestBody RoomDtos.CreateRoomRequest request) {
        UserAccount current = authService.currentUser();
        if (current == null) {
            return ResponseEntity.status(401).build();
        }
        Map<String, Object> config = request.config();
        Room room = roomService.createRoom(current, request.name(), request.maxPlayers(), request.isPrivate(), config);
        return ResponseEntity.ok(roomService.toRoomDetail(room, current));
    }

    @GetMapping("/{id}/")
    public ResponseEntity<RoomDtos.RoomDetail> getRoom(@PathVariable("id") long id) {
        UserAccount current = authService.currentUser();
        return roomService.findById(id)
                .map(room -> ResponseEntity.ok(roomService.toRoomDetail(room, current)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/join/")
    public ResponseEntity<RoomDtos.RoomDetail> joinRoom(@PathVariable("id") long id) {
        UserAccount current = authService.currentUser();
        if (current == null) {
            return ResponseEntity.status(401).build();
        }
        return roomService.findById(id)
                .map(room -> ResponseEntity.ok(roomService.toRoomDetail(roomService.joinRoom(room, current), current)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/join-by-code/")
    public ResponseEntity<RoomDtos.RoomDetail> joinByCode(@RequestBody RoomDtos.JoinByCodeRequest request) {
        UserAccount current = authService.currentUser();
        if (current == null) {
            return ResponseEntity.status(401).build();
        }
        return roomService.findByCode(request.code())
                .map(room -> ResponseEntity.ok(roomService.toRoomDetail(roomService.joinRoom(room, current), current)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/leave/")
    public ResponseEntity<RoomDtos.RoomDetail> leaveRoom(@PathVariable("id") long id) {
        UserAccount current = authService.currentUser();
        if (current == null) {
            return ResponseEntity.status(401).build();
        }
        return roomService.findById(id)
                .map(room -> ResponseEntity.ok(roomService.toRoomDetail(roomService.leaveRoom(room, current), current)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/start/")
    public ResponseEntity<RoomDtos.RoomDetail> startRoom(@PathVariable("id") long id) {
        UserAccount current = authService.currentUser();
        if (current == null) {
            return ResponseEntity.status(401).build();
        }
        return roomService.findById(id)
                .map(room -> {
                    if (room.getOwner() == null || !room.getOwner().getId().equals(current.getId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<RoomDtos.RoomDetail>build();
                    }
                    Room updated = roomService.startRoom(room);
                    return ResponseEntity.ok(roomService.toRoomDetail(updated, current));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/add-ai/")
    public ResponseEntity<RoomDtos.RoomDetail> addAi(@PathVariable("id") long id, @RequestBody RoomDtos.AddAiRequest request) {
        UserAccount current = authService.currentUser();
        if (current == null) {
            return ResponseEntity.status(401).build();
        }
        return roomService.findById(id)
                .map(room -> {
                    if (room.getOwner() == null || !room.getOwner().getId().equals(current.getId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<RoomDtos.RoomDetail>build();
                    }
                    Room updated = roomService.addAiPlayer(room, request.style(), request.displayName());
                    return ResponseEntity.ok(roomService.toRoomDetail(updated, current));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/kick/")
    public ResponseEntity<RoomDtos.RoomDetail> kickPlayer(
            @PathVariable("id") long id, @RequestBody RoomDtos.KickPlayerRequest request) {
        UserAccount current = authService.currentUser();
        if (current == null) {
            return ResponseEntity.status(401).build();
        }
        return roomService.findById(id)
                .map(room -> {
                    if (room.getOwner() == null || !room.getOwner().getId().equals(current.getId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<RoomDtos.RoomDetail>build();
                    }
                    Room updated = roomService.kickPlayer(room, request.playerId());
                    return ResponseEntity.ok(roomService.toRoomDetail(updated, current));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/")
    public ResponseEntity<Void> deleteRoom(@PathVariable("id") long id) {
        UserAccount current = authService.currentUser();
        if (current == null) {
            return ResponseEntity.status(401).build();
        }
        Optional<Room> optionalRoom = roomService.findById(id);
        if (optionalRoom.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Room room = optionalRoom.get();
        if (room.getOwner() == null || !room.getOwner().getId().equals(current.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        roomService.removeRoom(room);
        return ResponseEntity.noContent().build();
    }
}
