package com.aisocialgame.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminProofTargetFilterTest {
    @Test
    void bindsMethodPathQueryAndBodyWithoutConsumingTheControllerBody() throws Exception {
        Result first = apply("{\"amount\":1}");
        Result second = apply("{\"amount\":2}");

        assertNotNull(first.target());
        assertNotEquals(first.target(), second.target());
        assertEquals("{\"amount\":1}", first.body());
    }

    private Result apply(String body) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/billing/adjust");
        request.setQueryString("dryRun=false");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        MockFilterChain chain = new MockFilterChain();

        new AdminProofTargetFilter().doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest wrapped = (HttpServletRequest) chain.getRequest();
        return new Result(
                (String) wrapped.getAttribute(AdminProofTargetFilter.TARGET_ATTRIBUTE),
                new String(wrapped.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
        );
    }

    private record Result(String target, String body) {}
}
