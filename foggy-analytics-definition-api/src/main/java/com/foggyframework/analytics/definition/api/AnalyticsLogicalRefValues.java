package com.foggyframework.analytics.definition.api;

import java.util.Objects;
import java.util.regex.Pattern;

/** Shared URL-safe grammar for stable Bundle, artifact and query identities. */
final class AnalyticsLogicalRefValues {

    private static final Pattern VALUE = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._~-]{0,127}");

    private AnalyticsLogicalRefValues() {
    }

    static String require(String field, String value) {
        Objects.requireNonNull(value, field);
        if (!VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    field + " must be one URL-safe logical path segment");
        }
        return value;
    }
}
