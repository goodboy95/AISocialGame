package com.aisocialgame.dto.admin;

import com.aisocialgame.integration.grpc.dto.PagedLedgerSnapshot;

import java.util.List;
import java.util.Map;

public class AdminLedgerPageResponse {
    private int page;
    private int size;
    private long total;
    private List<Item> entries;

    public AdminLedgerPageResponse(PagedLedgerSnapshot snapshot) {
        this.page = snapshot.page();
        this.size = snapshot.size();
        this.total = snapshot.total();
        this.entries = snapshot.entries().stream()
                .map(entry -> new Item(
                        entry.id(),
                        entry.requestId(),
                        entry.projectKey(),
                        entry.type(),
                        entry.tokenDeltaTemp(),
                        entry.tokenDeltaPermanent(),
                        entry.tokenDeltaPublic(),
                        entry.balanceTemp(),
                        entry.balancePermanent(),
                        entry.balancePublic(),
                        entry.source(),
                        entry.createdAt(),
                        entry.metadata()
                ))
                .toList();
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotal() {
        return total;
    }

    public List<Item> getEntries() {
        return entries;
    }

    public record Item(
            long id,
            String requestId,
            String projectKey,
            String type,
            long tokenDeltaTemp,
            long tokenDeltaPermanent,
            long tokenDeltaPublic,
            long balanceTemp,
            long balancePermanent,
            long balancePublic,
            String source,
            java.time.Instant createdAt,
            Map<String, String> metadata
    ) {
    }
}
