package com.foggyframework.analytics.runtime.core.render;

import com.foggyframework.analytics.definition.api.AnalyticsArtifactKind;
import com.foggyframework.analytics.definition.api.AnalyticsArtifactRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;

import java.util.Objects;

/** Exact-revision request to render one Dashboard definition. */
public record AnalyticsDashboardRenderRequest(
        AnalyticsBundleRef bundleRef,
        AnalyticsBundleRevision expectedBundleRevision,
        AnalyticsArtifactRef dashboardRef,
        AnalyticsRenderRequestContext context) {

    public AnalyticsDashboardRenderRequest {
        bundleRef = Objects.requireNonNull(bundleRef, "bundleRef");
        expectedBundleRevision = Objects.requireNonNull(
                expectedBundleRevision,
                "expectedBundleRevision");
        dashboardRef = Objects.requireNonNull(dashboardRef, "dashboardRef");
        if (dashboardRef.kind() != AnalyticsArtifactKind.DASHBOARD) {
            throw new IllegalArgumentException("dashboardRef must have DASHBOARD kind");
        }
        context = Objects.requireNonNull(context, "context");
    }
}
