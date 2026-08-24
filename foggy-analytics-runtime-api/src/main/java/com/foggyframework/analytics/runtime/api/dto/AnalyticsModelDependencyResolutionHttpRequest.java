package com.foggyframework.analytics.runtime.api.dto;

/** Flat HTTP request for stable model-dependency discovery. */
public record AnalyticsModelDependencyResolutionHttpRequest(
        String namespace,
        String modelKind,
        String modelName,
        String requestId,
        String traceId) {
}
