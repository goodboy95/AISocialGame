package com.aisocialgame.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AppPropertiesTest {

    @Test
    void defaultsShouldUseAuthoritativeLocalDomains() {
        AppProperties properties = new AppProperties();

        assertEquals("https://localuserservice.testhut.top", properties.getSso().getUserServiceBaseUrl());
        assertEquals("https://localsocialgame.testhut.top/sso/callback", properties.getSso().getCallbackUrl());
        assertEquals("aisocialgame", properties.getExternal().getUserserviceJwt().getCallerId());
        assertEquals("aisocialgame", properties.getExternal().getUserserviceJwt().getIssuer());
        assertEquals("aienie-userservice-grpc", properties.getExternal().getUserserviceJwt().getAudience());
        assertEquals(300L, properties.getExternal().getUserserviceJwt().getTtlSeconds());
        assertEquals("user.auth.session.read,user.directory.read,user.ban.read,user.ban.write",
                properties.getExternal().getUserserviceJwt().getScopes());
    }
}
