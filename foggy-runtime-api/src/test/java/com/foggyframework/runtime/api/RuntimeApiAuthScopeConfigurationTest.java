package com.foggyframework.runtime.api;

import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.config.RuntimeApiAuthScope;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeApiAuthScopeConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void shouldKeepMutationsAsCompatibleDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(FoggyRuntimeApiProperties.class).getAuthScope())
                    .isEqualTo(RuntimeApiAuthScope.MUTATIONS);
        });
    }

    @Test
    void shouldBindManagementAllScope() {
        contextRunner
                .withPropertyValues("foggy.runtime-api.auth-scope=management-all")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(FoggyRuntimeApiProperties.class).getAuthScope())
                            .isEqualTo(RuntimeApiAuthScope.MANAGEMENT_ALL);
                });
    }

    @Test
    void shouldRejectUnknownScopeInsteadOfDowngrading() {
        contextRunner
                .withPropertyValues("foggy.runtime-api.auth-scope=unexpected")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .rootCause()
                            .hasMessageContaining("RuntimeApiAuthScope.unexpected");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FoggyRuntimeApiProperties.class)
    static class PropertiesConfiguration {
    }
}
