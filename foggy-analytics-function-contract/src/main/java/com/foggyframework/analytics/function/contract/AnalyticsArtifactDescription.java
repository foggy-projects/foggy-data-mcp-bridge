package com.foggyframework.analytics.function.contract;

/** Exact, parsed artifact identity returned by Analytics Runtime inspection. */
public record AnalyticsArtifactDescription(
        String bundleRef,
        String bundleRevision,
        String artifactKind,
        String artifactRef) {

    public AnalyticsArtifactDescription {
        bundleRef = AnalyticsFunctionValues.requireLogicalRef("bundleRef", bundleRef);
        bundleRevision = AnalyticsFunctionValues.requireRevision(
                "bundleRevision", bundleRevision);
        artifactKind = AnalyticsFunctionValues.requireArtifactKind(artifactKind);
        artifactRef = AnalyticsFunctionValues.requireLogicalRef("artifactRef", artifactRef);
    }
}
