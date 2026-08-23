package com.foggyframework.analytics.definition.api;

import java.util.Objects;

/** Stable logical identity of a Report or Dashboard inside an Analytics Bundle. */
public record AnalyticsArtifactRef(AnalyticsArtifactKind kind, String value) {

    public AnalyticsArtifactRef {
        kind = Objects.requireNonNull(kind, "kind");
        value = AnalyticsLogicalRefValues.require("artifactRef", value);
    }
}
