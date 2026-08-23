package com.foggyframework.analytics.function.contract;

import java.util.Objects;
import java.util.UUID;

/** Normalized correlation context returned by every function invocation. */
public record AnalyticsFunctionContext(String requestId, String traceId) {

    public AnalyticsFunctionContext {
        requestId = AnalyticsFunctionValues.requireCorrelation("requestId", requestId);
        traceId = AnalyticsFunctionValues.requireCorrelation("traceId", traceId);
    }

    public static AnalyticsFunctionContext normalize(
            AnalyticsFunctionRequestContext requested) {
        AnalyticsFunctionRequestContext source = Objects.requireNonNull(
                requested, "requested");
        String requestId = source.requestId() == null
                ? "analytics-request-" + UUID.randomUUID()
                : source.requestId();
        String traceId = source.traceId() == null ? requestId : source.traceId();
        return new AnalyticsFunctionContext(requestId, traceId);
    }
}
