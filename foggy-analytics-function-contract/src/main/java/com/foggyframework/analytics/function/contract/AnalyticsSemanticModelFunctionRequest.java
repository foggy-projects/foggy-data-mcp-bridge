package com.foggyframework.analytics.function.contract;

import java.util.Objects;

/** Governed current semantic-model read used by question-answering agents. */
public record AnalyticsSemanticModelFunctionRequest(
        String namespace,
        String modelName,
        AnalyticsFunctionAuthority authority,
        AnalyticsFunctionRequestContext context) {

    public AnalyticsSemanticModelFunctionRequest {
        namespace = AnalyticsFunctionValues.requireText("namespace", namespace);
        modelName = AnalyticsFunctionValues.requireText("modelName", modelName);
        authority = Objects.requireNonNull(authority, "authority");
        context = Objects.requireNonNull(context, "context");
    }
}
