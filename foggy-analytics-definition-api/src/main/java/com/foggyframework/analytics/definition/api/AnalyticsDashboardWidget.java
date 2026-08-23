package com.foggyframework.analytics.definition.api;

import java.util.Objects;

/** A Dashboard widget references either a Report or a QuerySpec, never both. */
public record AnalyticsDashboardWidget(
        String widgetRef,
        AnalyticsArtifactRef reportRef,
        AnalyticsQueryRef queryRef,
        AnalyticsVisualIntent visualIntent) {

    public AnalyticsDashboardWidget {
        widgetRef = requireValue(widgetRef);
        if ((reportRef == null) == (queryRef == null)) {
            throw new IllegalArgumentException(
                    "A widget must reference exactly one of reportRef or queryRef");
        }
        if (reportRef != null && reportRef.kind() != AnalyticsArtifactKind.REPORT) {
            throw new IllegalArgumentException("Widget reportRef must have REPORT kind");
        }
        visualIntent = Objects.requireNonNull(visualIntent, "visualIntent");
    }

    private static String requireValue(String value) {
        Objects.requireNonNull(value, "widgetRef");
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException("widgetRef must be non-blank and trimmed");
        }
        return value;
    }
}
