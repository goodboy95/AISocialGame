package com.aisocialgame.backend.controller;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * REST endpoints exposing room management operations.  Most methods are intentionally thin wrappers
 * around {@link RoomService}, but we still log the intent of the incoming requests here so that
 * troubleshooting failed calls is straightforward.
 */
@RestController
@RequestMapping("/rooms")
public class RoomController {

    private static final Logger log = LoggerFactory.getLogger(RoomController.class);

    private final RoomService roomService;
    private final AuthService authService;

    public RoomController(RoomService roomService, AuthService authService) {
        this.roomService = roomService;
        this.authService = authService;
    }

    /**
     * Returns a paginated list of rooms.  The method performs parameter validation and logging so
     * that operators can correlate HTTP traffic with service-level events.
     */
    @GetMapping("/")
    public RoomDtos.PaginatedRooms listRooms(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "is_private", required = false) Boolean isPrivate,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "page_size", required = false, defaultValue = "20") int pageSize) {
        log.debug("Listing rooms with search='{}', status='{}', isPrivate={}, page={}, pageSize={}",
                search, status, isPrivate, page, pageSize);
        Page<Room> rooms = roomService.listRooms(search, status, isPrivate, PageRequest.of(Math.max(page - 1, 0), pageSize));
        return new RoomDtos.PaginatedRooms(
                rooms.getTotalElements(),
                null,
                null,
                rooms.getContent().stream().map(roomService::toListItem).toList());
    }

    /**
     * Creates a new room owned by the currently authenticated user.
     */
    @PostMapping("/")
    public ResponseEntity<RoomDtos.RoomDetail> createRoom(@RequestBody RoomDtos.CreateRoomRequest request) {
        UserAccount current = authService.currentUser();
        if (current == null) {
            log.debug("Anonymous user attempted to create a room");
            return ResponseEntity.status(401).build();
        }
        Map<String, Object> config = request.config();
        log.info("User {} creating room with name='{}'", current.getId(), request.name());
        Room room = roomService.createRoom(current, request.name(), request.maxPlayers(), request.isPrivate(), config);
        return ResponseEntity.ok(roomService.toRoomDetail(room, current));
    }

    /**
     * Fetches detailed room information.
     */
    @GetMapping("/{id}/")
    public ResponseEntity<RoomDtos.RoomDetail> getRoom(@PathVariable("id") long id) {
        UserAccount current = authService.currentUser();
        return roomService.findById(id)
                .map(room -> ResponseEntity.ok(roomService.toRoomDetail(room, current)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Attempts to join the room with the currently authenticated user.
     */
    @PostMapping("/{id}/join/")
    public ResponseEntity<RoomDtos.RoomDetail> joinRoom(@PathVariable("id") long id) {
        UserAccount current = authService.currentUser();
        if (current == null) {
            log.debug("Anonymous user attempted to join room {}", id);
            return ResponseEntity.status(401).build();
        }
        log.info("User {} joining room {}", current.getId(), id);
        return roomService.findById(id)
                .map(room -> ResponseEntity.ok(roomService.toRoomDetail(roomService.joinRoom(room, current), current)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Same as {@link #joinRoom(long)} but using the short join code.
     */
    @PostMapping("/join-by-code/")
    public ResponseEntity<RoomDtos.RoomDetail> joinByCode(@RequestBody RoomDtos.JoinByCodeRequest request) {
        UserAccount current = authService.currentUser();
        if (current == null) {
            log.debug("Anonymous user attempted to join room by code");
            return ResponseEntity.status(401).build();
        }
        log.info("User {} joining room by code {}", current.getId(), request.code());
        return roomService.findByCode(request.code())
                .map(room -> ResponseEntity.ok(roomService.toRoomDetail(roomService.joinRoom(room, current), current)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Allows the current user to leave a room.
     */
    @PostMapping("/{id}/leave/")
    public ResponseEntity<RoomDtos.RoomDetail> leaveRoom(@PathVariable("id") long id) {
        UserAccount current = authService.currentUser();
        if (current == null) {
            log.debug("Anonymous user attempted to leave room {}", id);
            return ResponseEntity.status(401).build();
        }
        log.info("User {} leaving room {}", current.getId(), id);
        return roomService.findById(id)
                .map(room -> ResponseEntity.ok(roomService.toRoomDetail(roomService.leaveRoom(room, current), current)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Starts the game session.  Only the room owner is permitted to trigger this transition.
     */
    @PostMapping("/{id}/start/")
    public ResponseEntity<RoomDtos.RoomDetail> startRoom(@PathVariable("id") long id) {
        UserAccount current = authService.currentUser();
        if (current == null) {
            log.debug("Anonymous user attempted to start room {}", id);
            return ResponseEntity.status(401).build();
        }
        return roomService.findById(id)
                .map(room -> {
                    if (room.getOwner() == null || !room.getOwner().getId().equals(current.getId())) {
                        log.debug("User {} attempted to start room {} without ownership", current.getId(), id);
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<RoomDtos.RoomDetail>build();
                    }
                    log.info("User {} starting room {}", current.getId(), id);
                    Room updated = roomService.startRoom(room);
                    return ResponseEntity.ok(roomService.toRoomDetail(updated, current));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Adds an AI player to the specified room.
     */
    @PostMapping("/{id}/add-ai/")
    public ResponseEntity<RoomDtos.RoomDetail> addAi(@PathVariable("id") long id, @RequestBody RoomDtos.AddAiRequest request) {
        UserAccount current = authService.currentUser();
        if (current == null) {
            log.debug("Anonymous user attempted to add an AI to room {}", id);
            return ResponseEntity.status(401).build();
        }
        return roomService.findById(id)
                .map(room -> {
                    if (room.getOwner() == null || !room.getOwner().getId().equals(current.getId())) {
                        log.debug("User {} attempted to add AI to room {} without ownership", current.getId(), id);
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<RoomDtos.RoomDetail>build();
                    }
                    log.info("User {} adding AI (style='{}') to room {}", current.getId(), request.style(), id);
                    Room updated = roomService.addAiPlayer(room, request.style(), request.displayName());
                    return ResponseEntity.ok(roomService.toRoomDetail(updated, current));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Removes a player from the room.  Only the owner can perform the action.
     */
    @PostMapping("/{id}/kick/")
    public ResponseEntity<RoomDtos.RoomDetail> kickPlayer(
            @PathVariable("id") long id, @RequestBody RoomDtos.KickPlayerRequest request) {
        UserAccount current = authService.currentUser();
        if (current == null) {
            log.debug("Anonymous user attempted to kick a player from room {}", id);
            return ResponseEntity.status(401).build();
        }
        return roomService.findById(id)
                .map(room -> {
                    if (room.getOwner() == null || !room.getOwner().getId().equals(current.getId())) {
                        log.debug("User {} attempted to kick from room {} without ownership", current.getId(), id);
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<RoomDtos.RoomDetail>build();
                    }
                    log.info("User {} kicking player {} from room {}", current.getId(), request.playerId(), id);
                    Room updated = roomService.kickPlayer(room, request.playerId());
                    return ResponseEntity.ok(roomService.toRoomDetail(updated, current));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Deletes a room permanently.
     */
    @DeleteMapping("/{id}/")
    public ResponseEntity<Void> deleteRoom(@PathVariable("id") long id) {
        UserAccount current = authService.currentUser();
        if (current == null) {
            log.debug("Anonymous user attempted to delete room {}", id);
            return ResponseEntity.status(401).build();
        }
        Optional<Room> optionalRoom = roomService.findById(id);
        if (optionalRoom.isEmpty()) {
            log.debug("User {} attempted to delete missing room {}", current.getId(), id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Room room = optionalRoom.get();
        if (room.getOwner() == null || !room.getOwner().getId().equals(current.getId())) {
            log.debug("User {} attempted to delete room {} without ownership", current.getId(), id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.warn("User {} deleting room {}", current.getId(), id);
        roomService.removeRoom(room);
        return ResponseEntity.noContent().build();
    }
}
