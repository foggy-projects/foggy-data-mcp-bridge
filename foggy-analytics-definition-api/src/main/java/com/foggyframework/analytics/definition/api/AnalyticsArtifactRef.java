package com.foggyframework.analytics.definition.api;

import java.util.Objects;

/** Stable logical identity of a Report or Dashboard inside an Analytics Bundle. */
public record AnalyticsArtifactRef(AnalyticsArtifactKind kind, String value) {

    public AnalyticsArtifactRef {
        kind = Objects.requireNonNull(kind, "kind");
        value = requireIdentity("artifactRef", value);
    }

    private static String requireIdentity(String field, String value) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }
}
