package com.foggyframework.analytics.runtime.api.dto;

/** Exact revision assertion and correlation for artifact inspection. */
public record AnalyticsArtifactDescriptionRequest(
        String expectedBundleRevision,
        String requestId,
        String traceId) {
}
