package com.foggyframework.analytics.console.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
        Instant createdAt,
        AnalyticsConsoleConversationMode mode,
        String questionProfileId,
        String namespace,
        String modelName,
        String modelRevision,
        List<AnalyticsConsoleAskBinding> askBindings,
        Instant archivedAt) {

    public AnalyticsConsoleConversation {
        conversationId = required(conversationId, "conversationId");
        ownerSubjectRef = required(ownerSubjectRef, "ownerSubjectRef");
        externalConversationRef = required(externalConversationRef, "externalConversationRef");
        askRequestId = required(askRequestId, "askRequestId");
        askInvocationRef = required(askInvocationRef, "askInvocationRef");
        runtimeExecutionId = required(runtimeExecutionId, "runtimeExecutionId");
        runtimeTaskId = required(runtimeTaskId, "runtimeTaskId");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (archivedAt != null && archivedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("archivedAt must not precede createdAt");
        }
        mode = mode == null ? AnalyticsConsoleConversationMode.DESIGN : mode;
        if (mode == AnalyticsConsoleConversationMode.DESIGN) {
            assetId = required(assetId, "assetId");
            if (questionProfileId != null || namespace != null
                    || modelName != null || modelRevision != null) {
                throw new IllegalArgumentException(
                        "design conversation cannot contain a question profile");
            }
        } else {
            if (assetId != null) {
                throw new IllegalArgumentException(
                        "question conversation cannot be bound to an asset");
            }
            questionProfileId = required(questionProfileId, "questionProfileId");
            namespace = required(namespace, "namespace");
            if ((modelName == null) != (modelRevision == null)) {
                throw new IllegalArgumentException(
                        "legacy question modelName and modelRevision must both be set or both be null");
            }
            if (modelName != null) {
                modelName = required(modelName, "modelName");
                modelRevision = required(modelRevision, "modelRevision");
            }
        }
        AnalyticsConsoleAskBinding initial = new AnalyticsConsoleAskBinding(
                askRequestId,
                askInvocationRef,
                runtimeExecutionId,
                runtimeTaskId,
                createdAt);
        if (askBindings == null || askBindings.isEmpty()) {
            askBindings = List.of(initial);
        } else {
            askBindings = List.copyOf(askBindings);
            if (!askBindings.get(0).equals(initial)) {
                throw new IllegalArgumentException(
                        "initial Ask binding does not match conversation fields");
            }
        }
    }

    /** Source-compatible constructor for catalog clients created before archiving support. */
    public AnalyticsConsoleConversation(
            String conversationId,
            String assetId,
            String ownerSubjectRef,
            String externalConversationRef,
            String askRequestId,
            String askInvocationRef,
            String runtimeExecutionId,
            String runtimeTaskId,
            Instant createdAt,
            AnalyticsConsoleConversationMode mode,
            String questionProfileId,
            String namespace,
            String modelName,
            String modelRevision,
            List<AnalyticsConsoleAskBinding> askBindings) {
        this(
                conversationId,
                assetId,
                ownerSubjectRef,
                externalConversationRef,
                askRequestId,
                askInvocationRef,
                runtimeExecutionId,
                runtimeTaskId,
                createdAt,
                mode,
                questionProfileId,
                namespace,
                modelName,
                modelRevision,
                askBindings,
                null);
    }

    /** Legacy-compatible constructor retained for existing catalog fixtures and callers. */
    public AnalyticsConsoleConversation(
            String conversationId,
            String assetId,
            String ownerSubjectRef,
            String externalConversationRef,
            String askRequestId,
            String askInvocationRef,
            String runtimeExecutionId,
            String runtimeTaskId,
            Instant createdAt) {
        this(
                conversationId,
                assetId,
                ownerSubjectRef,
                externalConversationRef,
                askRequestId,
                askInvocationRef,
                runtimeExecutionId,
                runtimeTaskId,
                createdAt,
                AnalyticsConsoleConversationMode.DESIGN,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public Optional<AnalyticsConsoleAskBinding> askBinding(
            String requestId,
            String invocationRef) {
        return askBindings.stream()
                .filter(value -> value.askRequestId().equals(requestId))
                .filter(value -> value.askInvocationRef().equals(invocationRef))
                .findFirst();
    }

    public AnalyticsConsoleConversation withAskBinding(
            AnalyticsConsoleAskBinding binding) {
        Objects.requireNonNull(binding, "binding");
        if (!runtimeExecutionId.equals(binding.runtimeExecutionId())) {
            throw new IllegalArgumentException(
                    "continued Ask must use the frozen Runtime execution");
        }
        List<AnalyticsConsoleAskBinding> next = new ArrayList<>(askBindings);
        next.add(binding);
        return new AnalyticsConsoleConversation(
                conversationId,
                assetId,
                ownerSubjectRef,
                externalConversationRef,
                askRequestId,
                askInvocationRef,
                runtimeExecutionId,
                runtimeTaskId,
                createdAt,
                mode,
                questionProfileId,
                namespace,
                modelName,
                modelRevision,
                next,
                archivedAt);
    }

    public boolean archived() {
        return archivedAt != null;
    }

    public AnalyticsConsoleConversation archiveAt(Instant value) {
        if (archived()) return this;
        return new AnalyticsConsoleConversation(
                conversationId,
                assetId,
                ownerSubjectRef,
                externalConversationRef,
                askRequestId,
                askInvocationRef,
                runtimeExecutionId,
                runtimeTaskId,
                createdAt,
                mode,
                questionProfileId,
                namespace,
                modelName,
                modelRevision,
                askBindings,
                Objects.requireNonNull(value, "archivedAt"));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }
}
