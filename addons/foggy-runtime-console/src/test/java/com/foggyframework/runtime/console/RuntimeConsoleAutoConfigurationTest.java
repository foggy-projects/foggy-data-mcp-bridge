package com.foggyframework.runtime.console;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RuntimeConsoleAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RuntimeConsoleAutoConfiguration.class));

    @Test
    void shouldRemainDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(RuntimeConsoleActivationGuard.class);
        });
    }

    @Test
    void shouldFailWhenEnabledWithoutProtectedRuntimeApi() {
        contextRunner
                .withPropertyValues("foggy.runtime-console.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Runtime Console requires foggy.runtime-api.enabled=true.");
                });
    }

    @Test
    void shouldActivateOnlyWithManagementAllAuthCodeConfiguration() {
        contextRunner
                .withPropertyValues(
                        "foggy.runtime-console.enabled=true",
                        "foggy.runtime-api.enabled=true",
                        "foggy.runtime-api.security-mode=auth-code",
                        "foggy.runtime-api.auth-code=dummy-console-secret",
                        "foggy.runtime-api.auth-scope=management-all"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RuntimeConsoleActivationGuard.class);
                    assertThat(context).hasSingleBean(RuntimeConsoleWebMvcConfigurer.class);
                    assertThat(context).hasBean("runtimeConsoleSecurityHeadersFilter");
                });
    }

    @Test
    void shouldApplyRestrictiveHeadersOnlyToConsoleResources() throws Exception {
        RuntimeConsoleSecurityHeadersFilter filter = new RuntimeConsoleSecurityHeadersFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/console/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader("Content-Security-Policy"))
                .isEqualTo(RuntimeConsoleSecurityHeadersFilter.CONTENT_SECURITY_POLICY);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
    }
}
