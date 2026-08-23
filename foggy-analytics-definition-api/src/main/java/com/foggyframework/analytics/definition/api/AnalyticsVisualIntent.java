package com.foggyframework.analytics.definition.api;

import java.util.Map;
import java.util.Objects;

/** Product-neutral display intent; renderer-native configuration belongs in adapters. */
public record AnalyticsVisualIntent(
        AnalyticsVisualKind kind,
        Map<String, String> hints) {

    public AnalyticsVisualIntent {
        kind = Objects.requireNonNull(kind, "kind");
        hints = Map.copyOf(Objects.requireNonNull(hints, "hints"));
    }
}
