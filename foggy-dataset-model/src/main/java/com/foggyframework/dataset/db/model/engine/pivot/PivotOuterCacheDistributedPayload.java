package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;

/**
 * Serializable payload envelope for distributed Pivot outer-cache providers.
 */
public record PivotOuterCacheDistributedPayload(String payloadVersion,
                                                String payloadContentType,
                                                long storedAtMillis,
                                                long expiresAtMillis,
                                                String namespace,
                                                String model,
                                                SemanticQueryResponse response) {

    public PivotOuterCacheDistributedPayload {
        payloadVersion = normalizeRequired(payloadVersion, "payloadVersion");
        payloadContentType = normalizeRequired(payloadContentType, "payloadContentType");
        namespace = normalizeNamespace(namespace);
        model = normalizeModel(model);
        if (storedAtMillis < 0L) {
            throw new IllegalArgumentException("storedAtMillis must be non-negative");
        }
        if (expiresAtMillis <= storedAtMillis) {
            throw new IllegalArgumentException("expiresAtMillis must be greater than storedAtMillis");
        }
        if (response == null) {
            throw new IllegalArgumentException("response must not be null");
        }
    }

    public long ageMs(long nowMillis) {
        return Math.max(0L, nowMillis - storedAtMillis);
    }

    public boolean expired(long nowMillis) {
        return expiresAtMillis <= nowMillis;
    }

    private static String normalizeNamespace(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private static String normalizeModel(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private static String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.trim();
    }
}
