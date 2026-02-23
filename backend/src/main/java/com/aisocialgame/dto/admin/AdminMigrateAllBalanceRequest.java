package com.aisocialgame.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class AdminMigrateAllBalanceRequest {
    @Min(value = 1, message = "batchSize 最小为 1")
    @Max(value = 500, message = "batchSize 最大为 500")
    private Integer batchSize;

    public Integer getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }
}
