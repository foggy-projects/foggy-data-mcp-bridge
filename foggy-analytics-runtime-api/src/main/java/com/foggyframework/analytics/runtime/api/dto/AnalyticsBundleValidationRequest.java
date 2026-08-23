package com.foggyframework.analytics.runtime.api.dto;

/** Optional exact-revision assertion for a trusted Bundle registration. */
public record AnalyticsBundleValidationRequest(
        String expectedBundleRevision,
        String requestId,
        String traceId) {
}
