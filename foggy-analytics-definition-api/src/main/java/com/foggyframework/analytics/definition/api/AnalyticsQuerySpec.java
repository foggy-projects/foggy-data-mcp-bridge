package com.foggyframework.analytics.definition.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Governed semantic query definition.
 *
 * <p>The contract intentionally has no raw SQL, script or authority-filter field.</p>
 */
public record AnalyticsQuerySpec(
        AnalyticsQueryRef queryRef,
        AnalyticsNamespaceRef namespaceRef,
        String modelName,
        List<String> columns,
        List<String> groupBy) {

    public AnalyticsQuerySpec {
        queryRef = Objects.requireNonNull(queryRef, "queryRef");
        namespaceRef = Objects.requireNonNull(namespaceRef, "namespaceRef");
        modelName = requireValue("modelName", modelName);
        columns = immutableUniqueValues("columns", columns, false);
        groupBy = immutableUniqueValues("groupBy", groupBy, true);
    }

    private static List<String> immutableUniqueValues(
            String field,
            List<String> values,
            boolean allowEmpty) {
        Objects.requireNonNull(values, field);
        List<String> copy = values.stream()
                .map(value -> requireValue(field, value))
                .toList();
        if (!allowEmpty && copy.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        if (new HashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException(field + " must not contain duplicates");
        }
        return List.copyOf(copy);
    }

    private static String requireValue(String field, String value) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }
}
