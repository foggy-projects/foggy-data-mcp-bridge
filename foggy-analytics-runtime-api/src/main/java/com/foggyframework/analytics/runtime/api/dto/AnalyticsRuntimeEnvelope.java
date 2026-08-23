package com.foggyframework.analytics.runtime.api.dto;

/** Analytics-owned envelope; it deliberately does not reuse Foggy Runtime API DTOs. */
public record AnalyticsRuntimeEnvelope<T>(
        boolean success,
        String engine,
        String analyticsRuntimeApiVersion,
        String schemaVersion,
        T data,
        AnalyticsRuntimeContext context,
        AnalyticsRuntimeError error) {

    public static <T> AnalyticsRuntimeEnvelope<T> ok(
            String version,
            String schemaVersion,
            T data,
            AnalyticsRuntimeContext context) {
        return new AnalyticsRuntimeEnvelope<>(
                true,
                "java",
                version,
                schemaVersion,
                data,
                context,
                null);
    }

    public static <T> AnalyticsRuntimeEnvelope<T> fail(
            String version,
            String schemaVersion,
            AnalyticsRuntimeError error,
            AnalyticsRuntimeContext context) {
        return new AnalyticsRuntimeEnvelope<>(
                false,
                "java",
                version,
                schemaVersion,
                null,
                context,
                error);
    }
}
