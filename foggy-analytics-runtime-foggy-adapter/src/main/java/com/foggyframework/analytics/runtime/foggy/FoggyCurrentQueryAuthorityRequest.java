package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityBinding;

import java.util.Objects;

/** Request-local selection of the current valid QM for a live Function invocation. */
public record FoggyCurrentQueryAuthorityRequest(
        AnalyticsNamespaceRef namespace,
        String modelName,
        QueryAuthorityBinding binding,
        String requestId,
        String traceId) {

    public FoggyCurrentQueryAuthorityRequest {
        namespace = Objects.requireNonNull(namespace, "namespace");
        modelName = requireText("modelName", modelName);
        binding = Objects.requireNonNull(binding, "binding");
        requestId = requireText("requestId", requestId);
        traceId = requireText("traceId", traceId);
    }

    private static String requireText(String field, String value) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }
}
