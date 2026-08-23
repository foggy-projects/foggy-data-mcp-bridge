package com.foggyframework.analytics.runtime.api.service;

import com.foggyframework.analytics.runtime.api.config.FoggyAnalyticsRuntimeApiProperties;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContext;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionError;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;
import com.foggyframework.analytics.runtime.core.function.AnalyticsFunctionResponseFactory;

/** Creates independent Analytics envelopes and bounded correlation identifiers. */
public final class AnalyticsRuntimeApiResponseFactory {

    private static final int MAX_CORRELATION_LENGTH = 256;

    private final AnalyticsFunctionResponseFactory delegate;

    public AnalyticsRuntimeApiResponseFactory(
            FoggyAnalyticsRuntimeApiProperties properties) {
        this.delegate = new AnalyticsFunctionResponseFactory(
                properties.getRuntimeApiVersion(),
                properties.getSchemaVersion());
    }

    public <T> AnalyticsFunctionEnvelope<T> ok(
            T data,
            String requestId,
            String traceId) {
        return delegate.ok(data, context(requestId, traceId));
    }

    public <T> AnalyticsFunctionEnvelope<T> fail(
            String code,
            String phase,
            String message,
            boolean retryable,
            String requestId,
            String traceId) {
        return delegate.fail(
                new AnalyticsFunctionError(code, phase, message, retryable),
                delegate.context(safeRequestContext(requestId, traceId)));
    }

    public AnalyticsFunctionRequestContext requestContext(
            String requestId,
            String traceId) {
        return new AnalyticsFunctionRequestContext(requestId, traceId);
    }

    public AnalyticsFunctionContext context(String requestId, String traceId) {
        return delegate.context(requestContext(requestId, traceId));
    }

    public AnalyticsFunctionResponseFactory functionResponses() {
        return delegate;
    }

    private static boolean validCorrelation(String value) {
        if (value == null
                || value.isBlank()
                || !value.equals(value.trim())
                || value.length() > MAX_CORRELATION_LENGTH) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x21 || character > 0x7e) {
                return false;
            }
        }
        return true;
    }

    private static AnalyticsFunctionRequestContext safeRequestContext(
            String requestId,
            String traceId) {
        return new AnalyticsFunctionRequestContext(
                validCorrelation(requestId) ? requestId : null,
                validCorrelation(traceId) ? traceId : null);
    }
}
