package com.aisocialgame.model;

import java.time.LocalDateTime;

public class GameLogEntry {
    private String type;
    private String message;
    private LocalDateTime time;

    public GameLogEntry() {}

    public GameLogEntry(String type, String message) {
        this.type = type;
        this.message = message;
        this.time = LocalDateTime.now();
    }

    public String getType() { return type; }
    public String getMessage() { return message; }
    public LocalDateTime getTime() { return time; }

    public void setType(String type) { this.type = type; }
    public void setMessage(String message) { this.message = message; }
    public void setTime(LocalDateTime time) { this.time = time; }
}
