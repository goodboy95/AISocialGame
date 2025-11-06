package com.aisocialgame.backend.service.game;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.aisocialgame.backend.dto.RoomDtos;
import com.aisocialgame.backend.entity.GameSession;
import com.aisocialgame.backend.entity.Room;
import com.aisocialgame.backend.entity.RoomPlayer;
import com.aisocialgame.backend.entity.WordPair;
import com.aisocialgame.backend.repository.GameSessionRepository;
import com.aisocialgame.backend.repository.RoomPlayerRepository;
import com.aisocialgame.backend.repository.WordPairRepository;
import com.aisocialgame.backend.service.GameScheduler;
import com.aisocialgame.backend.service.GameSessionMapper;
import com.aisocialgame.backend.service.JsonUtils;
import com.aisocialgame.backend.realtime.RoomRealtimeEvents;
import com.aisocialgame.backend.service.game.undercover.UndercoverSessionState;
import com.aisocialgame.backend.service.game.undercover.UndercoverStage;

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
    private final WordPairRepository wordPairRepository;

    private final Map<Long, Map<Long, String>> speechDrafts = new ConcurrentHashMap<>();

    public UndercoverGameManager(
            RoomPlayerRepository roomPlayerRepository,
            GameSessionRepository gameSessionRepository,
            GameSessionMapper gameSessionMapper,
            ApplicationEventPublisher eventPublisher,
            GameScheduler scheduler,
            PlatformTransactionManager transactionManager,
            WordPairRepository wordPairRepository) {
        this.roomPlayerRepository = roomPlayerRepository;
        this.gameSessionRepository = gameSessionRepository;
        this.gameSessionMapper = gameSessionMapper;
        this.eventPublisher = eventPublisher;
        this.scheduler = scheduler;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.wordPairRepository = wordPairRepository;
    }

    public void initializeSession(GameSession session) {
        if (session == null || session.getId() == null) {
            return;
        }
        initializeSession(session.getId());
    }

    public void initializeSession(long sessionId) {
        AtomicReference<RoomDtos.GameSessionSnapshot> snapshotRef = new AtomicReference<>();
        AtomicReference<UndercoverSessionState> sessionStateRef = new AtomicReference<>();
        AtomicLong roomIdRef = new AtomicLong();
        AtomicLong currentPlayerRef = new AtomicLong(0);
        AtomicBoolean currentIsAi = new AtomicBoolean(false);
        AtomicLong currentDurationRef = new AtomicLong(HUMAN_SPEECH_SECONDS);
        transactionTemplate.executeWithoutResult(status -> {
            GameSession managed = gameSessionRepository.findById(sessionId).orElse(null);
            if (managed == null) {
                log.warn("Attempted to initialize missing session {}", sessionId);
                return;
            }
            Room room = managed.getRoom();
            roomIdRef.set(room.getId());
            List<RoomPlayer> players = roomPlayerRepository.findByRoomOrderBySeatNumberAsc(room);
            UndercoverSessionState.Config sessionConfig = resolveConfig(room, players);
            UndercoverSessionState.WordPair resolvedPair = resolveWordPair(sessionConfig);
            UndercoverGameState state = buildInitialState(managed, players, sessionConfig, resolvedPair);
            Long currentPlayer = determineNextSpeaker(state, null);
            state.setCurrentPlayerId(currentPlayer);
            managed.setPhase("role_distribution");
            managed.setRoundNumber(state.getRound());
            managed.setCurrentPlayerId(currentPlayer);
            UndercoverGameState.TimerPayload timerPayload = null;
            Instant deadline = null;
            UndercoverGameState.Assignment currentAssignment = state.findAssignment(currentPlayer);
            if (currentAssignment != null) {
                currentIsAi.set(currentAssignment.isAi());
                if (!currentAssignment.isAi()) {
                    timerPayload = buildTimerPayload(currentPlayer, sessionConfig);
                    int duration = Math.max(sessionConfig.getRoundConfig().getDiscussionSeconds(), 5);
                    deadline = Instant.now().plusSeconds(duration);
                    currentDurationRef.set(duration);
                }
            }
            state.setTimer(timerPayload);
            managed.setDeadlineAt(deadline);
            managed.setStateJson(JsonUtils.toJsonObject(state));
            managed.setUpdatedAt(Instant.now());
            roomPlayerRepository.saveAll(players);
            gameSessionRepository.save(managed);
            snapshotRef.set(gameSessionMapper.toSnapshot(managed));
            UndercoverSessionState sessionState = state.getUndercoverSession();
            if (sessionState != null) {
                UndercoverSessionState.PhasePayload payload = sessionState.getPhasePayload();
                if (payload != null) {
                    if (payload.getDiscussionOrder() == null || payload.getDiscussionOrder().isEmpty()) {
                        payload.setDiscussionOrder(determineSpeakingOrder(managed, players));
                    }
                    if (deadline != null) {
                        payload.setCountdownDeadline(deadline.getEpochSecond());
                    }
                    Map<String, Object> metadata = new LinkedHashMap<>(payload.getMetadata());
                    if (currentPlayer != null) {
                        metadata.put("current_player_id", currentPlayer);
                    }
                    payload.setMetadata(metadata);
                }
                sessionStateRef.set(sessionState);
            }
            if (currentPlayer != null) {
                currentPlayerRef.set(currentPlayer);
            }
        });
        RoomDtos.GameSessionSnapshot snapshot = snapshotRef.get();
        long roomId = roomIdRef.get();
        if (snapshot != null && roomId > 0) {
            broadcastEvent(roomId, "undercover.state_initialized", Map.of("session", snapshot));
        }
        UndercoverSessionState sessionState = sessionStateRef.get();
        if (roomId > 0 && sessionState != null) {
            broadcastEvent(roomId, "game.undercover.stage", buildStageBroadcastPayload(sessionState, snapshot));
        }
        long currentPlayerId = currentPlayerRef.get();
        if (roomId > 0 && currentPlayerId > 0) {
            if (currentIsAi.get()) {
                scheduler.executeAsync(() -> triggerAiSpeech(sessionId, currentPlayerId));
            } else {
                long duration = Math.max(currentDurationRef.get(), 5L);
                scheduler.schedule(sessionId, () -> autoSubmitSpeech(sessionId, currentPlayerId), Duration.ofSeconds(duration));
            }
        }
    }

    public void submitSpeech(long sessionId, long playerId, boolean autoSubmission, boolean fromAi, String rawContent) {
        AtomicReference<RoomDtos.GameSessionSnapshot> snapshotRef = new AtomicReference<>();
        AtomicReference<UndercoverGameState.Speech> speechRef = new AtomicReference<>();
        AtomicLong roomIdRef = new AtomicLong();
        AtomicLong nextPlayerRef = new AtomicLong(0);
        AtomicBoolean nextIsAi = new AtomicBoolean(false);
        AtomicLong nextDurationRef = new AtomicLong(HUMAN_SPEECH_SECONDS);
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
            UndercoverSessionState sessionState = state.getUndercoverSession();
            UndercoverSessionState.Config sessionConfig = sessionState != null ? sessionState.getConfig() : null;
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
            Instant now = Instant.now();
            UndercoverGameState.Speech speech = new UndercoverGameState.Speech(
                    playerId,
                    content,
                    assignment.isAi() || fromAi,
                    FORMATTER.format(now));
            state.getSpeeches().add(speech);
            speechRef.set(speech);
            if (sessionState != null) {
                if (sessionState.getStage() == UndercoverStage.ROLE_DISTRIBUTION) {
                    sessionState.setStage(UndercoverStage.DISCUSSION);
                }
                UndercoverSessionState.PlayerState playerState = sessionState.findPlayer(playerId);
                if (playerState != null) {
                    UndercoverSessionState.SpeechRecord record = new UndercoverSessionState.SpeechRecord(
                            state.getRound(), content, now);
                    playerState.getSpeeches().add(record);
                }
                sessionState.getTimeline().add(new UndercoverSessionState.TimelineEvent("SPEECH_RECORDED", state.getRound(), now));
            }
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
                        int duration = sessionConfig != null ? sessionConfig.getRoundConfig().getDiscussionSeconds() : (int) HUMAN_SPEECH_SECONDS;
                        int normalized = Math.max(duration, 5);
                        deadline = Instant.now().plusSeconds(normalized);
                        nextDurationRef.set(normalized);
                        timerPayload = buildTimerPayload(nextPlayer, sessionConfig);
                    }
                }
            } else {
                speechDrafts.remove(sessionId);
            }
            state.setTimer(timerPayload);
            session.setDeadlineAt(deadline);
            if (sessionState != null) {
                UndercoverSessionState.PhasePayload payload = sessionState.getPhasePayload();
                if (payload != null) {
                    if (deadline != null) {
                        payload.setCountdownDeadline(deadline.getEpochSecond());
                    } else {
                        payload.setCountdownDeadline(null);
                    }
                    Map<String, Object> metadata = new LinkedHashMap<>(payload.getMetadata());
                    if (nextPlayer != null) {
                        metadata.put("current_player_id", nextPlayer);
                    } else {
                        metadata.remove("current_player_id");
                    }
                    payload.setMetadata(metadata);
                }
            }
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
                long duration = Math.max(nextDurationRef.get(), 5L);
                scheduler.schedule(sessionId, () -> autoSubmitSpeech(sessionId, nextPlayerId), Duration.ofSeconds(duration));
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

    private UndercoverGameState buildInitialState(
            GameSession session,
            List<RoomPlayer> players,
            UndercoverSessionState.Config config,
            UndercoverSessionState.WordPair wordPair) {
        UndercoverGameState state = new UndercoverGameState();
        state.setPhase("role_distribution");
        state.setRound(1);
        List<Long> speakingOrder = determineSpeakingOrder(session, players);
        state.setSpeakingOrder(speakingOrder);

        AssignmentBundle bundle = assignRoles(session, players, config, wordPair);
        state.setAssignments(bundle.legacyAssignments());

        UndercoverGameState.VoteSummary voteSummary = new UndercoverGameState.VoteSummary();
        voteSummary.setRequired(Math.max(1, players.size() - 1));
        voteSummary.setSubmitted(0);
        state.setVoteSummary(voteSummary);

        UndercoverGameState.WordPair legacyPair = new UndercoverGameState.WordPair();
        legacyPair.setTopic(wordPair.getTopic());
        legacyPair.setDifficulty(wordPair.getDifficulty());
        legacyPair.setSelfWord(wordPair.getCivilian());
        state.setWordPair(legacyPair);

        UndercoverSessionState sessionState = new UndercoverSessionState();
        sessionState.setStage(UndercoverStage.ROLE_DISTRIBUTION);
        sessionState.setRound(1);
        sessionState.setPlayers(bundle.sessionPlayers());
        sessionState.setConfig(config);
        sessionState.setWordPair(wordPair);
        sessionState.setPhasePayload(buildInitialPhasePayload(speakingOrder));
        sessionState.setTimeline(buildInitialTimeline());
        state.setUndercoverSession(sessionState);
        return state;
    }

    private UndercoverSessionState.PhasePayload buildInitialPhasePayload(List<Long> speakingOrder) {
        UndercoverSessionState.PhasePayload payload = new UndercoverSessionState.PhasePayload();
        if (speakingOrder != null) {
            payload.setDiscussionOrder(new ArrayList<>(speakingOrder));
        }
        Instant now = Instant.now();
        payload.setCountdownDeadline(now.plusSeconds(5).getEpochSecond());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stage", UndercoverStage.ROLE_DISTRIBUTION.name());
        payload.setMetadata(metadata);
        return payload;
    }

    private List<UndercoverSessionState.TimelineEvent> buildInitialTimeline() {
        Instant now = Instant.now();
        List<UndercoverSessionState.TimelineEvent> events = new ArrayList<>();
        events.add(new UndercoverSessionState.TimelineEvent("SESSION_CREATED", 0, now));
        events.add(new UndercoverSessionState.TimelineEvent("ROLE_DISTRIBUTION", 1, now));
        return events;
    }

    private AssignmentBundle assignRoles(
            GameSession session,
            List<RoomPlayer> players,
            UndercoverSessionState.Config config,
            UndercoverSessionState.WordPair wordPair) {
        UndercoverSessionState.RoleConfig roleConfig = config.getRoleConfig();
        List<RoomPlayer> shuffled = new ArrayList<>(players);
        shuffled.sort(Comparator.comparingInt(RoomPlayer::getSeatNumber));
        Collections.shuffle(shuffled, new Random(computeSpeakingSeed(session, players)));
        int total = shuffled.size();
        int spies = Math.min(roleConfig.getSpies(), total);
        int blanks = Math.min(roleConfig.getBlank(), Math.max(0, total - spies));
        int civilians = Math.max(0, total - spies - blanks);
        if (spies <= 0 && total > 0) {
            spies = 1;
            civilians = Math.max(0, total - spies - blanks);
        }
        Map<Long, RoleAssignment> assignments = new HashMap<>();
        for (int i = 0; i < shuffled.size(); i++) {
            RoomPlayer player = shuffled.get(i);
            String role;
            String word;
            if (i < spies) {
                role = "spy";
                word = wordPair.getSpy() != null ? wordPair.getSpy() : "卧底词";
            } else if (i < spies + blanks) {
                role = "blank";
                word = null;
            } else {
                role = "civilian";
                word = wordPair.getCivilian() != null ? wordPair.getCivilian() : "平民词";
            }
            player.setRole(role);
            player.setWord(word);
            player.setAlive(true);
            player.setHasUsedSkill(false);
            assignments.put(player.getId(), new RoleAssignment(role, word));
        }

        List<UndercoverGameState.Assignment> legacyAssignments = new ArrayList<>();
        List<UndercoverSessionState.PlayerState> sessionPlayers = new ArrayList<>();
        for (RoomPlayer player : players) {
            RoleAssignment roleAssignment = assignments.getOrDefault(player.getId(), new RoleAssignment("civilian", wordPair.getCivilian()));
            UndercoverGameState.Assignment legacy = new UndercoverGameState.Assignment();
            legacy.setPlayerId(player.getId());
            legacy.setDisplayName(player.getDisplayName());
            legacy.setAi(player.isAi());
            legacy.setAlive(player.isAlive());
            legacy.setRole(roleAssignment.role());
            legacy.setWord(roleAssignment.word());
            legacy.setAiStyle(player.getAiStyle());
            legacyAssignments.add(legacy);

            UndercoverSessionState.PlayerState sessionPlayer = new UndercoverSessionState.PlayerState();
            sessionPlayer.setPlayerId(player.getId());
            sessionPlayer.setDisplayName(player.getDisplayName());
            sessionPlayer.setAi(player.isAi());
            sessionPlayer.setAlive(player.isAlive());
            sessionPlayer.setConnected(player.isActive());
            sessionPlayer.setRole(roleAssignment.role());
            sessionPlayer.setWord(roleAssignment.word());
            sessionPlayers.add(sessionPlayer);
        }
        return new AssignmentBundle(legacyAssignments, sessionPlayers);
    }

    private UndercoverSessionState.Config resolveConfig(Room room, List<RoomPlayer> players) {
        Map<String, Object> configMap = JsonUtils.fromJson(room.getConfigJson());
        UndercoverSessionState.Config config = new UndercoverSessionState.Config();
        UndercoverSessionState.RoleConfig roleConfig = recommendedRoleConfig(players.size());
        UndercoverSessionState.RoundConfig roundConfig = config.getRoundConfig();
        UndercoverSessionState.AiBehavior aiBehavior = config.getAiBehavior();

        Map<String, Object> roleRaw = mapValue(configMap, "role_config");
        if (roleRaw != null) {
            roleConfig.setCivilians(intValue(roleRaw, "civilians", roleConfig.getCivilians()));
            roleConfig.setSpies(Math.max(1, intValue(roleRaw, "spies", roleConfig.getSpies())));
            roleConfig.setBlank(Math.min(1, intValue(roleRaw, "blank", roleConfig.getBlank())));
        }

        Map<String, Object> roundRaw = mapValue(configMap, "round_config");
        if (roundRaw != null) {
            roundConfig.setMaxRounds(intValue(roundRaw, "max_rounds", roundConfig.getMaxRounds()));
            roundConfig.setDiscussionSeconds(intValue(roundRaw, "discussion_seconds", roundConfig.getDiscussionSeconds()));
            roundConfig.setVoteSeconds(intValue(roundRaw, "vote_seconds", roundConfig.getVoteSeconds()));
            roundConfig.setPkVoteSeconds(intValue(roundRaw, "pk_vote_seconds", roundConfig.getPkVoteSeconds()));
            roundConfig.setAllowSelfVote(booleanValue(roundRaw, "allow_self_vote", roundConfig.isAllowSelfVote()));
            roundConfig.setAutoVoteStrategy(stringValue(roundRaw, "auto_vote_strategy", roundConfig.getAutoVoteStrategy()));
        }

        Map<String, Object> aiRaw = mapValue(configMap, "ai_behavior");
        if (aiRaw != null) {
            aiBehavior.setStyle(stringValue(aiRaw, "style", aiBehavior.getStyle()));
            aiBehavior.setSpeechTempo(stringValue(aiRaw, "speech_tempo", aiBehavior.getSpeechTempo()));
        }

        Long wordPackId = longValue(configMap, "word_pack_id");
        if (wordPackId != null && wordPackId > 0) {
            config.setWordPackId(wordPackId);
        }
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("game_mode", stringValue(configMap, "game_mode", "undercover"));
        config.setExtras(extras);

        normalizeRoleConfig(roleConfig, players.size());
        config.setRoleConfig(roleConfig);
        return config;
    }

    private void normalizeRoleConfig(UndercoverSessionState.RoleConfig roleConfig, int playerCount) {
        int total = roleConfig.totalPlayers();
        if (total < playerCount) {
            roleConfig.setCivilians(roleConfig.getCivilians() + (playerCount - total));
        } else if (total > playerCount) {
            int excess = total - playerCount;
            int civilians = Math.max(0, roleConfig.getCivilians() - excess);
            roleConfig.setCivilians(civilians);
        }
        if (roleConfig.totalPlayers() < playerCount) {
            roleConfig.setCivilians(roleConfig.getCivilians() + (playerCount - roleConfig.totalPlayers()));
        }
        if (roleConfig.getBlank() > 1) {
            roleConfig.setBlank(1);
        }
    }

    private UndercoverSessionState.RoleConfig recommendedRoleConfig(int playerCount) {
        UndercoverSessionState.RoleConfig config = new UndercoverSessionState.RoleConfig();
        switch (playerCount) {
            case 5 -> {
                config.setCivilians(4);
                config.setSpies(1);
                config.setBlank(0);
            }
            case 6 -> {
                config.setCivilians(5);
                config.setSpies(1);
                config.setBlank(0);
            }
            case 7 -> {
                config.setCivilians(5);
                config.setSpies(1);
                config.setBlank(1);
            }
            case 8 -> {
                config.setCivilians(6);
                config.setSpies(1);
                config.setBlank(1);
            }
            case 9 -> {
                config.setCivilians(6);
                config.setSpies(2);
                config.setBlank(1);
            }
            case 10 -> {
                config.setCivilians(7);
                config.setSpies(2);
                config.setBlank(1);
            }
            default -> {
                int spies = Math.max(1, playerCount / 4);
                int blank = playerCount >= 7 ? 1 : 0;
                config.setSpies(spies);
                config.setBlank(blank);
                config.setCivilians(Math.max(0, playerCount - spies - blank));
            }
        }
        return config;
    }

    private UndercoverSessionState.WordPair resolveWordPair(UndercoverSessionState.Config config) {
        WordPair pair = selectWordPair(config.getWordPackId());
        UndercoverSessionState.WordPair snapshot = new UndercoverSessionState.WordPair();
        if (pair != null) {
            snapshot.setCivilian(pair.getCivilianWord());
            snapshot.setSpy(pair.getUndercoverWord());
            snapshot.setTopic(pair.getTopic());
            snapshot.setDifficulty(pair.getDifficulty() != null ? pair.getDifficulty().name().toLowerCase() : null);
            config.setWordPackId(pair.getId());
        } else {
            snapshot.setCivilian("牛奶");
            snapshot.setSpy("豆浆");
            snapshot.setTopic("早餐");
            snapshot.setDifficulty("easy");
        }
        return snapshot;
    }

    private WordPair selectWordPair(Long wordPackId) {
        if (wordPackId != null && wordPackId > 0) {
            return wordPairRepository.findById(wordPackId).orElseGet(this::pickRandomWordPair);
        }
        return pickRandomWordPair();
    }

    private WordPair pickRandomWordPair() {
        long total = wordPairRepository.count();
        if (total <= 0) {
            return null;
        }
        int index = (int) Math.max(0, Math.min(total - 1, new Random().nextInt((int) Math.max(1, total))));
        Page<WordPair> page = wordPairRepository.findAll(PageRequest.of(index, 1));
        if (!page.isEmpty()) {
            return page.getContent().get(0);
        }
        Page<WordPair> fallback = wordPairRepository.findAll(PageRequest.of(0, 1));
        return fallback.isEmpty() ? null : fallback.getContent().get(0);
    }

    private Map<String, Object> buildStageBroadcastPayload(
            UndercoverSessionState sessionState, RoomDtos.GameSessionSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stage", sessionState.getStage() != null ? sessionState.getStage().name() : UndercoverStage.LOBBY_READY.name());
        payload.put("round", sessionState.getRound());
        UndercoverSessionState.PhasePayload phasePayload = sessionState.getPhasePayload();
        if (phasePayload != null) {
            Map<String, Object> phase = new LinkedHashMap<>();
            phase.put("discussion_order", new ArrayList<>(phasePayload.getDiscussionOrder()));
            phase.put("countdown_deadline", phasePayload.getCountdownDeadline());
            if (phasePayload.getPkCandidates() != null) {
                phase.put("pk_candidates", phasePayload.getPkCandidates());
            }
            if (!phasePayload.getMetadata().isEmpty()) {
                phase.put("metadata", phasePayload.getMetadata());
            }
            payload.put("phase_payload", phase);
        }
        payload.put("players", sessionState.getPlayers().stream().map(this::toPublicPlayerView).toList());
        payload.put("timeline", sessionState.getTimeline().stream().map(this::toTimelineView).toList());
        payload.put("config", toConfigView(sessionState.getConfig()));
        if (snapshot != null) {
            payload.put("session", snapshot);
        }
        return payload;
    }

    private Map<String, Object> toPublicPlayerView(UndercoverSessionState.PlayerState player) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("player_id", player.getPlayerId());
        view.put("display_name", player.getDisplayName());
        view.put("is_ai", player.isAi());
        view.put("is_alive", player.isAlive());
        view.put("connected", player.isConnected());
        return view;
    }

    private Map<String, Object> toTimelineView(UndercoverSessionState.TimelineEvent event) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("event", event.getEvent());
        view.put("round", event.getRound());
        view.put("timestamp", event.getTimestamp());
        if (!event.getMetadata().isEmpty()) {
            view.put("metadata", event.getMetadata());
        }
        return view;
    }

    private Map<String, Object> toConfigView(UndercoverSessionState.Config config) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("role_config", toRoleConfigMap(config.getRoleConfig()));
        view.put("round_config", toRoundConfigMap(config.getRoundConfig()));
        view.put("ai_behavior", toAiBehaviorMap(config.getAiBehavior()));
        if (config.getWordPackId() != null) {
            view.put("word_pack_id", config.getWordPackId());
        }
        if (!config.getExtras().isEmpty()) {
            view.put("extras", config.getExtras());
        }
        return view;
    }

    private Map<String, Object> toRoleConfigMap(UndercoverSessionState.RoleConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("civilians", config.getCivilians());
        map.put("spies", config.getSpies());
        map.put("blank", config.getBlank());
        return map;
    }

    private Map<String, Object> toRoundConfigMap(UndercoverSessionState.RoundConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("max_rounds", config.getMaxRounds());
        map.put("discussion_seconds", config.getDiscussionSeconds());
        map.put("vote_seconds", config.getVoteSeconds());
        map.put("pk_vote_seconds", config.getPkVoteSeconds());
        map.put("allow_self_vote", config.isAllowSelfVote());
        map.put("auto_vote_strategy", config.getAutoVoteStrategy());
        return map;
    }

    private Map<String, Object> toAiBehaviorMap(UndercoverSessionState.AiBehavior behavior) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("style", behavior.getStyle());
        map.put("speech_tempo", behavior.getSpeechTempo());
        return map;
    }

    private Map<String, Object> mapValue(Map<String, Object> source, String key) {
        Object value = source != null ? source.get(key) : null;
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((k, v) -> normalized.put(String.valueOf(k), v));
            return normalized;
        }
        return null;
    }

    private int intValue(Map<String, Object> source, String key, int fallback) {
        Object value = source != null ? source.get(key) : null;
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private long longValue(Map<String, Object> source, String key) {
        Object value = source != null ? source.get(key) : null;
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private boolean booleanValue(Map<String, Object> source, String key, boolean fallback) {
        Object value = source != null ? source.get(key) : null;
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return fallback;
    }

    private String stringValue(Map<String, Object> source, String key, String fallback) {
        Object value = source != null ? source.get(key) : null;
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    private record AssignmentBundle(
            List<UndercoverGameState.Assignment> legacyAssignments,
            List<UndercoverSessionState.PlayerState> sessionPlayers) {
    }

    private record RoleAssignment(String role, String word) {
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

    private UndercoverGameState.TimerPayload buildTimerPayload(Long playerId, UndercoverSessionState.Config config) {
        if (playerId == null) {
            return null;
        }
        UndercoverGameState.TimerPayload payload = new UndercoverGameState.TimerPayload();
        payload.setPhase("discussion");
        int duration = config != null ? config.getRoundConfig().getDiscussionSeconds() : (int) HUMAN_SPEECH_SECONDS;
        payload.setDuration(Math.max(duration, 5));
        Map<String, Object> defaultAction = new HashMap<>();
        defaultAction.put("type", "auto_speech");
        defaultAction.put("player_id", playerId);
        payload.setDefaultAction(defaultAction);
        payload.setDescription("等待玩家发言");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("current_player_id", playerId);
        metadata.put("stage", UndercoverStage.DISCUSSION.name());
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
