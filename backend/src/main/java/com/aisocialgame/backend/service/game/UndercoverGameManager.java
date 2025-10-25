package com.aisocialgame.backend.service.game;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.aisocialgame.backend.dto.RoomDtos;
import com.aisocialgame.backend.entity.GameSession;
import com.aisocialgame.backend.entity.Room;
import com.aisocialgame.backend.entity.RoomPlayer;
import com.aisocialgame.backend.repository.GameSessionRepository;
import com.aisocialgame.backend.repository.RoomPlayerRepository;
import com.aisocialgame.backend.service.GameScheduler;
import com.aisocialgame.backend.service.GameSessionMapper;
import com.aisocialgame.backend.service.JsonUtils;
import com.aisocialgame.backend.realtime.RoomRealtimeEvents;

@Service
public class UndercoverGameManager {

    private static final Logger log = LoggerFactory.getLogger(UndercoverGameManager.class);
    private static final long HUMAN_SPEECH_SECONDS = 60L;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final RoomPlayerRepository roomPlayerRepository;
    private final GameSessionRepository gameSessionRepository;
    private final GameSessionMapper gameSessionMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final GameScheduler scheduler;
    private final TransactionTemplate transactionTemplate;

    private final Map<Long, Map<Long, String>> speechDrafts = new ConcurrentHashMap<>();

    public UndercoverGameManager(
            RoomPlayerRepository roomPlayerRepository,
            GameSessionRepository gameSessionRepository,
            GameSessionMapper gameSessionMapper,
            ApplicationEventPublisher eventPublisher,
            GameScheduler scheduler,
            PlatformTransactionManager transactionManager) {
        this.roomPlayerRepository = roomPlayerRepository;
        this.gameSessionRepository = gameSessionRepository;
        this.gameSessionMapper = gameSessionMapper;
        this.eventPublisher = eventPublisher;
        this.scheduler = scheduler;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void initializeSession(GameSession session) {
        if (session == null || session.getId() == null) {
            return;
        }
        initializeSession(session.getId());
    }

    public void initializeSession(long sessionId) {
        AtomicReference<RoomDtos.GameSessionSnapshot> snapshotRef = new AtomicReference<>();
        AtomicLong roomIdRef = new AtomicLong();
        AtomicLong currentPlayerRef = new AtomicLong(0);
        AtomicBoolean currentIsAi = new AtomicBoolean(false);
        transactionTemplate.executeWithoutResult(status -> {
            GameSession managed = gameSessionRepository.findById(sessionId).orElse(null);
            if (managed == null) {
                log.warn("Attempted to initialize missing session {}", sessionId);
                return;
            }
            Room room = managed.getRoom();
            roomIdRef.set(room.getId());
            List<RoomPlayer> players = roomPlayerRepository.findByRoomOrderBySeatNumberAsc(room);
            UndercoverGameState state = buildInitialState(managed, players);
            Long currentPlayer = determineNextSpeaker(state, null);
            state.setCurrentPlayerId(currentPlayer);
            managed.setPhase("speaking");
            managed.setRoundNumber(1);
            managed.setCurrentPlayerId(currentPlayer);
            UndercoverGameState.TimerPayload timerPayload = null;
            Instant deadline = null;
            UndercoverGameState.Assignment currentAssignment = state.findAssignment(currentPlayer);
            if (currentAssignment != null) {
                currentIsAi.set(currentAssignment.isAi());
                if (!currentAssignment.isAi()) {
                    deadline = Instant.now().plusSeconds(HUMAN_SPEECH_SECONDS);
                    timerPayload = buildTimerPayload(currentPlayer);
                }
            }
            state.setTimer(timerPayload);
            managed.setDeadlineAt(deadline);
            managed.setStateJson(JsonUtils.toJsonObject(state));
            managed.setUpdatedAt(Instant.now());
            roomPlayerRepository.saveAll(players);
            gameSessionRepository.save(managed);
            snapshotRef.set(gameSessionMapper.toSnapshot(managed));
            if (currentPlayer != null) {
                currentPlayerRef.set(currentPlayer);
            }
        });
        RoomDtos.GameSessionSnapshot snapshot = snapshotRef.get();
        long roomId = roomIdRef.get();
        if (snapshot != null && roomId > 0) {
            broadcastEvent(roomId, "undercover.state_initialized", Map.of("session", snapshot));
        }
        long currentPlayerId = currentPlayerRef.get();
        if (roomId > 0 && currentPlayerId > 0) {
            if (currentIsAi.get()) {
                scheduler.executeAsync(() -> triggerAiSpeech(sessionId, currentPlayerId));
            } else {
                scheduler.schedule(sessionId, () -> autoSubmitSpeech(sessionId, currentPlayerId), Duration.ofSeconds(HUMAN_SPEECH_SECONDS));
            }
        }
    }

    public void submitSpeech(long sessionId, long playerId, boolean autoSubmission, boolean fromAi, String rawContent) {
        AtomicReference<RoomDtos.GameSessionSnapshot> snapshotRef = new AtomicReference<>();
        AtomicReference<UndercoverGameState.Speech> speechRef = new AtomicReference<>();
        AtomicLong roomIdRef = new AtomicLong();
        AtomicLong nextPlayerRef = new AtomicLong(0);
        AtomicBoolean nextIsAi = new AtomicBoolean(false);
        transactionTemplate.executeWithoutResult(status -> {
            GameSession session = gameSessionRepository.findById(sessionId).orElse(null);
            if (session == null) {
                log.warn("Attempted to submit speech for missing session {}", sessionId);
                return;
            }
            Room room = session.getRoom();
            roomIdRef.set(room.getId());
            UndercoverGameState state = Optional.ofNullable(JsonUtils.fromJson(session.getStateJson(), UndercoverGameState.class))
                    .orElseGet(UndercoverGameState::new);
            if (!Objects.equals(state.getCurrentPlayerId(), playerId)) {
                log.debug("Ignoring speech from player {} because current speaker is {}", playerId, state.getCurrentPlayerId());
                return;
            }
            UndercoverGameState.Assignment assignment = state.findAssignment(playerId);
            if (assignment == null) {
                log.warn("Player {} not found in session {}", playerId, sessionId);
                return;
            }
            if (!assignment.isAlive()) {
                log.debug("Ignoring speech from eliminated player {}", playerId);
                return;
            }
            if (state.hasSpeech(playerId)) {
                log.debug("Player {} already delivered a speech for session {}", playerId, sessionId);
                return;
            }
            String content = sanitizeContent(rawContent);
            if (content.isBlank()) {
                content = autoSubmission ? "（超时未提交发言）" : "（空白发言）";
            }
            UndercoverGameState.Speech speech = new UndercoverGameState.Speech(
                    playerId,
                    content,
                    assignment.isAi() || fromAi,
                    FORMATTER.format(Instant.now()));
            state.getSpeeches().add(speech);
            speechRef.set(speech);
            removeDraft(sessionId, playerId);
            Long nextPlayer = determineNextSpeaker(state, playerId);
            session.setCurrentPlayerId(nextPlayer);
            state.setCurrentPlayerId(nextPlayer);
            UndercoverGameState.TimerPayload timerPayload = null;
            Instant deadline = null;
            if (nextPlayer != null) {
                UndercoverGameState.Assignment nextAssignment = state.findAssignment(nextPlayer);
                if (nextAssignment != null) {
                    nextIsAi.set(nextAssignment.isAi());
                    if (!nextAssignment.isAi()) {
                        deadline = Instant.now().plusSeconds(HUMAN_SPEECH_SECONDS);
                        timerPayload = buildTimerPayload(nextPlayer);
                    }
                }
            } else {
                speechDrafts.remove(sessionId);
            }
            state.setTimer(timerPayload);
            session.setDeadlineAt(deadline);
            session.setStateJson(JsonUtils.toJsonObject(state));
            session.setUpdatedAt(Instant.now());
            gameSessionRepository.save(session);
            snapshotRef.set(gameSessionMapper.toSnapshot(session));
            if (nextPlayer != null) {
                nextPlayerRef.set(nextPlayer);
            }
        });
        scheduler.cancel(sessionId);
        RoomDtos.GameSessionSnapshot snapshot = snapshotRef.get();
        long roomId = roomIdRef.get();
        if (snapshot != null && roomId > 0) {
            broadcastEvent(roomId, "undercover.state_updated", Map.of("session", snapshot));
        }
        UndercoverGameState.Speech speech = speechRef.get();
        if (speech != null && roomId > 0) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("playerId", speech.getPlayerId());
            payload.put("content", speech.getContent());
            payload.put("isAi", speech.isAi());
            payload.put("timestamp", speech.getTimestamp());
            broadcastEvent(roomId, "undercover.speech_stream", Map.of("payload", payload));
        }
        long nextPlayerId = nextPlayerRef.get();
        if (roomId > 0 && nextPlayerId > 0) {
            if (nextIsAi.get()) {
                scheduler.executeAsync(() -> triggerAiSpeech(sessionId, nextPlayerId));
            } else {
                scheduler.schedule(sessionId, () -> autoSubmitSpeech(sessionId, nextPlayerId), Duration.ofSeconds(HUMAN_SPEECH_SECONDS));
            }
        }
    }

    public void updateSpeechDraft(long sessionId, long playerId, String content) {
        String sanitized = content != null ? content.trim() : "";
        if (sanitized.isEmpty()) {
            removeDraft(sessionId, playerId);
            return;
        }
        speechDrafts.computeIfAbsent(sessionId, id -> new ConcurrentHashMap<>()).put(playerId, sanitized);
    }

    private void triggerAiSpeech(long sessionId, long playerId) {
        try {
            Thread.sleep(1500);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        String content = transactionTemplate.execute(status -> {
            GameSession session = gameSessionRepository.findById(sessionId).orElse(null);
            if (session == null) {
                return null;
            }
            UndercoverGameState state = Optional.ofNullable(JsonUtils.fromJson(session.getStateJson(), UndercoverGameState.class))
                    .orElseGet(UndercoverGameState::new);
            if (!Objects.equals(state.getCurrentPlayerId(), playerId)) {
                return null;
            }
            UndercoverGameState.Assignment assignment = state.findAssignment(playerId);
            if (assignment == null) {
                return null;
            }
            String word = assignment.getWord() != null ? assignment.getWord() : "这一轮的关键词";
            return String.format("%s觉得这个词可以这样描述：%s。", assignment.getDisplayName(), word);
        });
        if (content != null) {
            submitSpeech(sessionId, playerId, false, true, content);
        }
    }

    private void autoSubmitSpeech(long sessionId, long playerId) {
        String draft = getDraft(sessionId, playerId);
        submitSpeech(sessionId, playerId, true, false, draft != null ? draft : "");
    }

    private UndercoverGameState buildInitialState(GameSession session, List<RoomPlayer> players) {
        UndercoverGameState state = new UndercoverGameState();
        state.setPhase("speaking");
        state.setRound(1);
        List<Long> order = determineSpeakingOrder(session, players);
        state.setSpeakingOrder(order);
        List<UndercoverGameState.Assignment> assignments = new ArrayList<>();
        String[] undercoverWords = {"向日葵", "月光", "流星"};
        String[] civilianWords = {"太阳", "夜晚", "星星"};
        for (int i = 0; i < players.size(); i++) {
            RoomPlayer player = players.get(i);
            boolean undercover = i == players.size() - 1 && players.size() > 1;
            player.setRole(undercover ? "undercover" : "civilian");
            player.setWord(undercover
                    ? undercoverWords[i % undercoverWords.length]
                    : civilianWords[i % civilianWords.length]);
            UndercoverGameState.Assignment assignment = new UndercoverGameState.Assignment();
            assignment.setPlayerId(player.getId());
            assignment.setDisplayName(player.getDisplayName());
            assignment.setAi(player.isAi());
            assignment.setAlive(player.isAlive());
            assignment.setRole(player.getRole());
            assignment.setWord(player.getWord());
            assignment.setAiStyle(player.getAiStyle());
            assignments.add(assignment);
        }
        state.setAssignments(assignments);
        UndercoverGameState.VoteSummary voteSummary = new UndercoverGameState.VoteSummary();
        voteSummary.setRequired(Math.max(1, players.size() - 1));
        voteSummary.setSubmitted(0);
        state.setVoteSummary(voteSummary);
        return state;
    }

    private List<Long> determineSpeakingOrder(GameSession session, List<RoomPlayer> players) {
        List<Long> order = players.stream().map(RoomPlayer::getId).toList();
        if (order.size() <= 1) {
            return order;
        }
        long seed = computeSpeakingSeed(session, players);
        Random random = new Random(seed);
        List<Long> shuffled = new ArrayList<>(order);
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Long temp = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, temp);
        }
        return shuffled;
    }

    private long computeSpeakingSeed(GameSession session, List<RoomPlayer> players) {
        long base = session.getId() != null ? session.getId() : 0L;
        long timestamp = session.getStartedAt() != null ? session.getStartedAt().toEpochMilli() : 0L;
        long checksum = 0L;
        for (int i = 0; i < players.size(); i++) {
            RoomPlayer player = players.get(i);
            checksum += (player.getId() != null ? player.getId() : 0L) * (i + 1L);
        }
        long rawSeed = base * 1_000_003L + timestamp + checksum;
        long modulo = 2_147_483_647L;
        long normalized = rawSeed % modulo;
        if (normalized <= 0) {
            normalized = (normalized + modulo - 1) % modulo;
            if (normalized <= 0) {
                normalized = 1;
            }
        }
        return normalized;
    }

    private Long determineNextSpeaker(UndercoverGameState state, Long previous) {
        List<Long> order = state.getSpeakingOrder();
        if (order.isEmpty()) {
            order = state.getAssignments().stream()
                    .map(UndercoverGameState.Assignment::getPlayerId)
                    .toList();
            state.setSpeakingOrder(order);
        }
        int startIndex = 0;
        if (previous != null) {
            int idx = order.indexOf(previous);
            if (idx >= 0) {
                startIndex = idx + 1;
            }
        }
        for (int i = startIndex; i < order.size(); i++) {
            Long candidate = order.get(i);
            UndercoverGameState.Assignment assignment = state.findAssignment(candidate);
            if (assignment != null && assignment.isAlive() && !state.hasSpeech(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private UndercoverGameState.TimerPayload buildTimerPayload(Long playerId) {
        UndercoverGameState.TimerPayload payload = new UndercoverGameState.TimerPayload();
        payload.setPhase("speaking");
        payload.setDuration(HUMAN_SPEECH_SECONDS);
        Map<String, Object> defaultAction = new HashMap<>();
        defaultAction.put("type", "auto_speech");
        defaultAction.put("player_id", playerId);
        payload.setDefaultAction(defaultAction);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("current", playerId);
        payload.setMetadata(metadata);
        return payload;
    }

    private void broadcastEvent(long roomId, String event, Map<String, Object> payload) {
        Map<String, Object> message = new HashMap<>();
        message.put("event", event);
        if (payload != null) {
            message.putAll(payload);
        }
        eventPublisher.publishEvent(new RoomRealtimeEvents.GameEvent(roomId, message));
    }

    private String getDraft(long sessionId, long playerId) {
        Map<Long, String> drafts = speechDrafts.get(sessionId);
        if (drafts == null) {
            return null;
        }
        return drafts.get(playerId);
    }

    private void removeDraft(long sessionId, long playerId) {
        Map<Long, String> drafts = speechDrafts.get(sessionId);
        if (drafts == null) {
            return;
        }
        drafts.remove(playerId);
        if (drafts.isEmpty()) {
            speechDrafts.remove(sessionId);
        }
    }

    private String sanitizeContent(String content) {
        return content != null ? content.trim() : "";
    }
}
