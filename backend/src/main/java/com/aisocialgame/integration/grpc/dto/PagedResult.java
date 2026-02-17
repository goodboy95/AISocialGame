package com.aisocialgame.integration.grpc.dto;

import java.util.List;

public record PagedResult<T>(
        int page,
        int size,
        long total,
        List<T> items
) {
}
