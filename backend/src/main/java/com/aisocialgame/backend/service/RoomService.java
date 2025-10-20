package com.aisocialgame.backend.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aisocialgame.backend.dto.RoomDtos;
import com.aisocialgame.backend.entity.GameSession;
import com.aisocialgame.backend.entity.Room;
import com.aisocialgame.backend.entity.RoomPlayer;
import com.aisocialgame.backend.entity.UserAccount;
import com.aisocialgame.backend.repository.GameSessionRepository;
import com.aisocialgame.backend.repository.RoomPlayerRepository;
import com.aisocialgame.backend.repository.RoomRepository;
import com.aisocialgame.backend.repository.UserRepository;
import com.aisocialgame.backend.realtime.RoomRealtimeEvents;

/**
 * Service responsible for the lifecycle of {@link Room} entities.  The class glues together
 * persistence, realtime notifications and the light-weight game bootstrap logic that prepares
 * room specific state.  Most public methods perform small chunks of business logic and then
 * delegate to the repositories, so keeping them well documented helps new contributors quickly
 * grasp the flow.
 */
@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    private final RoomRepository roomRepository;
    private final RoomPlayerRepository roomPlayerRepository;
    private final GameSessionRepository gameSessionRepository;
    private final UserRepository userRepository;
    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
    private final Random random = new SecureRandom();
    private final ApplicationEventPublisher eventPublisher;

    public RoomService(
            RoomRepository roomRepository,
            RoomPlayerRepository roomPlayerRepository,
            GameSessionRepository gameSessionRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.roomRepository = roomRepository;
        this.roomPlayerRepository = roomPlayerRepository;
        this.gameSessionRepository = gameSessionRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates a new room and persists the owner as the first player/host.  A realtime update is not
     * emitted here because the frontend performs an immediate fetch after creation.
     */
    public Room createRoom(UserAccount owner, String name, int maxPlayers, boolean isPrivate, Map<String, Object> config) {
        log.info("Creating room '{}' for user {}", name, owner.getId());
        Room room = new Room();
        room.setOwner(owner);
        room.setName(name);
        room.setMaxPlayers(maxPlayers);
        room.setPrivate(isPrivate);
        room.setEngine(extractEngine(config));
        room.setCode(generateRoomCode());
        room.setConfigJson(JsonUtils.toJson(config));
        room.setCreatedAt(Instant.now());
        room.setUpdatedAt(Instant.now());
        Room saved = roomRepository.save(room);

        RoomPlayer player = new RoomPlayer();
        player.setRoom(saved);
        player.setUser(owner);
        player.setDisplayName(owner.getDisplayName() != null ? owner.getDisplayName() : owner.getUsername());
        player.setSeatNumber(1);
        player.setHost(true);
        player.setAi(false);
        player.setUsername(owner.getUsername());
        roomPlayerRepository.save(player);
        saved.setUpdatedAt(Instant.now());
        roomRepository.save(saved);
        log.debug("Room '{}' (id={}) created with code {}", name, saved.getId(), saved.getCode());
        return saved;
    }

    public Page<Room> listRooms(String search, String status, Boolean isPrivate, Pageable pageable) {
        List<Room> filtered = roomRepository.findAll().stream()
                .filter(room -> search == null || room.getName().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
                .filter(room -> status == null || room.getStatus().name().equalsIgnoreCase(status))
                .filter(room -> isPrivate == null || room.isPrivate() == isPrivate)
                .sorted(Comparator.comparing(Room::getUpdatedAt).reversed())
                .collect(Collectors.toList());
        int fromIndex = Math.min(pageable.getPageNumber() * pageable.getPageSize(), filtered.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), filtered.size());
        List<Room> pageContent = filtered.subList(fromIndex, toIndex);
        return new PageImpl<>(pageContent, pageable, filtered.size());
    }

    public Optional<Room> findById(long id) {
        return roomRepository.findById(id);
    }

    public Optional<Room> findByCode(String code) {
        return roomRepository.findByCodeIgnoreCase(code);
    }

    @Transactional
    public Room joinRoom(Room room, UserAccount user) {
        log.info("User {} attempting to join room {}", user.getId(), room.getId());
        ensureRoomJoinable(room);
        if (roomPlayerRepository.findByRoomAndUser(room, user).isPresent()) {
            log.debug("User {} already in room {}", user.getId(), room.getId());
            return room;
        }
        int seatNumber = roomPlayerRepository.findFirstByRoomOrderBySeatNumberDesc(room)
                .map(RoomPlayer::getSeatNumber)
                .orElse(0) + 1;
        RoomPlayer player = new RoomPlayer();
        player.setRoom(room);
        player.setUser(user);
        player.setDisplayName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
        player.setSeatNumber(seatNumber);
        player.setHost(false);
        player.setAi(false);
        player.setUsername(user.getUsername());
        roomPlayerRepository.save(player);
        room.setUpdatedAt(Instant.now());
        Room saved = roomRepository.save(room);
        publishRoomUpdate(saved, player, "room.player.joined", player.getDisplayName() + " 加入了房间");
        log.info("User {} joined room {} as seat {}", user.getId(), room.getId(), seatNumber);
        return saved;
    }

    @Transactional
    public Room addAiPlayer(Room room, String style, String displayName) {
        log.info("Adding AI player to room {} with style {}", room.getId(), style);
        ensureRoomJoinable(room);
        int seatNumber = roomPlayerRepository.findFirstByRoomOrderBySeatNumberDesc(room)
                .map(RoomPlayer::getSeatNumber)
                .orElse(0) + 1;
        RoomPlayer ai = new RoomPlayer();
        ai.setRoom(room);
        ai.setDisplayName(displayName != null ? displayName : generateAiName(style));
        ai.setSeatNumber(seatNumber);
        ai.setHost(false);
        ai.setAi(true);
        ai.setUsername(null);
        ai.setAiStyle(style);
        roomPlayerRepository.save(ai);
        room.setUpdatedAt(Instant.now());
        Room saved = roomRepository.save(room);
        publishRoomUpdate(saved, ai, "room.ai.joined", ai.getDisplayName() + "（AI）加入了房间");
        log.debug("AI player {} added to room {}", ai.getDisplayName(), room.getId());
        return saved;
    }

    @Transactional
    public Room leaveRoom(Room room, UserAccount user) {
        log.info("User {} leaving room {}", user.getId(), room.getId());
        RoomPlayer leaving = roomPlayerRepository.findByRoomAndUser(room, user).orElse(null);
        if (leaving != null) {
            roomPlayerRepository.delete(leaving);
        }
        if (room.getOwner() != null && room.getOwner().getId().equals(user.getId())) {
            roomPlayerRepository.findByRoomOrderBySeatNumberAsc(room).stream()
                    .filter(player -> player.getUser() != null)
                    .findFirst()
                    .ifPresentOrElse(newOwnerPlayer -> {
                        room.setOwner(newOwnerPlayer.getUser());
                        newOwnerPlayer.setHost(true);
                        roomRepository.save(room);
                    }, () -> room.setOwner(null));
            if (room.getOwner() == null) {
                roomRepository.save(room);
            }
        }
        room.setUpdatedAt(Instant.now());
        Room saved = roomRepository.save(room);
        if (leaving != null) {
            publishRoomUpdate(saved, leaving, "room.player.left", leaving.getDisplayName() + " 离开了房间");
        } else {
            publishRoomUpdate(saved, null, "room.player.left", null);
        }
        log.info("User {} left room {}", user.getId(), room.getId());
        return saved;
    }

    @Transactional
    public Room startRoom(Room room) {
        log.info("Starting room {}", room.getId());
        room.setStatus(Room.Status.PLAYING);
        room.setPhase("playing");
        room.setUpdatedAt(Instant.now());
        GameSession session = new GameSession();
        session.setRoom(room);
        session.setEngine(room.getEngine());
        session.setPhase("setup");
        session.setRoundNumber(1);
        session.setCurrentPlayerId(null);
        session.setStartedAt(Instant.now());
        session.setUpdatedAt(Instant.now());
        session.setStateJson(JsonUtils.toJson(generateInitialState(room)));
        gameSessionRepository.save(session);
        Room saved = roomRepository.save(room);
        RoomPlayer ownerPlayer = null;
        if (room.getOwner() != null) {
            ownerPlayer = roomPlayerRepository.findByRoomAndUser(room, room.getOwner()).orElse(null);
        }
        publishRoomUpdate(saved, ownerPlayer, "room.started", null);
        log.debug("Room {} moved to PLAYING phase", room.getId());
        return saved;
    }

    @Transactional
    public void removeRoom(Room room) {
        log.warn("Removing room {}", room.getId());
        roomRepository.delete(room);
        publishRoomRemoved(room, "room.removed");
    }

    @Transactional
    public Room kickPlayer(Room room, long playerId) {
        log.info("Kicking player {} from room {}", playerId, room.getId());
        RoomPlayer removed = roomPlayerRepository.findById(playerId)
                .filter(player -> player.getRoom().getId().equals(room.getId()))
                .orElse(null);
        if (removed != null) {
            roomPlayerRepository.delete(removed);
            if (removed.isHost()) {
                roomPlayerRepository.findByRoomOrderBySeatNumberAsc(room).stream()
                        .filter(next -> !next.getId().equals(removed.getId()))
                        .findFirst()
                        .ifPresent(next -> {
                            next.setHost(true);
                            if (next.getUser() != null) {
                                room.setOwner(next.getUser());
                            }
                            roomPlayerRepository.save(next);
                        });
            }
        }
        room.setUpdatedAt(Instant.now());
        Room saved = roomRepository.save(room);
        publishRoomUpdate(saved, removed, "room.player.kicked",
                removed != null ? removed.getDisplayName() + " 被移出房间" : null);
        if (removed != null) {
            log.info("Player {} removed from room {}", playerId, room.getId());
        } else {
            log.debug("Player {} not found in room {} during kick request", playerId, room.getId());
        }
        return saved;
    }

    public RoomDtos.RoomDetail toRoomDetail(Room room, UserAccount currentUser) {
        List<RoomPlayer> players = roomPlayerRepository.findByRoomOrderBySeatNumberAsc(room);
        int playerCount = players.size();
        RoomDtos.RoomOwner owner = toOwner(room.getOwner());
        GameSession session = gameSessionRepository.findFirstByRoomOrderByStartedAtDesc(room).orElse(null);
        RoomDtos.GameSessionSnapshot snapshot = session != null ? toSnapshot(session) : null;
        return new RoomDtos.RoomDetail(
                room.getId(),
                room.getName(),
                room.getCode(),
                owner,
                room.getStatus().name().toLowerCase(),
                displayStatus(room.getStatus()),
                room.getPhase(),
                displayPhase(room.getPhase()),
                room.getEngine(),
                room.getMaxPlayers(),
                session != null ? session.getRoundNumber() : 0,
                room.isPrivate(),
                playerCount,
                formatter.format(room.getCreatedAt()),
                formatter.format(room.getUpdatedAt()),
                JsonUtils.fromJson(room.getConfigJson()),
                players.stream().map(this::toPlayer).collect(Collectors.toList()),
                currentUser != null && players.stream().anyMatch(p -> p.getUser() != null && p.getUser().getId().equals(currentUser.getId())),
                currentUser != null && room.getOwner() != null && room.getOwner().getId().equals(currentUser.getId()),
                snapshot);
    }

    public RoomDtos.RoomListItem toListItem(Room room) {
        GameSession session = gameSessionRepository.findFirstByRoomOrderByStartedAtDesc(room).orElse(null);
        int playerCount = roomPlayerRepository.countByRoom(room);
        return new RoomDtos.RoomListItem(
                room.getId(),
                room.getName(),
                room.getCode(),
                toOwner(room.getOwner()),
                room.getStatus().name().toLowerCase(),
                displayStatus(room.getStatus()),
                room.getPhase(),
                displayPhase(room.getPhase()),
                room.getEngine(),
                room.getMaxPlayers(),
                session != null ? session.getRoundNumber() : 0,
                room.isPrivate(),
                playerCount,
                formatter.format(room.getCreatedAt()),
                formatter.format(room.getUpdatedAt()));
    }

    private void publishRoomUpdate(Room room, RoomPlayer actor, String event, String message) {
        if (log.isDebugEnabled()) {
            log.debug("Publishing room update event '{}' for room {}", event, room.getId());
        }
        RoomRealtimeEvents.Actor snapshot = null;
        if (actor != null) {
            snapshot = new RoomRealtimeEvents.Actor(
                    actor.getId(),
                    actor.getUser() != null ? actor.getUser().getId() : null,
                    actor.getUsername(),
                    actor.getDisplayName());
        }
        eventPublisher.publishEvent(new RoomRealtimeEvents.RoomUpdated(room.getId(), snapshot, event, message));
    }

    private void publishRoomRemoved(Room room, String reason) {
        if (log.isDebugEnabled()) {
            log.debug("Publishing room removal event for room {} due to {}", room.getId(), reason);
        }
        eventPublisher.publishEvent(new RoomRealtimeEvents.RoomRemoved(room.getId(), reason));
    }

    private RoomDtos.GameSessionSnapshot toSnapshot(GameSession session) {
        return new RoomDtos.GameSessionSnapshot(
                session.getId(),
                session.getEngine(),
                session.getPhase(),
                session.getRoundNumber(),
                session.getCurrentPlayerId(),
                session.getStatus().name().toLowerCase(),
                formatter.format(session.getStartedAt()),
                formatter.format(session.getUpdatedAt()),
                session.getDeadlineAt() != null ? formatter.format(session.getDeadlineAt()) : null,
                null,
                JsonUtils.fromJson(session.getStateJson()));
    }

    private RoomDtos.RoomPlayer toPlayer(RoomPlayer player) {
        return new RoomDtos.RoomPlayer(
                player.getId(),
                player.getUser() != null ? player.getUser().getId() : null,
                player.getUsername(),
                player.getDisplayName(),
                player.getSeatNumber(),
                player.isHost(),
                player.isAi(),
                player.isActive(),
                formatter.format(player.getJoinedAt()),
                player.getRole(),
                player.getWord(),
                player.isAlive(),
                player.isHasUsedSkill(),
                player.getAiStyle());
    }

    private RoomDtos.RoomOwner toOwner(UserAccount owner) {
        if (owner == null) {
            return null;
        }
        Long ownerId = owner.getId();
        UserAccount resolvedOwner = owner;
        if (ownerId != null) {
            resolvedOwner = userRepository.findById(ownerId).orElse(owner);
        }
        return new RoomDtos.RoomOwner(
                resolvedOwner.getId(),
                resolvedOwner.getUsername(),
                resolvedOwner.getDisplayName());
    }

    /**
     * Validates the room can accept new participants.
     *
     * @throws IllegalStateException when the room is not accepting members anymore.
     */
    private void ensureRoomJoinable(Room room) {
        if (room.getStatus() != Room.Status.WAITING) {
            log.debug("Room {} is not joinable because status is {}", room.getId(), room.getStatus());
            throw new IllegalStateException("房间已锁定，无法加入");
        }
        int count = roomPlayerRepository.countByRoom(room);
        if (count >= room.getMaxPlayers()) {
            log.debug("Room {} is full ({}/{})", room.getId(), count, room.getMaxPlayers());
            throw new IllegalStateException("房间人数已满");
        }
    }

    /**
     * Extracts the configured engine identifier from the provided configuration map.  The backend
     * keeps the parsing logic centralized so both REST and websocket flows remain in sync.
     */
    private String extractEngine(Map<String, Object> config) {
        if (config != null && config.containsKey("engine")) {
            return String.valueOf(config.get("engine"));
        }
        return "undercover";
    }

    /**
     * Generates a six character invite code using a character set that avoids ambiguous symbols.
     */
    private String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        while (true) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            String code = sb.toString();
            if (roomRepository.findByCodeIgnoreCase(code).isEmpty()) {
                return code;
            }
        }
    }

    /**
     * Builds a minimal initial game state for the classic "Undercover" party game so the frontend
     * can render the lobby immediately.  The implementation intentionally keeps the structure
     * simple and deterministic, trading sophistication for predictability while the real engine is
     * being integrated.
     */
    private Map<String, Object> generateInitialState(Room room) {
        Map<String, Object> state = new HashMap<>();
        state.put("phase", "intro");
        state.put("round", 1);
        List<Map<String, Object>> assignments = new ArrayList<>();
        List<RoomPlayer> players = roomPlayerRepository.findByRoomOrderBySeatNumberAsc(room);
        String[] undercoverWords = {"向日葵", "月光", "流星"};
        String[] civilianWords = {"太阳", "夜晚", "星星"};
        for (int i = 0; i < players.size(); i++) {
            RoomPlayer player = players.get(i);
            boolean undercover = i == players.size() - 1 && players.size() > 1;
            player.setRole(undercover ? "undercover" : "civilian");
            player.setWord(undercover ? undercoverWords[i % undercoverWords.length] : civilianWords[i % civilianWords.length]);
            Map<String, Object> assignment = new HashMap<>();
            assignment.put("playerId", player.getId());
            assignment.put("displayName", player.getDisplayName());
            assignment.put("isAi", player.isAi());
            assignment.put("isAlive", player.isAlive());
            assignment.put("role", player.getRole());
            assignment.put("word", player.getWord());
            assignment.put("aiStyle", player.getAiStyle());
            assignments.add(assignment);
        }
        state.put("assignments", assignments);
        state.put("speeches", new ArrayList<>());
        state.put("voteSummary", Map.of("submitted", 0, "required", Math.max(1, players.size() - 1)));
        roomPlayerRepository.saveAll(players);
        return state;
    }

    private String displayStatus(Room.Status status) {
        return switch (status) {
            case WAITING -> "等待中";
            case PLAYING -> "游戏中";
            case FINISHED -> "已结束";
        };
    }

    private String displayPhase(String phase) {
        return switch (phase) {
            case "playing" -> "游戏中";
            case "lobby" -> "大厅";
            default -> "未知";
        };
    }

    private String generateAiName(String style) {
        return (style != null ? style : "AI") + "玩家" + (random.nextInt(90) + 10);
    }
}
