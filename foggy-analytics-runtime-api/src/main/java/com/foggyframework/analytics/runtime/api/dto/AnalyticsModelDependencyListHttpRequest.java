package com.foggyframework.analytics.runtime.api.dto;

/** HTTP request body for namespace-scoped model dependency discovery. */
public record AnalyticsModelDependencyListHttpRequest(
        String namespace,
        String modelKind,
        String requestId,
        String traceId) {
}
