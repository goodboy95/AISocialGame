package com.aisocialgame.backend.service;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.aisocialgame.backend.entity.GameSession;
import com.aisocialgame.backend.entity.Room;
import com.aisocialgame.backend.entity.RoomPlayer;
import com.aisocialgame.backend.entity.UserAccount;
import com.aisocialgame.backend.repository.GameSessionRepository;
import com.aisocialgame.backend.repository.RoomPlayerRepository;
import com.aisocialgame.backend.repository.RoomRepository;
import com.aisocialgame.backend.service.game.UndercoverGameManager;

@Service
public class RoomGameEventService {

    private static final Logger log = LoggerFactory.getLogger(RoomGameEventService.class);

    private final RoomRepository roomRepository;
    private final RoomPlayerRepository roomPlayerRepository;
    private final GameSessionRepository gameSessionRepository;
    private final UndercoverGameManager undercoverGameManager;

    public RoomGameEventService(
            RoomRepository roomRepository,
            RoomPlayerRepository roomPlayerRepository,
            GameSessionRepository gameSessionRepository,
            UndercoverGameManager undercoverGameManager) {
        this.roomRepository = roomRepository;
        this.roomPlayerRepository = roomPlayerRepository;
        this.gameSessionRepository = gameSessionRepository;
        this.undercoverGameManager = undercoverGameManager;
    }

    public boolean handleEvent(long roomId, UserAccount user, String event, Map<String, Object> payload) {
        if (event == null || user == null) {
            return false;
        }
        return switch (event) {
            case "submit_speech" -> handleSubmitSpeech(roomId, user, payload);
            case "update_speech_draft" -> handleUpdateDraft(roomId, user, payload);
            default -> false;
        };
    }

    private boolean handleSubmitSpeech(long roomId, UserAccount user, Map<String, Object> payload) {
        GameContext context = resolveContext(roomId, user);
        if (context == null) {
            return false;
        }
        if (!"undercover".equalsIgnoreCase(context.session().getEngine())) {
            return false;
        }
        String content = Optional.ofNullable(payload)
                .map(map -> map.get("payload"))
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(map -> map.get("content"))
                .map(String::valueOf)
                .orElse("");
        undercoverGameManager.submitSpeech(context.session().getId(), context.player().getId(), false, false, content);
        return true;
    }

    private boolean handleUpdateDraft(long roomId, UserAccount user, Map<String, Object> payload) {
        GameContext context = resolveContext(roomId, user);
        if (context == null) {
            return false;
        }
        if (!"undercover".equalsIgnoreCase(context.session().getEngine())) {
            return false;
        }
        String content = Optional.ofNullable(payload)
                .map(map -> map.get("payload"))
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(map -> map.get("content"))
                .map(String::valueOf)
                .orElse("");
        undercoverGameManager.updateSpeechDraft(context.session().getId(), context.player().getId(), content);
        return true;
    }

    private GameContext resolveContext(long roomId, UserAccount user) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            log.debug("Game event ignored because room {} is missing", roomId);
            return null;
        }
        RoomPlayer player = roomPlayerRepository.findByRoomAndUser(room, user).orElse(null);
        if (player == null) {
            log.debug("User {} is not a member of room {}", user.getId(), roomId);
            return null;
        }
        GameSession session = gameSessionRepository.findFirstByRoomOrderByStartedAtDesc(room).orElse(null);
        if (session == null) {
            log.debug("Room {} has no active session", roomId);
            return null;
        }
        return new GameContext(session, player);
    }

    private record GameContext(GameSession session, RoomPlayer player) {
    }
}
