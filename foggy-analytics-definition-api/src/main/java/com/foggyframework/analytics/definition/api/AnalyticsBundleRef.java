package com.foggyframework.analytics.definition.api;

import java.util.Objects;

/** Stable logical identity of an Analytics Bundle. */
public record AnalyticsBundleRef(String value) {

    public AnalyticsBundleRef {
        value = requireIdentity("bundleRef", value);
    }

    private static String requireIdentity(String field, String value) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }
}
