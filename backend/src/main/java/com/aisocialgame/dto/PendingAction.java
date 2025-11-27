package com.aisocialgame.dto;

public class PendingAction {
    private String type;
    private String description;
    private long deadlineSeconds;

    public PendingAction() {}

    public PendingAction(String type, String description, long deadlineSeconds) {
        this.type = type;
        this.description = description;
        this.deadlineSeconds = deadlineSeconds;
    }

    public String getType() { return type; }
    public String getDescription() { return description; }
    public long getDeadlineSeconds() { return deadlineSeconds; }

    public void setType(String type) { this.type = type; }
    public void setDescription(String description) { this.description = description; }
    public void setDeadlineSeconds(long deadlineSeconds) { this.deadlineSeconds = deadlineSeconds; }
}
