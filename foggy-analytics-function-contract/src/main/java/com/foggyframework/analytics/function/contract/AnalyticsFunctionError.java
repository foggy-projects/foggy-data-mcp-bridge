package com.foggyframework.analytics.function.contract;

/** Stable sanitized error shared by embedded and HTTP transports. */
public record AnalyticsFunctionError(
        String code,
        String phase,
        String message,
        boolean retryable) {

    public AnalyticsFunctionError {
        code = AnalyticsFunctionValues.requireText("code", code);
        phase = AnalyticsFunctionValues.requireText("phase", phase);
        message = AnalyticsFunctionValues.requireText("message", message);
    }
}
