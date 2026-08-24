package com.foggyframework.analytics.function.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Sanitized tabular evidence returned to an Analytics question agent. */
public record AnalyticsSemanticQueryResult(
        String namespace,
        String modelName,
        String modelRevision,
        List<Column> columns,
        List<Map<String, Object>> rows,
        Long total,
        boolean hasMore,
        boolean truncated,
        List<String> warnings) {

    public AnalyticsSemanticQueryResult {
        namespace = AnalyticsFunctionValues.requireText("namespace", namespace);
        modelName = AnalyticsFunctionValues.requireText("modelName", modelName);
        modelRevision = AnalyticsFunctionValues.requireRevision(
                "modelRevision", modelRevision);
        columns = columns == null ? List.of() : List.copyOf(columns);
        List<Map<String, Object>> safeRows = new ArrayList<>();
        if (rows != null) {
            rows.forEach(row -> safeRows.add(
                    AnalyticsFunctionJsonValues.normalizeObject("row", row)));
        }
        rows = List.copyOf(safeRows);
        warnings = warnings == null ? List.of() : warnings.stream()
                .map(value -> AnalyticsFunctionValues.requireText("warning", value))
                .toList();
        if (total != null && total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
    }

    public record Column(String name, String type, String title) {
        public Column {
            name = AnalyticsFunctionValues.requireText("column.name", name);
            type = AnalyticsFunctionValues.requireText("column.type", type);
            title = title == null ? null : AnalyticsFunctionValues.requireText(
                    "column.title", title);
        }
    }
}
