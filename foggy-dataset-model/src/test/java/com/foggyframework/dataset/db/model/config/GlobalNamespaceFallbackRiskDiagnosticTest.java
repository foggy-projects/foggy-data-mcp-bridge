package com.foggyframework.dataset.db.model.config;

import com.foggyframework.dataset.db.model.DbModelAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class GlobalNamespaceFallbackRiskDiagnosticTest {

    @Test
    void enabledCompatibilityFallbackEmitsStableProductionRiskCode(CapturedOutput output) {
        Environment environment = new MockEnvironment().withProperty("spring.profiles.active", "prod");

        new DbModelAutoConfiguration()
                .globalNamespaceFallbackRiskDiagnostic(environment)
                .afterSingletonsInstantiated();

        assertThat(output)
                .contains("FOGGY-SEC-932-001")
                .contains("global datasource fallback")
                .contains("prod");
    }

    @Test
    void riskDiagnosticIsBoundToTheExplicitFallbackProperty() throws NoSuchMethodException {
        Method method = DbModelAutoConfiguration.class
                .getMethod("globalNamespaceFallbackRiskDiagnostic", Environment.class);
        ConditionalOnProperty condition = method.getAnnotation(ConditionalOnProperty.class);

        assertThat(condition).isNotNull();
        assertThat(condition.name())
                .containsExactly("foggy.dataset.datasource.allow-global-fallback-for-namespace");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
    }
}
