package com.foggyframework.analytics.definition.api;

import java.util.Objects;

/** Stable namespace identity used to resolve Analytics model dependencies. */
public record AnalyticsNamespaceRef(String value) {

    public AnalyticsNamespaceRef {
        Objects.requireNonNull(value, "namespaceRef");
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException("namespaceRef must be non-blank and trimmed");
        }
    }
}
