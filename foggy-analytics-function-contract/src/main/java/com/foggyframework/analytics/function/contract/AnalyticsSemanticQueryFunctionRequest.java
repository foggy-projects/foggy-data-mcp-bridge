package com.foggyframework.analytics.function.contract;

import java.util.Objects;

/** Authority-bound semantic query invocation for the current configured QM. */
public record AnalyticsSemanticQueryFunctionRequest(
        String namespace,
        String modelName,
        AnalyticsSemanticQuery query,
        AnalyticsFunctionAuthority authority,
        AnalyticsFunctionRequestContext context) {

    public AnalyticsSemanticQueryFunctionRequest {
        namespace = AnalyticsFunctionValues.requireText("namespace", namespace);
        modelName = AnalyticsFunctionValues.requireText("modelName", modelName);
        query = Objects.requireNonNull(query, "query");
        authority = Objects.requireNonNull(authority, "authority");
        context = Objects.requireNonNull(context, "context");
    }
}
