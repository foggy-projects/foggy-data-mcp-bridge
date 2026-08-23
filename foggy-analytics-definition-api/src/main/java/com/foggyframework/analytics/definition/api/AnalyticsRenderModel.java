package com.foggyframework.analytics.definition.api;

import java.util.List;
import java.util.Objects;

/** Product-neutral runtime projection returned to Console, TMS or an SDK client. */
public record AnalyticsRenderModel(
        AnalyticsArtifactRef artifactRef,
        AnalyticsBundleRevision resolvedBundleRevision,
        AnalyticsRenderState state,
        List<AnalyticsWidgetData> widgets,
        List<String> diagnostics) {

    public AnalyticsRenderModel {
        artifactRef = Objects.requireNonNull(artifactRef, "artifactRef");
        resolvedBundleRevision = Objects.requireNonNull(
                resolvedBundleRevision, "resolvedBundleRevision");
        state = Objects.requireNonNull(state, "state");
        widgets = List.copyOf(Objects.requireNonNull(widgets, "widgets"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
}
