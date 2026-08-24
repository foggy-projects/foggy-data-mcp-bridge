package com.foggyframework.analytics.console;

import com.foggyframework.analytics.console.config.AnalyticsConsoleProperties;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubjectResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AnalyticsConsoleActivationGuardTest {

    @Test
    void rejectsHalfConfiguredFapBeforeServingTheConsole() {
        AnalyticsConsoleProperties properties = new AnalyticsConsoleProperties();
        properties.getFap().setEnabled(true);
        properties.getFap().setBaseUrl("https://fap.internal");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("foggy.analytics.runtime-api.enabled", "true");

        assertThatThrownBy(() -> new AnalyticsConsoleActivationGuard(
                properties,
                mock(AnalyticsConsoleSubjectResolver.class),
                environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provider-ref");
    }

    @Test
    void rejectsConsoleWithoutAnalyticsRuntime() {
        assertThatThrownBy(() -> new AnalyticsConsoleActivationGuard(
                new AnalyticsConsoleProperties(),
                mock(AnalyticsConsoleSubjectResolver.class),
                new MockEnvironment()).afterPropertiesSet())
                .hasMessageContaining("runtime-api.enabled=true");
    }
}
