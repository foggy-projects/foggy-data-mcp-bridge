package com.foggyframework.analytics.runtime.api.dto;

/** Correlation context echoed without exposing product identity or authority data. */
public record AnalyticsRuntimeContext(String requestId, String traceId) {
}
