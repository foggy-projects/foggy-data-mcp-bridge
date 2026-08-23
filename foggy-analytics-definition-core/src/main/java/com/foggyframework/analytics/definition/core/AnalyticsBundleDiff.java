package com.foggyframework.analytics.definition.core;

import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;

import java.util.List;
import java.util.Objects;

/** Typed logical diff between two revisions of the same Analytics Bundle. */
public record AnalyticsBundleDiff(
        AnalyticsBundleRef bundleRef,
        AnalyticsBundleRevision fromRevision,
        AnalyticsBundleRevision toRevision,
        List<AnalyticsDefinitionChange> changes) {

    public AnalyticsBundleDiff {
        bundleRef = Objects.requireNonNull(bundleRef, "bundleRef");
        fromRevision = Objects.requireNonNull(fromRevision, "fromRevision");
        toRevision = Objects.requireNonNull(toRevision, "toRevision");
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
    }
}
