package com.foggyframework.dataset.model.semantic.permission;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Request-local decision memoization. A new HTTP request must create a new
 * session; decisions are immutable once installed.
 */
public final class PermissionEvaluationSession {

    private final String traceId;
    private final Map<DecisionKey, PermissionDecision> decisions = new ConcurrentHashMap<>();

    public PermissionEvaluationSession() {
        this(UUID.randomUUID().toString());
    }

    public PermissionEvaluationSession(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        this.traceId = traceId;
    }

    public String getTraceId() {
        return traceId;
    }

    public PermissionDecision getOrEvaluate(
            String namespace,
            String model,
            PermissionAction action,
            Supplier<PermissionDecision> evaluator
    ) {
        DecisionKey key = new DecisionKey(canonical(namespace), requireText(model), action);
        return decisions.computeIfAbsent(key, ignored -> evaluator.get());
    }

    public int size() {
        return decisions.size();
    }

    private static String canonical(String namespace) {
        return namespace == null ? "" : namespace.trim();
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        return value.trim();
    }

    private record DecisionKey(String namespace, String model, PermissionAction action) {
    }
}
