package com.foggyframework.analytics.function.contract;

import java.util.Set;

/** Current selectable model summary without an externally visible content fingerprint. */
public record AnalyticsModelSummary(
        String namespace,
        String modelKind,
        String modelName,
        String description) {

    private static final Set<String> MODEL_KINDS = Set.of("tm", "qm");

    public AnalyticsModelSummary(
            String namespace,
            String modelKind,
            String modelName) {
        this(namespace, modelKind, modelName, "");
    }

    public AnalyticsModelSummary {
        namespace = AnalyticsFunctionValues.requireText("namespace", namespace);
        modelKind = AnalyticsFunctionValues.requireText("modelKind", modelKind);
        if (!MODEL_KINDS.contains(modelKind)) {
            throw new IllegalArgumentException("modelKind must be tm or qm");
        }
        modelName = AnalyticsFunctionValues.requireText("modelName", modelName);
        description = description == null ? "" : description.trim();
    }
}
