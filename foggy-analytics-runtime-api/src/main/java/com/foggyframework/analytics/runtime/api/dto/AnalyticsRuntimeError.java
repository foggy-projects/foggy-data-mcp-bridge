package com.foggyframework.analytics.runtime.api.dto;

/** Stable, sanitized Analytics API error projection. */
public record AnalyticsRuntimeError(
        String code,
        String phase,
        String message,
        boolean retryable) {
}
