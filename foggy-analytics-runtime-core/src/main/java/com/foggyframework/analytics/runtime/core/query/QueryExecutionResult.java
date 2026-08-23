package com.foggyframework.analytics.runtime.core.query;

import com.foggyframework.analytics.definition.api.AnalyticsColumnSchema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded product-neutral query result returned by an adapter QueryExecutor. */
public record QueryExecutionResult(
        List<AnalyticsColumnSchema> columns,
        List<Map<String, Object>> rows,
        boolean truncated,
        List<String> diagnostics) {

    public QueryExecutionResult {
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        Objects.requireNonNull(rows, "rows");
        rows = rows.stream()
                .map(QueryExecutionResult::immutableRow)
                .toList();
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    private static Map<String, Object> immutableRow(Map<String, Object> row) {
        Objects.requireNonNull(row, "row");
        Map<String, Object> copy = new LinkedHashMap<>();
        row.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "row column"),
                value));
        return Collections.unmodifiableMap(copy);
    }
}
