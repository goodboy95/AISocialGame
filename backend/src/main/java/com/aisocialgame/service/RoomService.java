package com.aisocialgame.service;

import com.aisocialgame.dto.JoinRoomResult;
import com.aisocialgame.exception.ApiException;
import com.aisocialgame.model.Game;
import com.aisocialgame.model.Persona;
import com.aisocialgame.model.Room;
import com.aisocialgame.model.RoomSeat;
import com.aisocialgame.model.RoomStatus;
import com.aisocialgame.model.User;
import com.aisocialgame.repository.PersonaRepository;
import com.aisocialgame.repository.RoomRepository;
import com.aisocialgame.dto.ws.SeatEvent;
import com.aisocialgame.websocket.GamePushService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class RoomService {
    private final RoomRepository roomRepository;
    private final GameService gameService;
    private final PersonaRepository personaRepository;
    private final AiNameService aiNameService;
    private final GamePushService gamePushService;

    public RoomService(RoomRepository roomRepository,
                       GameService gameService,
                       PersonaRepository personaRepository,
                       AiNameService aiNameService,
                       GamePushService gamePushService) {
        this.roomRepository = roomRepository;
        this.gameService = gameService;
        this.personaRepository = personaRepository;
        this.aiNameService = aiNameService;
        this.gamePushService = gamePushService;
    }

    public Room createRoom(String gameId, String name, boolean isPrivate, String password, String commMode, Map<String, Object> config, User creator) {
        Game game = gameService.findById(gameId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "游戏不存在"));
        int maxPlayers = resolveMaxPlayers(config, game.getMaxPlayers());

        Room room = new Room(UUID.randomUUID().toString(), gameId, name, RoomStatus.WAITING, maxPlayers, isPrivate, password, commMode, config != null ? config : new HashMap<>());

        // Auto seat creator as host
        if (creator != null) {
            RoomSeat host = new RoomSeat(0, creator.getId(), creator.getNickname(), false, null, creator.getAvatar(), true, true);
            room.getSeats().add(host);
        }

        return roomRepository.save(room);
    }

    @Transactional(readOnly = true)
    public List<Room> listByGame(String gameId) {
        return roomRepository.findByGameIdOrderByCreatedAtAsc(gameId);
    }

    @Transactional(readOnly = true)
    public Room getRoom(String roomId) {
        return roomRepository.findById(roomId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "房间不存在"));
    }

    public synchronized JoinRoomResult joinRoom(String roomId, String displayName, User user, String preferredPlayerId) {
        Room room = getRoom(roomId);
        if (room.getSeats().size() >= room.getMaxPlayers()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "房间已满");
        }

        // Already joined
        String userId = user != null ? user.getId() : null;
        if (userId != null) {
            RoomSeat seat = room.getSeats().stream().filter(s -> userId.equals(s.getPlayerId())).findFirst().orElse(null);
            if (seat != null) {
                return new JoinRoomResult(room, seat);
            }
        }

        if (userId == null && preferredPlayerId != null && !preferredPlayerId.isBlank()) {
            RoomSeat seat = room.getSeats().stream().filter(s -> preferredPlayerId.equals(s.getPlayerId())).findFirst().orElse(null);
            if (seat != null) {
                return new JoinRoomResult(room, seat);
            }
        }

        int seatNumber = room.getSeats().size();
        String avatar = user != null ? user.getAvatar() : "https://api.dicebear.com/7.x/avataaars/svg?seed=" + displayName.replace(" ", "");
        String playerId = userId != null ? userId : UUID.randomUUID().toString();
        RoomSeat seat = new RoomSeat(seatNumber, playerId, displayName, false, null, avatar, true, room.getSeats().isEmpty());
        room.getSeats().add(seat);
        roomRepository.save(room);
        gamePushService.pushSeatChange(roomId, new SeatEvent("JOIN", seat));
        return new JoinRoomResult(room, seat);
    }

    public synchronized Room addAi(String roomId, String personaId) {
        Room room = getRoom(roomId);
        if (room.getSeats().size() >= room.getMaxPlayers()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "房间已满");
        }
        Persona persona = personaRepository.findById(personaId);
        if (persona == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI人设不存在");
        }
        int seatNumber = room.getSeats().size();
        String aiDisplayName = aiNameService.generateName(persona);
        RoomSeat seat = new RoomSeat(seatNumber, "ai-" + personaId + "-" + seatNumber, aiDisplayName, true, personaId, persona.getAvatar(), true, false);
        room.getSeats().add(seat);
        roomRepository.save(room);
        gamePushService.pushSeatChange(roomId, new SeatEvent("AI_ADDED", seat));
        return room;
    }

    public void updateStatus(String roomId, RoomStatus status) {
        Room room = getRoom(roomId);
        room.setStatus(status);
        roomRepository.save(room);
    }

    private int resolveMaxPlayers(Map<String, Object> config, int fallback) {
        if (config == null) return fallback;
        Object value = config.get("playerCount");
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }
}
