package com.foggyframework.analytics.runtime.core.query;

import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;

import java.util.Objects;

/** Safe authority-resolution request without raw filters or product permission types. */
public record QueryAuthorityRequest(
        AnalyticsModelDependency modelDependency,
        QueryAuthorityBinding binding,
        String requestId,
        String traceId) {

    public QueryAuthorityRequest {
        modelDependency = Objects.requireNonNull(modelDependency, "modelDependency");
        binding = Objects.requireNonNull(binding, "binding");
        requestId = requireValue("requestId", requestId);
        traceId = requireValue("traceId", traceId);
    }

    private static String requireValue(String field, String value) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }
}
