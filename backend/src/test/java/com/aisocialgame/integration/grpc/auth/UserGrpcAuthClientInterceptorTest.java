package com.aisocialgame.integration.grpc.auth;

import fireflychat.user.v1.UserAuthServiceGrpc;
import fireflychat.user.v1.ValidateSessionRequest;
import fireflychat.user.v1.ValidateSessionResponse;
import io.grpc.ClientInterceptors;
import io.grpc.Contexts;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserGrpcAuthClientInterceptorTest {
    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> LEGACY_INTERNAL_TOKEN_KEY =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    @Test
    void realGrpcCallsCarryFreshCanonicalJwtMetadata() throws Exception {
        List<String> receivedTokens = new CopyOnWriteArrayList<>();
        ServerInterceptor capture = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {
                receivedTokens.add(headers.get(AUTHORIZATION_KEY));
                assertEquals(null, headers.get(LEGACY_INTERNAL_TOKEN_KEY));
                return Contexts.interceptCall(io.grpc.Context.current(), call, headers, next);
            }
        };
        UserAuthServiceGrpc.UserAuthServiceImplBase service = new UserAuthServiceGrpc.UserAuthServiceImplBase() {
            @Override
            public void validateSession(ValidateSessionRequest request,
                                        StreamObserver<ValidateSessionResponse> responseObserver) {
                responseObserver.onNext(ValidateSessionResponse.newBuilder().setValid(true).build());
                responseObserver.onCompleted();
            }
        };
        String serverName = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(ServerInterceptors.intercept(service, capture))
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        try {
            UserServiceCallerJwtProviderTest.MutableClock clock =
                    new UserServiceCallerJwtProviderTest.MutableClock(Instant.parse("2026-08-19T02:00:00Z"));
            AtomicInteger sequence = new AtomicInteger();
            UserServiceCallerJwtProvider provider = new UserServiceCallerJwtProvider(
                    UserServiceCallerJwtProviderTest.validProperties(),
                    clock,
                    () -> "grpc-jti-" + sequence.incrementAndGet());
            var stub = UserAuthServiceGrpc.newBlockingStub(ClientInterceptors.intercept(
                    channel, new UserGrpcAuthClientInterceptor(provider)));
            ValidateSessionRequest request = ValidateSessionRequest.newBuilder()
                    .setUserId(42L)
                    .setSessionId("session-id")
                    .build();

            assertTrue(stub.validateSession(request).getValid());
            clock.advance(Duration.ofSeconds(271));
            assertTrue(stub.validateSession(request).getValid());

            assertEquals(2, receivedTokens.size());
            assertNotEquals(receivedTokens.get(0), receivedTokens.get(1));
            assertTrue(receivedTokens.get(0).startsWith("Bearer "));
            assertTrue(receivedTokens.get(1).startsWith("Bearer "));
            assertEquals("grpc-jti-1", UserServiceCallerJwtProviderTest
                    .parse(receivedTokens.get(0).substring("Bearer ".length()), Instant.parse("2026-08-19T02:00:00Z"))
                    .getBody().getId());
            assertEquals("grpc-jti-2", UserServiceCallerJwtProviderTest
                    .parse(receivedTokens.get(1).substring("Bearer ".length()), clock.instant())
                    .getBody().getId());
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }
}
