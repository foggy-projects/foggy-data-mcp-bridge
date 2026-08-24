package com.foggyframework.analytics.function.contract;

import java.util.Objects;

/** Exact, authority-bound semantic query invocation for a configured QM. */
public record AnalyticsSemanticQueryFunctionRequest(
        String namespace,
        String modelName,
        String expectedModelRevision,
        AnalyticsSemanticQuery query,
        AnalyticsFunctionAuthority authority,
        AnalyticsFunctionRequestContext context) {

    public AnalyticsSemanticQueryFunctionRequest {
        namespace = AnalyticsFunctionValues.requireText("namespace", namespace);
        modelName = AnalyticsFunctionValues.requireText("modelName", modelName);
        expectedModelRevision = AnalyticsFunctionValues.requireRevision(
                "expectedModelRevision", expectedModelRevision);
        query = Objects.requireNonNull(query, "query");
        authority = Objects.requireNonNull(authority, "authority");
        context = Objects.requireNonNull(context, "context");
    }
}
