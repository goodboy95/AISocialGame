package com.aisocialgame;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aisocialgame.config.AppProperties;
import com.aisocialgame.exception.ApiException;
import com.aisocialgame.logging.SafeLogValue;
import com.aisocialgame.service.AdminAuthService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AdminAuthServiceTest {

    @Test
    void loginAndValidateToken() {
        AppProperties properties = new AppProperties();
        properties.getAdmin().setUsername("admin");
        properties.getAdmin().setPassword("test-admin-password");
        properties.getAdmin().setTokenTtlHours(1);
        AdminAuthService adminAuthService = new AdminAuthService(properties);
        ListAppender<ILoggingEvent> appender = attachAppender();

        try {
            String token = adminAuthService.login("admin", "test-admin-password");
            Assertions.assertNotNull(token);
            Assertions.assertEquals("admin", adminAuthService.requireAdmin(token));

            String logged = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(message -> message.contains("Admin login succeeded"))
                    .findFirst()
                    .orElseThrow();
            Assertions.assertTrue(logged.contains("actorFingerprint=" + SafeLogValue.fingerprint("admin")));
            Assertions.assertFalse(logged.contains("actor=admin"));
            Assertions.assertFalse(logged.contains("test-admin-password"));
            Assertions.assertFalse(logged.contains(token));
        } finally {
            detachAppender(appender);
        }
    }

    @Test
    void wrongPasswordShouldThrow() {
        AppProperties properties = new AppProperties();
        properties.getAdmin().setUsername("admin");
        properties.getAdmin().setPassword("test-admin-password");
        AdminAuthService adminAuthService = new AdminAuthService(properties);
        ListAppender<ILoggingEvent> appender = attachAppender();

        try {
            Assertions.assertThrows(ApiException.class, () -> adminAuthService.login("admin", "bad"));

            String logged = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(message -> message.contains("Admin login rejected"))
                    .findFirst()
                    .orElseThrow();
            Assertions.assertTrue(logged.contains("actorFingerprint=" + SafeLogValue.fingerprint("admin")));
            Assertions.assertFalse(logged.contains("actor=admin"));
            Assertions.assertTrue(logged.contains("reason=INVALID_CREDENTIALS"));
            Assertions.assertFalse(logged.contains("test-admin-password"));
            Assertions.assertFalse(logged.contains("bad"));
        } finally {
            detachAppender(appender);
        }
    }

    @Test
    void missingAndUnknownSessionsShouldLogReasonWithoutToken() {
        AppProperties properties = new AppProperties();
        AdminAuthService adminAuthService = new AdminAuthService(properties);
        ListAppender<ILoggingEvent> appender = attachAppender();
        String unknownToken = "unknown-admin-session-secret";

        try {
            Assertions.assertThrows(ApiException.class, () -> adminAuthService.requireAdmin(null));
            Assertions.assertThrows(ApiException.class, () -> adminAuthService.requireAdmin(unknownToken));

            String logged = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(message -> message.contains("Admin session rejected"))
                    .reduce("", (left, right) -> left + "\n" + right);
            Assertions.assertTrue(logged.contains("reasonCode=MISSING_TOKEN"));
            Assertions.assertTrue(logged.contains("reasonCode=EXPIRED_OR_UNKNOWN_SESSION"));
            Assertions.assertTrue(logged.contains("sessionStore=memory"));
            Assertions.assertFalse(logged.contains(unknownToken));
        } finally {
            detachAppender(appender);
        }
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger().addAppender(appender);
        return appender;
    }

    private void detachAppender(ListAppender<ILoggingEvent> appender) {
        logger().detachAppender(appender);
        appender.stop();
    }

    private Logger logger() {
        return (Logger) LoggerFactory.getLogger(AdminAuthService.class);
    }
}
