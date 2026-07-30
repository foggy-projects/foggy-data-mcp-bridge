package com.foggyframework.runtime.console;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

final class RuntimeConsoleActivationGuard implements InitializingBean {

    private final Environment environment;

    RuntimeConsoleActivationGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        requireTrue("foggy.runtime-api.enabled",
                "Runtime Console requires foggy.runtime-api.enabled=true.");

        String authCode = environment.getProperty("foggy.runtime-api.auth-code");
        if (!StringUtils.hasText(authCode)) {
            throw new IllegalStateException("Runtime Console requires a configured Runtime API auth code.");
        }

        String securityMode = environment.getProperty(
                "foggy.runtime-api.security-mode",
                "none-dev-test-only"
        );
        boolean effectiveAuthCode = "auth-code".equalsIgnoreCase(securityMode) || StringUtils.hasText(authCode);
        if (!effectiveAuthCode) {
            throw new IllegalStateException("Runtime Console requires effective Runtime API security mode auth-code.");
        }

        String authScope = environment.getProperty("foggy.runtime-api.auth-scope", "mutations");
        if (!"management-all".equalsIgnoreCase(authScope)) {
            throw new IllegalStateException("Runtime Console requires foggy.runtime-api.auth-scope=management-all.");
        }
    }

    private void requireTrue(String property, String message) {
        if (!environment.getProperty(property, Boolean.class, false)) {
            throw new IllegalStateException(message);
        }
    }
}
