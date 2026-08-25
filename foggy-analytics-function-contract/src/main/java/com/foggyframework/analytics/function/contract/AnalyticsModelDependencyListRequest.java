package com.foggyframework.analytics.function.contract;

import java.util.Objects;
import java.util.Set;

/** Product-neutral request for current model identities within one namespace. */
public record AnalyticsModelDependencyListRequest(
        String namespace,
        String modelKind,
        AnalyticsFunctionRequestContext context) {

    private static final Set<String> MODEL_KINDS = Set.of("tm", "qm");

    public AnalyticsModelDependencyListRequest {
        namespace = AnalyticsFunctionValues.requireText("namespace", namespace);
        modelKind = AnalyticsFunctionValues.requireText("modelKind", modelKind);
        if (!MODEL_KINDS.contains(modelKind)) {
            throw new IllegalArgumentException("modelKind must be tm or qm");
        }
        context = Objects.requireNonNull(context, "context");
    }
}
