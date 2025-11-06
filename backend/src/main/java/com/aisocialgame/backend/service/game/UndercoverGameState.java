package com.aisocialgame.backend.service.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aisocialgame.backend.service.game.undercover.UndercoverSessionState;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UndercoverGameState {

    private String phase = "preparing";
    private int round = 1;

    @JsonProperty("current_player_id")
    private Long currentPlayerId;

    private List<Assignment> assignments = new ArrayList<>();
    private List<Speech> speeches = new ArrayList<>();

    @JsonProperty("voteSummary")
    private VoteSummary voteSummary = new VoteSummary();

    @JsonProperty("word_pair")
    private WordPair wordPair = new WordPair();

    @JsonProperty("speaking_order")
    private List<Long> speakingOrder = new ArrayList<>();

    private TimerPayload timer;

    @JsonProperty("undercover_session")
    private UndercoverSessionState undercoverSession;

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public Long getCurrentPlayerId() {
        return currentPlayerId;
    }

    public void setCurrentPlayerId(Long currentPlayerId) {
        this.currentPlayerId = currentPlayerId;
    }

    public List<Assignment> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<Assignment> assignments) {
        this.assignments = assignments != null ? assignments : new ArrayList<>();
    }

    public List<Speech> getSpeeches() {
        return speeches;
    }

    public void setSpeeches(List<Speech> speeches) {
        this.speeches = speeches != null ? speeches : new ArrayList<>();
    }

    public VoteSummary getVoteSummary() {
        return voteSummary;
    }

    public void setVoteSummary(VoteSummary voteSummary) {
        this.voteSummary = voteSummary != null ? voteSummary : new VoteSummary();
    }

    public WordPair getWordPair() {
        return wordPair;
    }

    public void setWordPair(WordPair wordPair) {
        this.wordPair = wordPair != null ? wordPair : new WordPair();
    }

    public List<Long> getSpeakingOrder() {
        return speakingOrder;
    }

    public void setSpeakingOrder(List<Long> speakingOrder) {
        this.speakingOrder = speakingOrder != null ? speakingOrder : new ArrayList<>();
    }

    public TimerPayload getTimer() {
        return timer;
    }

    public void setTimer(TimerPayload timer) {
        this.timer = timer;
    }

    public UndercoverSessionState getUndercoverSession() {
        return undercoverSession;
    }

    public void setUndercoverSession(UndercoverSessionState undercoverSession) {
        this.undercoverSession = undercoverSession;
    }

    public Assignment findAssignment(Long playerId) {
        if (playerId == null) {
            return null;
        }
        return assignments.stream()
                .filter(assignment -> playerId.equals(assignment.getPlayerId()))
                .findFirst()
                .orElse(null);
    }

    public boolean hasSpeech(Long playerId) {
        if (playerId == null) {
            return false;
        }
        return speeches.stream().anyMatch(speech -> playerId.equals(speech.getPlayerId()));
    }

    public static class Assignment {

        @JsonProperty("playerId")
        private Long playerId;

        @JsonProperty("displayName")
        private String displayName;

        @JsonProperty("isAi")
        private boolean ai;

        @JsonProperty("isAlive")
        private boolean alive = true;

        private String role;
        private String word;

        @JsonProperty("aiStyle")
        private String aiStyle;

        public Long getPlayerId() {
            return playerId;
        }

        public void setPlayerId(Long playerId) {
            this.playerId = playerId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public boolean isAi() {
            return ai;
        }

        public void setAi(boolean ai) {
            this.ai = ai;
        }

        public boolean isAlive() {
            return alive;
        }

        public void setAlive(boolean alive) {
            this.alive = alive;
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

        public String getAiStyle() {
            return aiStyle;
        }

        public void setAiStyle(String aiStyle) {
            this.aiStyle = aiStyle;
        }
    }

    public static class Speech {

        @JsonProperty("player_id")
        private Long playerId;

        private String content;

        @JsonProperty("is_ai")
        private boolean ai;

        private String timestamp;

        public Speech() {}

        public Speech(Long playerId, String content, boolean ai, String timestamp) {
            this.playerId = playerId;
            this.content = content;
            this.ai = ai;
            this.timestamp = timestamp;
        }

        public Long getPlayerId() {
            return playerId;
        }

        public void setPlayerId(Long playerId) {
            this.playerId = playerId;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public boolean isAi() {
            return ai;
        }

        public void setAi(boolean ai) {
            this.ai = ai;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
    }

    public static class VoteSummary {

        private int submitted = 0;
        private int required = 0;
        private Map<String, Integer> tally = new HashMap<>();

        @JsonProperty("selfTarget")
        private Long selfTarget;

        public int getSubmitted() {
            return submitted;
        }

        public void setSubmitted(int submitted) {
            this.submitted = submitted;
        }

        public int getRequired() {
            return required;
        }

        public void setRequired(int required) {
            this.required = required;
        }

        public Map<String, Integer> getTally() {
            return tally;
        }

        public void setTally(Map<String, Integer> tally) {
            this.tally = tally != null ? tally : new HashMap<>();
        }

        public Long getSelfTarget() {
            return selfTarget;
        }

        public void setSelfTarget(Long selfTarget) {
            this.selfTarget = selfTarget;
        }
    }

    public static class WordPair {

        private String topic;
        private String difficulty;

        @JsonProperty("selfWord")
        private String selfWord;

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

        public String getSelfWord() {
            return selfWord;
        }

        public void setSelfWord(String selfWord) {
            this.selfWord = selfWord;
        }
    }

    public static class TimerPayload {

        private String phase;
        private long duration;

        @JsonProperty("default_action")
        private Map<String, Object> defaultAction;

        private String description;
        private Map<String, Object> metadata;

        public String getPhase() {
            return phase;
        }

        public void setPhase(String phase) {
            this.phase = phase;
        }

        public long getDuration() {
            return duration;
        }

        public void setDuration(long duration) {
            this.duration = duration;
        }

        public Map<String, Object> getDefaultAction() {
            return defaultAction;
        }

        public void setDefaultAction(Map<String, Object> defaultAction) {
            this.defaultAction = defaultAction;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }
}
