package com.foggyframework.analytics.function.fap;

import com.foggyframework.analytics.function.contract.AnalyticsFunctionJsonValues;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

final class FapAnalyticsValues {

    private static final Pattern OPAQUE_ID = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:-]{0,255}");
    private static final Pattern FUNCTION_REF = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:-]*(?:@v[1-9][0-9]*)?");
    private static final Pattern STABLE_NAME = Pattern.compile(
            "[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");
    private static final Pattern DIGEST = Pattern.compile(
            "sha256:[a-f0-9]{64}");
    private static final Pattern SAFE_ERROR_CODE = Pattern.compile(
            "[A-Z][A-Z0-9_]{0,127}");

    private FapAnalyticsValues() {
    }

    static String text(String field, String value, int maxLength) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || !value.equals(value.trim())
                || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must be non-blank, trimmed and at most "
                            + maxLength + " characters");
        }
        return value;
    }

    static String opaqueId(String field, String value) {
        text(field, value, 256);
        if (!OPAQUE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not a FAP opaque id");
        }
        return value;
    }

    static String functionRef(String value) {
        text("functionRef", value, 256);
        if (!FUNCTION_REF.matcher(value).matches()) {
            throw new IllegalArgumentException("functionRef is invalid");
        }
        return value;
    }

    static String stableName(String value) {
        text("name", value, 160);
        if (!STABLE_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("name is not a FAP stable name");
        }
        return value;
    }

    static String digest(String field, String value) {
        text(field, value, 71);
        if (!DIGEST.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not a canonical SHA-256 digest");
        }
        return value;
    }

    static String safeErrorCode(String value) {
        text("code", value, 128);
        if (!SAFE_ERROR_CODE.matcher(value).matches()) {
            throw new IllegalArgumentException("code is not safe for FAP callback projection");
        }
        return value;
    }

    static Map<String, Object> object(String field, Map<?, ?> value) {
        return AnalyticsFunctionJsonValues.normalizeObject(field, value);
    }

    static Object jsonValue(String field, Object value) {
        return AnalyticsFunctionJsonValues.normalizeValue(field, value);
    }

    static List<String> tags(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> normalized = values.stream()
                .map(value -> text("tag", value, 128))
                .distinct()
                .sorted()
                .toList();
        if (normalized.size() > 32) {
            throw new IllegalArgumentException("tags exceed the FAP limit");
        }
        return normalized;
    }
}
