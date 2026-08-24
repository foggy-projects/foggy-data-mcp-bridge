package com.foggyframework.analytics.runtime.api.controller;

import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionErrorCodes;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeApiResponseFactory;
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
        AnalyticsModelDependenciesController.class,
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

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<AnalyticsFunctionEnvelope<Void>> invalidRequest(
            Exception ignored,
            HttpServletRequest request) {
        return failure(
                HttpStatus.BAD_REQUEST,
                AnalyticsFunctionErrorCodes.INVALID_REQUEST,
                "request",
                "Analytics request is invalid.",
                false,
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AnalyticsFunctionEnvelope<Void>> unexpectedFailure(
            Exception ignored,
            HttpServletRequest request) {
        return failure(
                HttpStatus.INTERNAL_SERVER_ERROR,
                AnalyticsFunctionErrorCodes.INTERNAL_ERROR,
                "runtime",
                "Analytics operation failed.",
                false,
                request);
    }

    private ResponseEntity<AnalyticsFunctionEnvelope<Void>> failure(
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

}
