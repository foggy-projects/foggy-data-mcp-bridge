package com.foggyframework.analytics.function.contract;

import java.util.List;

/** JSON-safe evidence from restricted SemanticDSL Compose validation or execution. */
public record AnalyticsComposeResult(
        String namespace,
        String mode,
        boolean valid,
        boolean executed,
        Object value,
        String sql,
        List<Object> params,
        List<String> warnings) {

    public AnalyticsComposeResult {
        namespace = AnalyticsFunctionValues.requireText("namespace", namespace);
        mode = AnalyticsFunctionValues.requireText("mode", mode);
        value = AnalyticsFunctionJsonValues.normalizeValue("value", value);
        @SuppressWarnings("unchecked")
        List<Object> normalizedParams = (List<Object>) AnalyticsFunctionJsonValues
                .normalizeValue("params", params == null ? List.of() : params);
        params = normalizedParams;
        warnings = warnings == null ? List.of() : warnings.stream()
                .map(item -> AnalyticsFunctionValues.requireText("warning", item))
                .toList();
    }
}
