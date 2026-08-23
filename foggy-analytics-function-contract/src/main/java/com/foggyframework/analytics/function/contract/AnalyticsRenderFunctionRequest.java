package com.foggyframework.analytics.function.contract;

import java.util.Map;
import java.util.Objects;

/** Shared Report/Dashboard invocation with exact revision and opaque authority. */
public record AnalyticsRenderFunctionRequest(
        String bundleRef,
        String artifactRef,
        String expectedBundleRevision,
        Map<String, Object> parameters,
        String timezone,
        String locale,
        AnalyticsFunctionAuthority authority,
        AnalyticsFunctionRequestContext context) {

    public AnalyticsRenderFunctionRequest {
        bundleRef = AnalyticsFunctionValues.requireLogicalRef(
                "bundleRef", bundleRef);
        artifactRef = AnalyticsFunctionValues.requireLogicalRef(
                "artifactRef", artifactRef);
        expectedBundleRevision = AnalyticsFunctionValues.requireRevision(
                expectedBundleRevision);
        parameters = immutableParameters(parameters);
        timezone = AnalyticsFunctionValues.requireText("timezone", timezone);
        locale = AnalyticsFunctionValues.requireText("locale", locale);
        authority = Objects.requireNonNull(authority, "authority");
        context = Objects.requireNonNull(context, "context");
    }

    private static Map<String, Object> immutableParameters(
            Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return AnalyticsFunctionJsonValues.normalizeObject("parameters", source);
    }
}
