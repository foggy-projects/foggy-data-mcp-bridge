package com.foggyframework.analytics.function.contract;

import java.util.Objects;
import java.util.regex.Pattern;

final class AnalyticsFunctionValues {

    private static final Pattern REVISION = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final Pattern LOGICAL_REF = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._~-]{0,127}");
    private static final int MAX_CORRELATION_LENGTH = 256;

    private AnalyticsFunctionValues() {
    }

    static String requireText(String field, String value) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }

    static String optionalRevision(String value) {
        if (value == null) {
            return null;
        }
        return requireRevision("expectedBundleRevision", value);
    }

    static String requireLogicalRef(String field, String value) {
        requireText(field, value);
        if (!LOGICAL_REF.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    field + " must be one URL-safe logical path segment");
        }
        return value;
    }

    static String requireRevision(String value) {
        return requireRevision("expectedBundleRevision", value);
    }

    static String requireRevision(String field, String value) {
        requireText(field, value);
        if (!REVISION.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    field + " must be a canonical SHA-256 revision");
        }
        return value;
    }

    static String optionalCorrelation(String field, String value) {
        if (value == null) {
            return null;
        }
        requireText(field, value);
        if (value.length() > MAX_CORRELATION_LENGTH) {
            throw new IllegalArgumentException(field + " is too long");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x21 || character > 0x7e) {
                throw new IllegalArgumentException(
                        field + " must contain only visible ASCII characters");
            }
        }
        return value;
    }

    static String requireCorrelation(String field, String value) {
        optionalCorrelation(field, value);
        if (value == null) {
            throw new NullPointerException(field);
        }
        return value;
    }
}
