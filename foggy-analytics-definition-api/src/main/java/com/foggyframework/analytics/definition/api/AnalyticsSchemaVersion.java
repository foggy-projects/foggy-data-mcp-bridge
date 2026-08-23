package com.foggyframework.analytics.definition.api;

import java.util.Objects;
import java.util.regex.Pattern;

/** Version of the serialized Analytics Definition schema. */
public record AnalyticsSchemaVersion(String value) {

    private static final Pattern VERSION = Pattern.compile("[0-9]+(?:\\.[0-9]+)*");
    public static final AnalyticsSchemaVersion V1 = new AnalyticsSchemaVersion("1.0");

    public AnalyticsSchemaVersion {
        Objects.requireNonNull(value, "schemaVersion");
        if (!VERSION.matcher(value).matches()) {
            throw new IllegalArgumentException("schemaVersion must be a numeric dotted version");
        }
    }
}
