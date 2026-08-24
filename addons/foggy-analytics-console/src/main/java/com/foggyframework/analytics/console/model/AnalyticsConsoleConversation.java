package com.foggyframework.analytics.console.model;

import java.time.Instant;
import java.util.Objects;

/** Durable Console-to-FAP binding; credentials and prompts are deliberately absent. */
public record AnalyticsConsoleConversation(
        String conversationId,
        String assetId,
        String ownerSubjectRef,
        String externalConversationRef,
        String askRequestId,
        String askInvocationRef,
        String runtimeExecutionId,
        String runtimeTaskId,
        Instant createdAt) {

    public AnalyticsConsoleConversation {
        conversationId = required(conversationId, "conversationId");
        assetId = required(assetId, "assetId");
        ownerSubjectRef = required(ownerSubjectRef, "ownerSubjectRef");
        externalConversationRef = required(externalConversationRef, "externalConversationRef");
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
