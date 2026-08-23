package com.foggyframework.analytics.definition.api;

/** Stable logical identity of an Analytics Bundle. */
public record AnalyticsBundleRef(String value) {

    public AnalyticsBundleRef {
        value = AnalyticsLogicalRefValues.require("bundleRef", value);
    }
}
