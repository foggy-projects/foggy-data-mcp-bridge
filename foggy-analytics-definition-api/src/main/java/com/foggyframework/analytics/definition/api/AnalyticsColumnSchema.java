package com.foggyframework.analytics.definition.api;

import java.util.Objects;

/** Renderer-neutral result-column metadata. */
public record AnalyticsColumnSchema(String name, String type, boolean nullable) {

    public AnalyticsColumnSchema {
        name = requireValue("name", name);
        type = requireValue("type", type);
    }

    private static String requireValue(String field, String value) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }
}
