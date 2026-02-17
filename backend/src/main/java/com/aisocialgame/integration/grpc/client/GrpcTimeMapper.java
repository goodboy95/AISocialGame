package com.aisocialgame.integration.grpc.client;

import com.google.protobuf.Timestamp;

import java.time.Instant;

final class GrpcTimeMapper {
    private GrpcTimeMapper() {
    }

    static Instant toInstant(Timestamp timestamp) {
        if (timestamp == null || (timestamp.getSeconds() == 0L && timestamp.getNanos() == 0)) {
            return null;
        }
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
