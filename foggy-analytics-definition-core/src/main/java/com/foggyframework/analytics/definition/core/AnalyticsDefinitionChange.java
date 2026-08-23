package com.foggyframework.analytics.definition.core;

import java.util.Objects;

/** One deterministic logical change between two typed Analytics Bundle indexes. */
public record AnalyticsDefinitionChange(
        AnalyticsDefinitionType definitionType,
        String definitionRef,
        AnalyticsDefinitionChangeType changeType) {

    public AnalyticsDefinitionChange {
        definitionType = Objects.requireNonNull(definitionType, "definitionType");
        definitionRef = requireValue(definitionRef);
        changeType = Objects.requireNonNull(changeType, "changeType");
    }

    private static String requireValue(String value) {
        Objects.requireNonNull(value, "definitionRef");
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException("definitionRef must be non-blank and trimmed");
        }
        return value;
    }
}
