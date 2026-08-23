package com.foggyframework.analytics.function.contract;

/** Opaque authority handle; raw ACL, filter and product identity are excluded. */
public record AnalyticsFunctionAuthority(String provider, String reference) {

    public AnalyticsFunctionAuthority {
        provider = AnalyticsFunctionValues.requireText("authority.provider", provider);
        reference = AnalyticsFunctionValues.requireText("authority.reference", reference);
    }
}
