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

@Component
public class UserGrpcAuthClientInterceptor implements ClientInterceptor {
    private static final Metadata.Key<String> INTERNAL_TOKEN_KEY =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    private final AppProperties appProperties;

    public UserGrpcAuthClientInterceptor(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions,
                                                               Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(INTERNAL_TOKEN_KEY, appProperties.getExternal().getUserserviceInternalGrpcToken().trim());
                super.start(responseListener, headers);
            }
        };
    }
}
