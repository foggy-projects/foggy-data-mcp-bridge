package com.foggyframework.analytics.function.contract;

import java.util.Objects;

/** Exact logical Bundle request without a filesystem path or product ownership. */
public record AnalyticsBundleFunctionRequest(
        String bundleRef,
        String expectedBundleRevision,
        AnalyticsFunctionRequestContext context) {

    public AnalyticsBundleFunctionRequest {
        bundleRef = AnalyticsFunctionValues.requireLogicalRef(
                "bundleRef", bundleRef);
        expectedBundleRevision = AnalyticsFunctionValues.optionalRevision(
                expectedBundleRevision);
        context = Objects.requireNonNull(context, "context");
    }
}
