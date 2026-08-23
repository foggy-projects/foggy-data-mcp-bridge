package com.foggyframework.analytics.runtime.api.service;

import com.foggyframework.analytics.runtime.api.config.FoggyAnalyticsRuntimeApiProperties;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsRuntimeContext;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsRuntimeEnvelope;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsRuntimeError;

import java.util.UUID;

/** Creates independent Analytics envelopes and bounded correlation identifiers. */
public final class AnalyticsRuntimeApiResponseFactory {

    private static final int MAX_CORRELATION_LENGTH = 256;

    private final FoggyAnalyticsRuntimeApiProperties properties;

    public AnalyticsRuntimeApiResponseFactory(
            FoggyAnalyticsRuntimeApiProperties properties) {
        this.properties = properties;
    }

    public <T> AnalyticsRuntimeEnvelope<T> ok(
            T data,
            String requestId,
            String traceId) {
        return AnalyticsRuntimeEnvelope.ok(
                properties.getRuntimeApiVersion(),
                properties.getSchemaVersion(),
                data,
                context(requestId, traceId));
    }

    public <T> AnalyticsRuntimeEnvelope<T> fail(
            String code,
            String phase,
            String message,
            boolean retryable,
            String requestId,
            String traceId) {
        return AnalyticsRuntimeEnvelope.fail(
                properties.getRuntimeApiVersion(),
                properties.getSchemaVersion(),
                new AnalyticsRuntimeError(code, phase, message, retryable),
                context(requestId, traceId));
    }

    public AnalyticsRuntimeContext context(String requestId, String traceId) {
        String normalizedRequestId = correlationOrGenerated(requestId, "request");
        String normalizedTraceId = validCorrelation(traceId)
                ? traceId
                : normalizedRequestId;
        return new AnalyticsRuntimeContext(normalizedRequestId, normalizedTraceId);
    }

    private static String correlationOrGenerated(String value, String prefix) {
        return validCorrelation(value)
                ? value
                : "analytics-" + prefix + '-' + UUID.randomUUID();
    }

    private static boolean validCorrelation(String value) {
        return value != null
                && !value.isBlank()
                && value.equals(value.trim())
                && value.length() <= MAX_CORRELATION_LENGTH;
    }
}
