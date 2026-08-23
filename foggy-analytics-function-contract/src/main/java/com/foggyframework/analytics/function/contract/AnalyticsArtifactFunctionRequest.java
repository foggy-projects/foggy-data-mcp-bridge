package com.foggyframework.analytics.function.contract;

import java.util.Objects;

/** Exact artifact inspection request without product ownership or data authority. */
public record AnalyticsArtifactFunctionRequest(
        String bundleRef,
        String artifactKind,
        String artifactRef,
        String expectedBundleRevision,
        AnalyticsFunctionRequestContext context) {

    public AnalyticsArtifactFunctionRequest {
        bundleRef = AnalyticsFunctionValues.requireLogicalRef("bundleRef", bundleRef);
        artifactKind = AnalyticsFunctionValues.requireArtifactKind(artifactKind);
        artifactRef = AnalyticsFunctionValues.requireLogicalRef("artifactRef", artifactRef);
        expectedBundleRevision = AnalyticsFunctionValues.requireRevision(
                "expectedBundleRevision", expectedBundleRevision);
        context = Objects.requireNonNull(context, "context");
    }
}
