package com.foggyframework.analytics.console;

import com.foggyframework.analytics.console.config.AnalyticsConsoleProperties;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubjectResolver;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.util.Objects;

/** Refuses a partially protected Console assembly. */
final class AnalyticsConsoleActivationGuard implements InitializingBean {

    private final AnalyticsConsoleProperties properties;
    private final AnalyticsConsoleSubjectResolver subjectResolver;
    private final Environment environment;

    AnalyticsConsoleActivationGuard(
            AnalyticsConsoleProperties properties,
            AnalyticsConsoleSubjectResolver subjectResolver,
            Environment environment) {
        this.properties = properties;
        this.subjectResolver = subjectResolver;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        Objects.requireNonNull(subjectResolver, "Analytics Console subject resolver");
        if (!environment.getProperty(
                "foggy.analytics.runtime-api.enabled", Boolean.class, false)) {
            throw new IllegalStateException(
                    "Analytics Console requires foggy.analytics.runtime-api.enabled=true.");
        }
        if (properties.getCatalogPath() == null || properties.getCatalogPath().isBlank()) {
            throw new IllegalStateException("Analytics Console catalog-path is required.");
        }
        if (properties.getMaxDefinitionBytes() < 1
                || properties.getMaxDefinitionBytes() > 16L * 1024 * 1024) {
            throw new IllegalStateException(
                    "Analytics Console max-definition-bytes is outside the safe range.");
        }
        String securityMode = properties.getSecurityMode();
        if (!"host-managed".equalsIgnoreCase(securityMode)
                && !"static-dev-only".equalsIgnoreCase(securityMode)) {
            throw new IllegalStateException(
                    "Analytics Console security-mode must be host-managed or static-dev-only.");
        }
        validateFap(properties.getFap());
    }

    private static void validateFap(AnalyticsConsoleProperties.Fap fap) {
        if (!fap.isEnabled()) {
            return;
        }
        URI baseUri;
        try {
            baseUri = URI.create(required(fap.getBaseUrl(), "fap.base-url"));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Analytics Console fap.base-url is invalid.", error);
        }
        if (!("http".equalsIgnoreCase(baseUri.getScheme())
                || "https".equalsIgnoreCase(baseUri.getScheme()))
                || baseUri.getHost() == null
                || baseUri.getUserInfo() != null
                || baseUri.getQuery() != null
                || baseUri.getFragment() != null
                || !(baseUri.getPath() == null
                        || baseUri.getPath().isEmpty()
                        || "/".equals(baseUri.getPath()))) {
            throw new IllegalStateException(
                    "Analytics Console fap.base-url must be an HTTP(S) server URL.");
        }
        required(fap.getProviderRef(), "fap.provider-ref");
        required(fap.getSkillName(), "fap.skill-name");
        required(fap.getCapabilityName(), "fap.capability-name");
        required(fap.getCallbackCapabilityId(), "fap.callback-capability-id");
        if (fap.getCallbackCapabilityRevision() < 1) {
            throw new IllegalStateException(
                    "Analytics Console fap.callback-capability-revision must be positive.");
        }
        required(fap.getCallbackAuthorization(), "fap.callback-authorization");
        if (fap.getTimeoutSeconds() < 1 || fap.getTimeoutSeconds() > 120) {
            throw new IllegalStateException(
                    "Analytics Console fap.timeout-seconds is outside the safe range.");
        }
    }

    private static String required(String value, String property) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalStateException("Analytics Console " + property + " is required.");
        }
        return value;
    }
}
