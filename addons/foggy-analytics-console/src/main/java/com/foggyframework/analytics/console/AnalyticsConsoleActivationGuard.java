package com.foggyframework.analytics.console;

import com.foggyframework.analytics.console.config.AnalyticsConsoleProperties;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubjectResolver;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.util.Objects;
import java.util.HashSet;

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
        String storageMode = properties.getStorageMode();
        if (!"file-single-process".equalsIgnoreCase(storageMode)
                && !"external-durable".equalsIgnoreCase(storageMode)) {
            throw new IllegalStateException(
                    "Analytics Console storage-mode must be file-single-process or external-durable.");
        }
        if ("file-single-process".equalsIgnoreCase(storageMode)) {
            required(properties.getCatalogPath(), "catalog-path");
            required(properties.getFunctionTracePath(), "function-trace-path");
            required(properties.getAskRecoveryPath(), "ask-recovery-path");
        }
        if (properties.isProductionMode()) {
            if (!"host-managed".equalsIgnoreCase(securityMode)) {
                throw new IllegalStateException(
                        "Analytics Console production-mode requires host-managed security.");
            }
            if (!"external-durable".equalsIgnoreCase(storageMode)) {
                throw new IllegalStateException(
                        "Analytics Console production-mode requires external-durable storage.");
            }
            if (!properties.getFap().isEnabled()
                    || properties.getQuestionProfiles().isEmpty()) {
                throw new IllegalStateException(
                        "Analytics Console production-mode requires FAP and a question profile.");
            }
        }
        validateFap(properties);
        validateQuestionProfiles(properties);
    }

    private static void validateFap(AnalyticsConsoleProperties properties) {
        AnalyticsConsoleProperties.Fap fap = properties.getFap();
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
        required(fap.getQuestionSkillName(), "fap.question-skill-name");
        required(fap.getQuestionCapabilityName(), "fap.question-capability-name");
        required(
                fap.getQuestionCallbackCapabilityId(),
                "fap.question-callback-capability-id");
        if (fap.getQuestionCallbackCapabilityRevision() < 1) {
            throw new IllegalStateException(
                    "Analytics Console fap.question-callback-capability-revision must be positive.");
        }
        if (fap.getTimeoutSeconds() < 1 || fap.getTimeoutSeconds() > 120) {
            throw new IllegalStateException(
                    "Analytics Console fap.timeout-seconds is outside the safe range.");
        }
        if ("static-dev-only".equalsIgnoreCase(properties.getSecurityMode())) {
            required(fap.getDevAuthorization(), "fap.dev-authorization");
            required(fap.getDevWorkspaceRef(), "fap.dev-workspace-ref");
            required(fap.getDevModelConfigRef(), "fap.dev-model-config-ref");
            required(fap.getDevModelVariantId(), "fap.dev-model-variant-id");
            required(fap.getDevTenantRef(), "fap.dev-tenant-ref");
            required(fap.getDevProviderSubjectRef(), "fap.dev-provider-subject-ref");
        }
    }

    private static void validateQuestionProfiles(AnalyticsConsoleProperties properties) {
        HashSet<String> ids = new HashSet<>();
        for (AnalyticsConsoleProperties.QuestionProfile profile
                : properties.getQuestionProfiles()) {
            String id = required(profile.getId(), "question-profiles[].id");
            if (!ids.add(id)) {
                throw new IllegalStateException(
                        "Analytics Console question profile ids must be unique.");
            }
            required(profile.getDisplayName(), "question-profiles[].display-name");
            required(profile.getNamespace(), "question-profiles[].namespace");
        }
    }

    private static String required(String value, String property) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalStateException("Analytics Console " + property + " is required.");
        }
        return value;
    }
}
