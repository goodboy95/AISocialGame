package com.aisocialgame.integration.grpc.auth;

import com.aisocialgame.config.AppProperties;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Component
public class AiGrpcHmacClientInterceptor implements ClientInterceptor {
    private static final Metadata.Key<String> CALLER_KEY =
            Metadata.Key.of("x-aienie-caller", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> TS_KEY =
            Metadata.Key.of("x-aienie-ts", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> NONCE_KEY =
            Metadata.Key.of("x-aienie-nonce", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> SIGNATURE_KEY =
            Metadata.Key.of("x-aienie-signature", Metadata.ASCII_STRING_MARSHALLER);

    private final AppProperties appProperties;

    public AiGrpcHmacClientInterceptor(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions,
                                                               Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String caller = appProperties.getExternal().getAiserviceHmacCaller().trim();
                String secret = appProperties.getExternal().getAiserviceHmacSecret().trim();
                String ts = String.valueOf(Instant.now().getEpochSecond());
                String nonce = UUID.randomUUID().toString();
                String fullMethod = method.getFullMethodName();
                String methodPath = fullMethod.startsWith("/") ? fullMethod : "/" + fullMethod;
                String canonical = caller + "\n" + methodPath + "\n" + ts + "\n" + nonce;
                String signature = sign(canonical, secret);

                headers.put(CALLER_KEY, caller);
                headers.put(TS_KEY, ts);
                headers.put(NONCE_KEY, nonce);
                headers.put(SIGNATURE_KEY, signature);
                super.start(responseListener, headers);
            }
        };
    }

    private String sign(String text, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign ai grpc request", ex);
        }
    }
}
