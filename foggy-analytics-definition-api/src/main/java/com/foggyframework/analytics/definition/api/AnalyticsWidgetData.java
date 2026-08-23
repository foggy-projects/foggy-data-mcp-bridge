package com.foggyframework.analytics.definition.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded, renderer-neutral data projection for one widget. */
public record AnalyticsWidgetData(
        String widgetRef,
        AnalyticsVisualIntent visualIntent,
        AnalyticsRenderState state,
        List<AnalyticsColumnSchema> columns,
        List<Map<String, Object>> rows,
        boolean truncated,
        List<String> diagnostics) {

    public AnalyticsWidgetData {
        widgetRef = AnalyticsLogicalRefValues.require("widgetRef", widgetRef);
        visualIntent = Objects.requireNonNull(visualIntent, "visualIntent");
        state = Objects.requireNonNull(state, "state");
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        Objects.requireNonNull(rows, "rows");
        rows = rows.stream()
                .map(AnalyticsWidgetData::immutableRow)
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
