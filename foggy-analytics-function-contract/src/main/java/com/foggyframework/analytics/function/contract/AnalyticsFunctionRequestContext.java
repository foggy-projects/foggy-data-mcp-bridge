package com.foggyframework.analytics.function.contract;

/** Optional caller correlation values; the Runtime generates missing values. */
public record AnalyticsFunctionRequestContext(String requestId, String traceId) {

    public AnalyticsFunctionRequestContext {
        requestId = AnalyticsFunctionValues.optionalCorrelation("requestId", requestId);
        traceId = AnalyticsFunctionValues.optionalCorrelation("traceId", traceId);
    }

    public static AnalyticsFunctionRequestContext empty() {
        return new AnalyticsFunctionRequestContext(null, null);
    }
}
