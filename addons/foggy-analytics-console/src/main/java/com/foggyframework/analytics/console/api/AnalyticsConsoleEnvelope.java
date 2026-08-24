package com.foggyframework.analytics.console.api;

/** Product API envelope, separate from Analytics Runtime Function envelopes. */
public record AnalyticsConsoleEnvelope<T>(
        boolean success,
        T data,
        Error error,
        String requestId) {

    public static <T> AnalyticsConsoleEnvelope<T> ok(T data, String requestId) {
        return new AnalyticsConsoleEnvelope<>(true, data, null, requestId);
    }

    public static <T> AnalyticsConsoleEnvelope<T> fail(
            String code, String message, String requestId) {
        return new AnalyticsConsoleEnvelope<>(
                false, null, new Error(code, message), requestId);
    }

    public record Error(String code, String message) {
    }
}
