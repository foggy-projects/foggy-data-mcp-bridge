package com.foggyframework.analytics.definition.api;

import java.util.Objects;

/** Stable logical identity of a governed QuerySpec inside an Analytics Bundle. */
public record AnalyticsQueryRef(String value) {

    public AnalyticsQueryRef {
        Objects.requireNonNull(value, "queryRef");
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException("queryRef must be non-blank and trimmed");
        }
    }
}
