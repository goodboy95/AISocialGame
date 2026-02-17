package com.aisocialgame.integration.grpc.dto;

import java.util.List;

public record PagedLedgerSnapshot(
        int page,
        int size,
        long total,
        List<LedgerEntrySnapshot> entries
) {
}
