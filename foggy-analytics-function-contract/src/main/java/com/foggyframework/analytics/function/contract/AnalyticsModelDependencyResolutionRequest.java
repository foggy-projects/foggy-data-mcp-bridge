package com.foggyframework.analytics.function.contract;

import java.util.Objects;
import java.util.Set;

/** Product-neutral request for one current, persistable model dependency identity. */
public record AnalyticsModelDependencyResolutionRequest(
        String namespace,
        String modelKind,
        String modelName,
        AnalyticsFunctionRequestContext context) {

    private static final Set<String> MODEL_KINDS = Set.of("tm", "qm");

    public AnalyticsModelDependencyResolutionRequest {
        namespace = AnalyticsFunctionValues.requireText("namespace", namespace);
        modelKind = AnalyticsFunctionValues.requireText("modelKind", modelKind);
        if (!MODEL_KINDS.contains(modelKind)) {
            throw new IllegalArgumentException("modelKind must be tm or qm");
        }
        modelName = AnalyticsFunctionValues.requireText("modelName", modelName);
        context = Objects.requireNonNull(context, "context");
    }
}
