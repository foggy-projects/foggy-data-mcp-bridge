package com.foggyframework.analytics.definition.api;

/** Stable logical identity of a governed QuerySpec inside an Analytics Bundle. */
public record AnalyticsQueryRef(String value) {

    public AnalyticsQueryRef {
        value = AnalyticsLogicalRefValues.require("queryRef", value);
    }
}
