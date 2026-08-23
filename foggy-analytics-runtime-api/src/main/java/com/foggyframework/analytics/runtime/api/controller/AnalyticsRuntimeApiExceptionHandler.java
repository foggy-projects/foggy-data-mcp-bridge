package com.foggyframework.analytics.runtime.api.controller;

import com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsRuntimeEnvelope;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeApiException;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeApiResponseFactory;
import com.foggyframework.analytics.runtime.core.render.AnalyticsRenderException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Sanitizes Analytics failures and does not expose paths, authority handles, SQL or internals. */
@RestControllerAdvice(assignableTypes = {
        AnalyticsCapabilitiesController.class,
        AnalyticsBundlesController.class,
        AnalyticsRenderController.class
})
@ConditionalOnProperty(
        prefix = "foggy.analytics.runtime-api",
        name = "enabled",
        havingValue = "true")
public class AnalyticsRuntimeApiExceptionHandler {

    private final AnalyticsRuntimeApiResponseFactory responses;

    public AnalyticsRuntimeApiExceptionHandler(
            AnalyticsRuntimeApiResponseFactory responses) {
        this.responses = responses;
    }

    @ExceptionHandler(AnalyticsRuntimeApiException.class)
    public ResponseEntity<AnalyticsRuntimeEnvelope<Void>> apiFailure(
            AnalyticsRuntimeApiException failure,
            HttpServletRequest request) {
        return failure(
                failure.status(),
                failure.code(),
                failure.phase(),
                failure.getMessage(),
                failure.retryable(),
                request);
    }

    @ExceptionHandler(AnalyticsBundleStoreException.class)
    public ResponseEntity<AnalyticsRuntimeEnvelope<Void>> bundleFailure(
            AnalyticsBundleStoreException failure,
            HttpServletRequest request) {
        return failure(
                bundleStatus(failure.code()),
                "ANALYTICS_BUNDLE_" + failure.code().name(),
                "bundle",
                bundleMessage(failure.code()),
                failure.code() == AnalyticsBundleStoreException.Code.BUNDLE_UNAVAILABLE,
                request);
    }

    @ExceptionHandler(AnalyticsRenderException.class)
    public ResponseEntity<AnalyticsRuntimeEnvelope<Void>> renderFailure(
            AnalyticsRenderException failure,
            HttpServletRequest request) {
        return failure(
                HttpStatus.NOT_FOUND,
                "ANALYTICS_" + failure.code().name(),
                "definition",
                renderMessage(failure.code()),
                false,
                request);
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<AnalyticsRuntimeEnvelope<Void>> invalidRequest(
            Exception ignored,
            HttpServletRequest request) {
        return failure(
                HttpStatus.BAD_REQUEST,
                "ANALYTICS_INVALID_REQUEST",
                "request",
                "Analytics request is invalid.",
                false,
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AnalyticsRuntimeEnvelope<Void>> unexpectedFailure(
            Exception ignored,
            HttpServletRequest request) {
        return failure(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "ANALYTICS_INTERNAL_ERROR",
                "runtime",
                "Analytics operation failed.",
                false,
                request);
    }

    private ResponseEntity<AnalyticsRuntimeEnvelope<Void>> failure(
            HttpStatus status,
            String code,
            String phase,
            String message,
            boolean retryable,
            HttpServletRequest request) {
        return ResponseEntity.status(status).body(responses.fail(
                code,
                phase,
                message,
                retryable,
                request.getHeader("X-Request-Id"),
                request.getHeader("X-Trace-Id")));
    }

    private static HttpStatus bundleStatus(AnalyticsBundleStoreException.Code code) {
        return switch (code) {
            case BUNDLE_NOT_REGISTERED -> HttpStatus.NOT_FOUND;
            case REVISION_CONFLICT, DEPENDENCY_STALE -> HttpStatus.CONFLICT;
            case BUNDLE_UNAVAILABLE, RECOVERY_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
            case IMMUTABLE_BUNDLE -> HttpStatus.FORBIDDEN;
            case INVALID_BUNDLE, BUNDLE_IDENTITY_MISMATCH, DIGEST_MISMATCH,
                    UNSAFE_PATH, UNSUPPORTED_RESOURCE_PATH -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }

    private static String bundleMessage(AnalyticsBundleStoreException.Code code) {
        return switch (code) {
            case BUNDLE_NOT_REGISTERED -> "Analytics Bundle is not registered.";
            case BUNDLE_UNAVAILABLE -> "Analytics Bundle is temporarily unavailable.";
            case INVALID_BUNDLE, BUNDLE_IDENTITY_MISMATCH, DIGEST_MISMATCH,
                    UNSAFE_PATH, UNSUPPORTED_RESOURCE_PATH ->
                    "Analytics Bundle validation failed.";
            case DEPENDENCY_STALE -> "Analytics Bundle model dependencies are stale.";
            case IMMUTABLE_BUNDLE -> "Analytics Bundle is immutable.";
            case REVISION_CONFLICT -> "Analytics Bundle revision does not match.";
            case RECOVERY_FAILED -> "Analytics Bundle recovery is required.";
        };
    }

    private static String renderMessage(AnalyticsRenderException.Code code) {
        return switch (code) {
            case REPORT_NOT_FOUND -> "Analytics Report does not exist.";
            case DASHBOARD_NOT_FOUND -> "Analytics Dashboard does not exist.";
            case QUERY_NOT_FOUND -> "Analytics Query definition does not exist.";
            case MODEL_DEPENDENCY_NOT_FOUND ->
                    "Analytics model dependency is unavailable.";
        };
    }
}
