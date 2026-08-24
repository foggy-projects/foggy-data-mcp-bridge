package com.foggyframework.analytics.function.contract;

import java.util.Objects;

/** Exact governed semantic-model read used by question-answering agents. */
public record AnalyticsSemanticModelFunctionRequest(
        String namespace,
        String modelName,
        String expectedModelRevision,
        AnalyticsFunctionAuthority authority,
        AnalyticsFunctionRequestContext context) {

    public AnalyticsSemanticModelFunctionRequest {
        namespace = AnalyticsFunctionValues.requireText("namespace", namespace);
        modelName = AnalyticsFunctionValues.requireText("modelName", modelName);
        expectedModelRevision = AnalyticsFunctionValues.requireRevision(
                "expectedModelRevision", expectedModelRevision);
        authority = Objects.requireNonNull(authority, "authority");
        context = Objects.requireNonNull(context, "context");
    }
}
