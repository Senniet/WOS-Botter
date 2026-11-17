package dev.frostguard.engine.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import dev.frostguard.api.configs.ConfigurationKeyEnum;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;

class AnalyticsServiceTest {

    @Test
    void externalMixpanelIsAlwaysDisabled() {
        assertFalse(AnalyticsService.getInstance().isEnabled());
    }

    @Test
    void defaultsToDisabled() {
        assertEquals("false", ConfigurationKeyEnum.ANALYTICS_ENABLED_BOOL.getDefaultValue());
    }

    @Test
    void describesDeliveryFailureWithoutStackTrace() {
        Exception failure = new SocketTimeoutException("Connect timed out");

        assertEquals("Connect timed out", AnalyticsService.describeDeliveryFailure(failure));
    }

    @Test
    void fallsBackToExceptionTypeWhenDeliveryFailureHasNoMessage() {
        Exception failure = new SocketTimeoutException();

        assertEquals("SocketTimeoutException", AnalyticsService.describeDeliveryFailure(failure));
    }
}
