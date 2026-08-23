package com.foggyframework.analytics.definition.api;

import java.util.Objects;

/** A Report owns presentation intent and references exactly one governed QuerySpec. */
public record AnalyticsReportDefinition(
        AnalyticsArtifactRef artifactRef,
        AnalyticsQueryRef queryRef,
        AnalyticsVisualIntent visualIntent) {

    public AnalyticsReportDefinition {
        artifactRef = Objects.requireNonNull(artifactRef, "artifactRef");
        if (artifactRef.kind() != AnalyticsArtifactKind.REPORT) {
            throw new IllegalArgumentException("Report artifactRef must have REPORT kind");
        }
        queryRef = Objects.requireNonNull(queryRef, "queryRef");
        visualIntent = Objects.requireNonNull(visualIntent, "visualIntent");
    }
}
