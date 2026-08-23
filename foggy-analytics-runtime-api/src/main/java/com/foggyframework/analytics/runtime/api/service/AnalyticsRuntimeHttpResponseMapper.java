package com.foggyframework.analytics.runtime.api.service;

import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionErrorCodes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Objects;

/** Maps a transport-neutral Function outcome to HTTP without changing its body. */
public final class AnalyticsRuntimeHttpResponseMapper {

    public <T> ResponseEntity<AnalyticsFunctionEnvelope<T>> map(
            AnalyticsFunctionEnvelope<T> outcome) {
        AnalyticsFunctionEnvelope<T> value = Objects.requireNonNull(
                outcome, "outcome");
        return ResponseEntity.status(status(value)).body(value);
    }

    private static HttpStatus status(AnalyticsFunctionEnvelope<?> outcome) {
        if (outcome.success()) {
            return HttpStatus.OK;
        }
        return switch (outcome.error().code()) {
            case AnalyticsFunctionErrorCodes.INVALID_REQUEST ->
                    HttpStatus.BAD_REQUEST;
            case AnalyticsFunctionErrorCodes.BUNDLE_NOT_REGISTERED,
                    AnalyticsFunctionErrorCodes.REPORT_NOT_FOUND,
                    AnalyticsFunctionErrorCodes.DASHBOARD_NOT_FOUND,
                    AnalyticsFunctionErrorCodes.QUERY_NOT_FOUND,
                    AnalyticsFunctionErrorCodes.MODEL_DEPENDENCY_NOT_FOUND ->
                    HttpStatus.NOT_FOUND;
            case AnalyticsFunctionErrorCodes.BUNDLE_REVISION_CONFLICT,
                    AnalyticsFunctionErrorCodes.BUNDLE_DEPENDENCY_STALE ->
                    HttpStatus.CONFLICT;
            case AnalyticsFunctionErrorCodes.BUNDLE_IMMUTABLE ->
                    HttpStatus.FORBIDDEN;
            case AnalyticsFunctionErrorCodes.BUNDLE_INVALID,
                    AnalyticsFunctionErrorCodes.BUNDLE_IDENTITY_MISMATCH,
                    AnalyticsFunctionErrorCodes.BUNDLE_DIGEST_MISMATCH,
                    AnalyticsFunctionErrorCodes.BUNDLE_UNSAFE_PATH,
                    AnalyticsFunctionErrorCodes.BUNDLE_UNSUPPORTED_RESOURCE_PATH ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            case AnalyticsFunctionErrorCodes.BUNDLE_UNAVAILABLE,
                    AnalyticsFunctionErrorCodes.BUNDLE_RECOVERY_FAILED,
                    AnalyticsFunctionErrorCodes.RENDER_UNAVAILABLE ->
                    HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
