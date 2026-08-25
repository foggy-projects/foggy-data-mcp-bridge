package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.runtime.core.query.QueryAuthorityBinding;

import java.util.Objects;

/** Opaque authority request used to obtain a trusted Compose caller identity. */
public record FoggyComposeAuthorityRequest(
        String namespace,
        QueryAuthorityBinding binding,
        String requestId,
        String traceId) {

    public FoggyComposeAuthorityRequest {
        namespace = requireText("namespace", namespace);
        binding = Objects.requireNonNull(binding, "binding");
        requestId = requireText("requestId", requestId);
        traceId = requireText("traceId", traceId);
    }

    private static String requireText(String field, String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }
}
