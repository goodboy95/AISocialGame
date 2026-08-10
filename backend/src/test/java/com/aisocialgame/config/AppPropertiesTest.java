package com.aisocialgame.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AppPropertiesTest {

    @Test
    void defaultsShouldUseAuthoritativeLocalDomains() {
        AppProperties properties = new AppProperties();

        assertEquals("https://localuserservice.testhut.top", properties.getSso().getUserServiceBaseUrl());
        assertEquals("https://localsocialgame.testhut.top/sso/callback", properties.getSso().getCallbackUrl());
    }
}
