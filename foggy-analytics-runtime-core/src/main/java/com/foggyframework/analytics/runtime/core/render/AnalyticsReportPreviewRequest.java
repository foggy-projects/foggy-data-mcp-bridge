package com.foggyframework.analytics.runtime.core.render;

import com.foggyframework.analytics.definition.api.AnalyticsArtifactKind;
import com.foggyframework.analytics.definition.api.AnalyticsArtifactRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;

import java.util.Objects;

/** Exact-revision request to preview one Report definition. */
public record AnalyticsReportPreviewRequest(
        AnalyticsBundleRef bundleRef,
        AnalyticsBundleRevision expectedBundleRevision,
        AnalyticsArtifactRef reportRef,
        AnalyticsRenderRequestContext context) {

    public AnalyticsReportPreviewRequest {
        bundleRef = Objects.requireNonNull(bundleRef, "bundleRef");
        expectedBundleRevision = Objects.requireNonNull(
                expectedBundleRevision,
                "expectedBundleRevision");
        reportRef = Objects.requireNonNull(reportRef, "reportRef");
        if (reportRef.kind() != AnalyticsArtifactKind.REPORT) {
            throw new IllegalArgumentException("reportRef must have REPORT kind");
        }
        context = Objects.requireNonNull(context, "context");
    }
}
