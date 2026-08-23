package com.foggyframework.analytics.definition.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Product-neutral Dashboard composition definition. */
public record AnalyticsDashboardDefinition(
        AnalyticsArtifactRef artifactRef,
        List<AnalyticsDashboardWidget> widgets) {

    public AnalyticsDashboardDefinition {
        artifactRef = Objects.requireNonNull(artifactRef, "artifactRef");
        if (artifactRef.kind() != AnalyticsArtifactKind.DASHBOARD) {
            throw new IllegalArgumentException("Dashboard artifactRef must have DASHBOARD kind");
        }
        widgets = List.copyOf(Objects.requireNonNull(widgets, "widgets"));
        if (new HashSet<>(widgets.stream().map(AnalyticsDashboardWidget::widgetRef).toList()).size()
                != widgets.size()) {
            throw new IllegalArgumentException("Dashboard widgetRef values must be unique");
        }
    }
}
