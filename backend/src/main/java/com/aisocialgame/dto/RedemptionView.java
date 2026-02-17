package com.aisocialgame.dto;

import com.aisocialgame.integration.grpc.dto.RedemptionRecordSnapshot;

import java.time.format.DateTimeFormatter;

public class RedemptionView {
    private final String code;
    private final long tokensGranted;
    private final String creditType;
    private final String redeemedAt;

    public RedemptionView(RedemptionRecordSnapshot snapshot) {
        this.code = snapshot.code();
        this.tokensGranted = snapshot.tokensGranted();
        this.creditType = snapshot.creditType();
        this.redeemedAt = snapshot.redeemedAt() == null ? null : DateTimeFormatter.ISO_INSTANT.format(snapshot.redeemedAt());
    }

    public String getCode() {
        return code;
    }

    public long getTokensGranted() {
        return tokensGranted;
    }

    public String getCreditType() {
        return creditType;
    }

    public String getRedeemedAt() {
        return redeemedAt;
    }
}
