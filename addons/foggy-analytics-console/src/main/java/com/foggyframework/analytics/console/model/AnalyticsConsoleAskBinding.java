package com.foggyframework.analytics.console.model;

import java.time.Instant;
import java.util.Objects;

/** One exact START or CONTINUE Ask binding; prompt and credentials are absent. */
public record AnalyticsConsoleAskBinding(
        String askRequestId,
        String askInvocationRef,
        String runtimeExecutionId,
        String runtimeTaskId,
        Instant createdAt) {

    public AnalyticsConsoleAskBinding {
        askRequestId = required(askRequestId, "askRequestId");
        askInvocationRef = required(askInvocationRef, "askInvocationRef");
        runtimeExecutionId = required(runtimeExecutionId, "runtimeExecutionId");
        runtimeTaskId = required(runtimeTaskId, "runtimeTaskId");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }
}
