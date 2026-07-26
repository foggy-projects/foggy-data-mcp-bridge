package com.foggyframework.dataset.model.spi.preagg;

import java.util.Locale;

/**
 * Permission scope used while materializing a pre-aggregation.
 */
public enum PreAggregationBuildMode {
    GLOBAL,
    SECURITY_SCOPED;

    public static PreAggregationBuildMode parse(String value) {
        if (value == null || value.isBlank()) {
            return GLOBAL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unsupported pre-aggregation buildMode: " + value, ex);
        }
    }
}
