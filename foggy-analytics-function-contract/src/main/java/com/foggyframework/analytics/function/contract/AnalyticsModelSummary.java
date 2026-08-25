package com.foggyframework.analytics.function.contract;

import java.util.Set;

/** Current selectable model identity without an externally visible content fingerprint. */
public record AnalyticsModelSummary(
        String namespace,
        String modelKind,
        String modelName) {

    private static final Set<String> MODEL_KINDS = Set.of("tm", "qm");

    public AnalyticsModelSummary {
        namespace = AnalyticsFunctionValues.requireText("namespace", namespace);
        modelKind = AnalyticsFunctionValues.requireText("modelKind", modelKind);
        if (!MODEL_KINDS.contains(modelKind)) {
            throw new IllegalArgumentException("modelKind must be tm or qm");
        }
        modelName = AnalyticsFunctionValues.requireText("modelName", modelName);
    }
}
