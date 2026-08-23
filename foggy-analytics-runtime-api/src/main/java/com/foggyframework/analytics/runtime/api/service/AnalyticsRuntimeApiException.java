package com.foggyframework.analytics.runtime.api.service;

import org.springframework.http.HttpStatus;

import java.util.Objects;

/** HTTP-boundary failure with a stable Analytics-owned code. */
public final class AnalyticsRuntimeApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String phase;
    private final boolean retryable;

    public AnalyticsRuntimeApiException(
            HttpStatus status,
            String code,
            String phase,
            String message,
            boolean retryable) {
        super(message);
        this.status = Objects.requireNonNull(status, "status");
        this.code = requireValue("code", code);
        this.phase = requireValue("phase", phase);
        this.retryable = retryable;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String phase() {
        return phase;
    }

    public boolean retryable() {
        return retryable;
    }

    private static String requireValue(String field, String value) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }
}
