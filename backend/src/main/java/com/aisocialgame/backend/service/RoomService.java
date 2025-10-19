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

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomPlayerRepository roomPlayerRepository;
    private final GameSessionRepository gameSessionRepository;
    private final UserRepository userRepository;
    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
    private final Random random = new SecureRandom();

    public RoomService(
            RoomRepository roomRepository,
            RoomPlayerRepository roomPlayerRepository,
            GameSessionRepository gameSessionRepository,
            UserRepository userRepository) {
        this.roomRepository = roomRepository;
        this.roomPlayerRepository = roomPlayerRepository;
        this.gameSessionRepository = gameSessionRepository;
        this.userRepository = userRepository;
    }

    public Room createRoom(UserAccount owner, String name, int maxPlayers, boolean isPrivate, Map<String, Object> config) {
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
        ensureRoomJoinable(room);
        if (roomPlayerRepository.findByRoomAndUser(room, user).isPresent()) {
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
        return roomRepository.save(room);
    }

    @Transactional
    public Room addAiPlayer(Room room, String style, String displayName) {
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
        return roomRepository.save(room);
    }

    @Transactional
    public Room leaveRoom(Room room, UserAccount user) {
        roomPlayerRepository.findByRoomAndUser(room, user).ifPresent(roomPlayerRepository::delete);
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
        return roomRepository.save(room);
    }

    @Transactional
    public Room startRoom(Room room) {
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
        return roomRepository.save(room);
    }

    @Transactional
    public void removeRoom(Room room) {
        roomRepository.delete(room);
    }

    @Transactional
    public Room kickPlayer(Room room, long playerId) {
        roomPlayerRepository.findById(playerId).ifPresent(player -> {
            if (player.getRoom().getId().equals(room.getId())) {
                roomPlayerRepository.delete(player);
                if (player.isHost()) {
                    roomPlayerRepository.findByRoomOrderBySeatNumberAsc(room).stream()
                            .filter(next -> !next.getId().equals(player.getId()))
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
        });
        room.setUpdatedAt(Instant.now());
        return roomRepository.save(room);
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

    private void ensureRoomJoinable(Room room) {
        if (room.getStatus() != Room.Status.WAITING) {
            throw new IllegalStateException("房间已锁定，无法加入");
        }
        int count = roomPlayerRepository.countByRoom(room);
        if (count >= room.getMaxPlayers()) {
            throw new IllegalStateException("房间人数已满");
        }
    }

    private String extractEngine(Map<String, Object> config) {
        if (config != null && config.containsKey("engine")) {
            return String.valueOf(config.get("engine"));
        }
        return "undercover";
    }

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
            assignments.add(Map.of(
                    "playerId", player.getId(),
                    "displayName", player.getDisplayName(),
                    "isAi", player.isAi(),
                    "isAlive", player.isAlive(),
                    "role", player.getRole(),
                    "word", player.getWord(),
                    "aiStyle", player.getAiStyle()));
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
