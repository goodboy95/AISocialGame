package com.aisocialgame.backend.service.game.undercover;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the persisted session state for the "谁是卧底" game mode.  The
 * structure mirrors the JSON example documented in {@code doc/Undercover_dev.md}
 * so that QA tooling and future services can consume the data without needing to
 * understand the legacy fields that still exist for the old frontend.
 */
public class UndercoverSessionState {

    private UndercoverStage stage = UndercoverStage.LOBBY_READY;

    private int round = 1;

    private List<PlayerState> players = new ArrayList<>();

    private List<TimelineEvent> timeline = new ArrayList<>();

    @JsonProperty("vote_history")
    private List<VoteHistory> voteHistory = new ArrayList<>();

    @JsonProperty("word_pair")
    private WordPair wordPair = new WordPair();

    private Config config = new Config();

    @JsonProperty("phase_payload")
    private PhasePayload phasePayload = new PhasePayload();

    public UndercoverStage getStage() {
        return stage;
    }

    public void setStage(UndercoverStage stage) {
        this.stage = stage;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public List<PlayerState> getPlayers() {
        return players;
    }

    public void setPlayers(List<PlayerState> players) {
        this.players = players != null ? players : new ArrayList<>();
    }

    public List<TimelineEvent> getTimeline() {
        return timeline;
    }

    public void setTimeline(List<TimelineEvent> timeline) {
        this.timeline = timeline != null ? timeline : new ArrayList<>();
    }

    public List<VoteHistory> getVoteHistory() {
        return voteHistory;
    }

    public void setVoteHistory(List<VoteHistory> voteHistory) {
        this.voteHistory = voteHistory != null ? voteHistory : new ArrayList<>();
    }

    public WordPair getWordPair() {
        return wordPair;
    }

    public void setWordPair(WordPair wordPair) {
        this.wordPair = wordPair != null ? wordPair : new WordPair();
    }

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config != null ? config : new Config();
    }

    public PhasePayload getPhasePayload() {
        return phasePayload;
    }

    public void setPhasePayload(PhasePayload phasePayload) {
        this.phasePayload = phasePayload != null ? phasePayload : new PhasePayload();
    }

    public PlayerState findPlayer(long playerId) {
        return players.stream()
                .filter(player -> player.getPlayerId() == playerId)
                .findFirst()
                .orElse(null);
    }

    public static class PlayerState {

        @JsonProperty("player_id")
        private long playerId;

        @JsonProperty("display_name")
        private String displayName;

        private String role;

        private String word;

        private boolean alive = true;

        @JsonProperty("is_ai")
        private boolean ai;

        private boolean connected = true;

        private List<SpeechRecord> speeches = new ArrayList<>();

        @JsonProperty("ai_trace")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private List<Map<String, Object>> aiTrace = new ArrayList<>();

        public long getPlayerId() {
            return playerId;
        }

        public void setPlayerId(long playerId) {
            this.playerId = playerId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getWord() {
            return word;
        }

        public void setWord(String word) {
            this.word = word;
        }

        public boolean isAlive() {
            return alive;
        }

        public void setAlive(boolean alive) {
            this.alive = alive;
        }

        public boolean isAi() {
            return ai;
        }

        public void setAi(boolean ai) {
            this.ai = ai;
        }

        public boolean isConnected() {
            return connected;
        }

        public void setConnected(boolean connected) {
            this.connected = connected;
        }

        public List<SpeechRecord> getSpeeches() {
            return speeches;
        }

        public void setSpeeches(List<SpeechRecord> speeches) {
            this.speeches = speeches != null ? speeches : new ArrayList<>();
        }

        public List<Map<String, Object>> getAiTrace() {
            return aiTrace;
        }

        public void setAiTrace(List<Map<String, Object>> aiTrace) {
            this.aiTrace = aiTrace != null ? aiTrace : new ArrayList<>();
        }
    }

    public static class SpeechRecord {

        private int round;

        private String text;

        @JsonProperty("timestamp")
        private long timestamp;

        public SpeechRecord() {
        }

        public SpeechRecord(int round, String text, Instant timestamp) {
            this.round = round;
            this.text = text;
            this.timestamp = timestamp != null ? timestamp.getEpochSecond() : 0L;
        }

        public int getRound() {
            return round;
        }

        public void setRound(int round) {
            this.round = round;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }

    public static class TimelineEvent {

        private String event;

        private Integer round;

        private long timestamp;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, Object> metadata = new HashMap<>();

        public TimelineEvent() {
        }

        public TimelineEvent(String event, Integer round, Instant timestamp) {
            this.event = event;
            this.round = round;
            this.timestamp = timestamp != null ? timestamp.getEpochSecond() : 0L;
        }

        public String getEvent() {
            return event;
        }

        public void setEvent(String event) {
            this.event = event;
        }

        public Integer getRound() {
            return round;
        }

        public void setRound(Integer round) {
            this.round = round;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? metadata : new HashMap<>();
        }
    }

    public static class VoteHistory {

        private int round;

        private VoteType type = VoteType.MAIN;

        private List<VoteBallot> ballots = new ArrayList<>();

        private VoteResult result = new VoteResult();

        @JsonProperty("resolved_at")
        private long resolvedAt;

        public int getRound() {
            return round;
        }

        public void setRound(int round) {
            this.round = round;
        }

        public VoteType getType() {
            return type;
        }

        public void setType(VoteType type) {
            this.type = type != null ? type : VoteType.MAIN;
        }

        public List<VoteBallot> getBallots() {
            return ballots;
        }

        public void setBallots(List<VoteBallot> ballots) {
            this.ballots = ballots != null ? ballots : new ArrayList<>();
        }

        public VoteResult getResult() {
            return result;
        }

        public void setResult(VoteResult result) {
            this.result = result != null ? result : new VoteResult();
        }

        public long getResolvedAt() {
            return resolvedAt;
        }

        public void setResolvedAt(long resolvedAt) {
            this.resolvedAt = resolvedAt;
        }
    }

    public enum VoteType {
        MAIN,
        PK
    }

    public static class VoteBallot {

        private long from;

        private Long to;

        @JsonProperty("auto_assigned")
        private boolean autoAssigned;

        private String reason;

        public long getFrom() {
            return from;
        }

        public void setFrom(long from) {
            this.from = from;
        }

        public Long getTo() {
            return to;
        }

        public void setTo(Long to) {
            this.to = to;
        }

        public boolean isAutoAssigned() {
            return autoAssigned;
        }

        public void setAutoAssigned(boolean autoAssigned) {
            this.autoAssigned = autoAssigned;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public static class VoteResult {

        private List<Long> eliminated = new ArrayList<>();

        private List<Long> survivors = new ArrayList<>();

        private String method;

        public List<Long> getEliminated() {
            return eliminated;
        }

        public void setEliminated(List<Long> eliminated) {
            this.eliminated = eliminated != null ? eliminated : new ArrayList<>();
        }

        public List<Long> getSurvivors() {
            return survivors;
        }

        public void setSurvivors(List<Long> survivors) {
            this.survivors = survivors != null ? survivors : new ArrayList<>();
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }
    }

    public static class WordPair {

        private String civilian;

        private String spy;

        private String topic;

        private String difficulty;

        public String getCivilian() {
            return civilian;
        }

        public void setCivilian(String civilian) {
            this.civilian = civilian;
        }

        public String getSpy() {
            return spy;
        }

        public void setSpy(String spy) {
            this.spy = spy;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(String difficulty) {
            this.difficulty = difficulty;
        }
    }

    public static class Config {

        @JsonProperty("role_config")
        private RoleConfig roleConfig = new RoleConfig();

        @JsonProperty("round_config")
        private RoundConfig roundConfig = new RoundConfig();

        @JsonProperty("ai_behavior")
        private AiBehavior aiBehavior = new AiBehavior();

        @JsonProperty("word_pack_id")
        private Long wordPackId;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, Object> extras = new HashMap<>();

        public RoleConfig getRoleConfig() {
            return roleConfig;
        }

        public void setRoleConfig(RoleConfig roleConfig) {
            this.roleConfig = roleConfig != null ? roleConfig : new RoleConfig();
        }

        public RoundConfig getRoundConfig() {
            return roundConfig;
        }

        public void setRoundConfig(RoundConfig roundConfig) {
            this.roundConfig = roundConfig != null ? roundConfig : new RoundConfig();
        }

        public AiBehavior getAiBehavior() {
            return aiBehavior;
        }

        public void setAiBehavior(AiBehavior aiBehavior) {
            this.aiBehavior = aiBehavior != null ? aiBehavior : new AiBehavior();
        }

        public Long getWordPackId() {
            return wordPackId;
        }

        public void setWordPackId(Long wordPackId) {
            this.wordPackId = wordPackId;
        }

        public Map<String, Object> getExtras() {
            return extras;
        }

        public void setExtras(Map<String, Object> extras) {
            this.extras = extras != null ? extras : new HashMap<>();
        }
    }

    public static class RoleConfig {

        private int civilians = 6;

        private int spies = 2;

        private int blank = 0;

        public int getCivilians() {
            return civilians;
        }

        public void setCivilians(int civilians) {
            this.civilians = Math.max(civilians, 0);
        }

        public int getSpies() {
            return spies;
        }

        public void setSpies(int spies) {
            this.spies = Math.max(spies, 0);
        }

        public int getBlank() {
            return blank;
        }

        public void setBlank(int blank) {
            this.blank = Math.max(blank, 0);
        }

        public int totalPlayers() {
            return civilians + spies + blank;
        }
    }

    public static class RoundConfig {

        @JsonProperty("max_rounds")
        private int maxRounds = 8;

        @JsonProperty("discussion_seconds")
        private int discussionSeconds = 90;

        @JsonProperty("vote_seconds")
        private int voteSeconds = 30;

        @JsonProperty("pk_vote_seconds")
        private int pkVoteSeconds = 20;

        @JsonProperty("allow_self_vote")
        private boolean allowSelfVote = false;

        @JsonProperty("auto_vote_strategy")
        private String autoVoteStrategy = "random";

        public int getMaxRounds() {
            return maxRounds;
        }

        public void setMaxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
        }

        public int getDiscussionSeconds() {
            return discussionSeconds;
        }

        public void setDiscussionSeconds(int discussionSeconds) {
            this.discussionSeconds = discussionSeconds;
        }

        public int getVoteSeconds() {
            return voteSeconds;
        }

        public void setVoteSeconds(int voteSeconds) {
            this.voteSeconds = voteSeconds;
        }

        public int getPkVoteSeconds() {
            return pkVoteSeconds;
        }

        public void setPkVoteSeconds(int pkVoteSeconds) {
            this.pkVoteSeconds = pkVoteSeconds;
        }

        public boolean isAllowSelfVote() {
            return allowSelfVote;
        }

        public void setAllowSelfVote(boolean allowSelfVote) {
            this.allowSelfVote = allowSelfVote;
        }

        public String getAutoVoteStrategy() {
            return autoVoteStrategy;
        }

        public void setAutoVoteStrategy(String autoVoteStrategy) {
            this.autoVoteStrategy = autoVoteStrategy;
        }
    }

    public static class AiBehavior {

        private String style = "balanced";

        @JsonProperty("speech_tempo")
        private String speechTempo = "normal";

        public String getStyle() {
            return style;
        }

        public void setStyle(String style) {
            this.style = style;
        }

        public String getSpeechTempo() {
            return speechTempo;
        }

        public void setSpeechTempo(String speechTempo) {
            this.speechTempo = speechTempo;
        }
    }

    public static class PhasePayload {

        @JsonProperty("discussion_order")
        private List<Long> discussionOrder = new ArrayList<>();

        @JsonProperty("countdown_deadline")
        private Long countdownDeadline;

        @JsonProperty("pk_candidates")
        private List<Long> pkCandidates;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, Object> metadata = new HashMap<>();

        public List<Long> getDiscussionOrder() {
            return discussionOrder;
        }

        public void setDiscussionOrder(List<Long> discussionOrder) {
            this.discussionOrder = discussionOrder != null ? discussionOrder : new ArrayList<>();
        }

        public Long getCountdownDeadline() {
            return countdownDeadline;
        }

        public void setCountdownDeadline(Long countdownDeadline) {
            this.countdownDeadline = countdownDeadline;
        }

        public List<Long> getPkCandidates() {
            return pkCandidates;
        }

        public void setPkCandidates(List<Long> pkCandidates) {
            this.pkCandidates = pkCandidates;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? metadata : new HashMap<>();
        }
    }
}
