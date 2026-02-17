package com.aisocialgame.dto;

import com.aisocialgame.integration.grpc.dto.CheckinStatusResult;

import java.time.format.DateTimeFormatter;

public class CheckinStatusResponse {
    private final boolean checkedInToday;
    private final String lastCheckinDate;
    private final long tokensGrantedToday;

    public CheckinStatusResponse(CheckinStatusResult result) {
        this.checkedInToday = result.checkedInToday();
        this.lastCheckinDate = result.lastCheckinDate() == null
                ? null
                : DateTimeFormatter.ISO_INSTANT.format(result.lastCheckinDate());
        this.tokensGrantedToday = result.tokensGrantedToday();
    }

    public boolean isCheckedInToday() {
        return checkedInToday;
    }

    public String getLastCheckinDate() {
        return lastCheckinDate;
    }

    public long getTokensGrantedToday() {
        return tokensGrantedToday;
    }
}
