package com.foggyframework.analytics.function.contract;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Canonical immutable JSON value domain shared by embedded and HTTP calls. */
public final class AnalyticsFunctionJsonValues {

    private static final int MAX_NESTING = 64;

    private AnalyticsFunctionJsonValues() {
    }

    /**
     * Recursively copies a JSON object and canonicalizes every number.
     * Integral numbers become {@link BigInteger}; decimal numbers become
     * {@link BigDecimal}. Null, boolean, string, list and string-keyed map
     * values are supported. Dates and product-specific objects must be encoded
     * explicitly as strings before crossing the Function boundary.
     */
    public static Map<String, Object> normalizeObject(
            String field,
            Map<?, ?> source) {
        String normalizedField = AnalyticsFunctionValues.requireText(
                "field", field);
        Objects.requireNonNull(source, normalizedField);
        @SuppressWarnings("unchecked")
        Map<String, Object> normalized = (Map<String, Object>) normalize(
                normalizedField,
                source,
                0,
                new IdentityHashMap<>());
        return normalized;
    }

    /** Recursively normalizes one value into the Function JSON value domain. */
    public static Object normalizeValue(String field, Object value) {
        return normalize(
                AnalyticsFunctionValues.requireText("field", field),
                value,
                0,
                new IdentityHashMap<>());
    }

    private static Object normalize(
            String field,
            Object value,
            int depth,
            IdentityHashMap<Object, Boolean> visiting) {
        if (depth > MAX_NESTING) {
            throw invalid(field, "exceeds the maximum nesting depth");
        }
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof BigInteger integer) {
            return integer;
        }
        if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long) {
            return BigInteger.valueOf(((Number) value).longValue());
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Float || value instanceof Double) {
            double decimal = ((Number) value).doubleValue();
            if (!Double.isFinite(decimal)) {
                throw invalid(field, "contains a non-finite number");
            }
            return BigDecimal.valueOf(decimal);
        }
        if (value instanceof Map<?, ?> map) {
            enter(field, map, visiting);
            try {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) {
                        throw invalid(field, "contains a non-string object key");
                    }
                    String normalizedKey = AnalyticsFunctionValues.requireText(
                            field + " key", key);
                    copy.put(
                            normalizedKey,
                            normalize(
                                    field + '.' + normalizedKey,
                                    entry.getValue(),
                                    depth + 1,
                                    visiting));
                }
                return Collections.unmodifiableMap(copy);
            } finally {
                visiting.remove(map);
            }
        }
        if (value instanceof List<?> list) {
            enter(field, list, visiting);
            try {
                List<Object> copy = new ArrayList<>(list.size());
                for (int index = 0; index < list.size(); index++) {
                    copy.add(normalize(
                            field + '[' + index + ']',
                            list.get(index),
                            depth + 1,
                            visiting));
                }
                return Collections.unmodifiableList(copy);
            } finally {
                visiting.remove(list);
            }
        }
        throw invalid(field, "contains an unsupported value type");
    }

    private static void enter(
            String field,
            Object value,
            IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw invalid(field, "contains a cyclic value");
        }
    }

    private static IllegalArgumentException invalid(String field, String reason) {
        return new IllegalArgumentException(field + ' ' + reason);
    }
}
