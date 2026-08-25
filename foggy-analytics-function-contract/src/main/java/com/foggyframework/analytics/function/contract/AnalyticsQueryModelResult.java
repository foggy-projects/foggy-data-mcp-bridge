package com.foggyframework.analytics.function.contract;

import java.util.Map;
import java.util.Objects;

/** JSON-safe response from the full query-model DSL surface. */
public record AnalyticsQueryModelResult(
        String namespace,
        String modelName,
        String modelRevision,
        String mode,
        Map<String, Object> response) {

    public AnalyticsQueryModelResult {
        namespace = AnalyticsFunctionValues.requireText("namespace", namespace);
        modelName = AnalyticsFunctionValues.requireText("modelName", modelName);
        modelRevision = AnalyticsFunctionValues.requireRevision(
                "modelRevision", modelRevision);
        mode = AnalyticsFunctionValues.requireText("mode", mode);
        response = AnalyticsFunctionJsonValues.normalizeObject(
                "response", Objects.requireNonNull(response, "response"));
    }
}
