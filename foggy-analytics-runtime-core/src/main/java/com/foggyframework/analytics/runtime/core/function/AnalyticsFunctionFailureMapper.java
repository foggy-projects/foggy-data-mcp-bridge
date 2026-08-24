package com.foggyframework.analytics.runtime.core.function;

import com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionError;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionErrorCodes;
import com.foggyframework.analytics.runtime.core.render.AnalyticsRenderException;

/** Sanitizes implementation failures into stable product-neutral error codes. */
public final class AnalyticsFunctionFailureMapper {

    public AnalyticsFunctionError map(Throwable failure) {
        if (failure instanceof AnalyticsSemanticFunctionException semanticFailure) {
            return new AnalyticsFunctionError(
                    semanticErrorCode(semanticFailure.code()),
                    "semantic-query",
                    semanticMessage(semanticFailure.code()),
                    false);
        }
        if (failure instanceof AnalyticsModelDependencyResolutionException dependencyFailure) {
            return new AnalyticsFunctionError(
                    dependencyErrorCode(dependencyFailure.code()),
                    "model-dependency",
                    dependencyMessage(dependencyFailure.code()),
                    dependencyFailure.code()
                            == AnalyticsModelDependencyResolutionException.Code.REVISION_UNAVAILABLE);
        }
        if (failure instanceof AnalyticsBundleStoreException bundleFailure) {
            return new AnalyticsFunctionError(
                    bundleErrorCode(bundleFailure.code()),
                    "bundle",
                    bundleMessage(bundleFailure.code()),
                    bundleFailure.code()
                            == AnalyticsBundleStoreException.Code.BUNDLE_UNAVAILABLE);
        }
        if (failure instanceof AnalyticsRenderException renderFailure) {
                return new AnalyticsFunctionError(
                    renderErrorCode(renderFailure.code()),
                    "definition",
                    renderMessage(renderFailure.code()),
                    false);
        }
        if (failure instanceof IllegalArgumentException) {
            return new AnalyticsFunctionError(
                    AnalyticsFunctionErrorCodes.INVALID_REQUEST,
                    "request",
                    "Analytics request is invalid.",
                    false);
        }
        return new AnalyticsFunctionError(
                AnalyticsFunctionErrorCodes.INTERNAL_ERROR,
                "runtime",
                "Analytics operation failed.",
                false);
    }

    private static String semanticErrorCode(
            AnalyticsSemanticFunctionException.Code code) {
        return switch (code) {
            case MODEL_NOT_FOUND -> AnalyticsFunctionErrorCodes.MODEL_DEPENDENCY_NOT_FOUND;
            case MODEL_REVISION_CONFLICT ->
                    AnalyticsFunctionErrorCodes.MODEL_REVISION_CONFLICT;
            case QUERY_INVALID -> AnalyticsFunctionErrorCodes.SEMANTIC_QUERY_INVALID;
            case QUERY_FAILED, RESPONSE_INVALID ->
                    AnalyticsFunctionErrorCodes.SEMANTIC_QUERY_FAILED;
        };
    }

    private static String semanticMessage(
            AnalyticsSemanticFunctionException.Code code) {
        return switch (code) {
            case MODEL_NOT_FOUND -> "Analytics semantic model does not exist.";
            case MODEL_REVISION_CONFLICT ->
                    "Analytics semantic model revision does not match.";
            case QUERY_INVALID -> "Analytics semantic query is invalid.";
            case QUERY_FAILED -> "Analytics semantic query failed.";
            case RESPONSE_INVALID -> "Analytics semantic query returned an invalid response.";
        };
    }

    private static String dependencyErrorCode(
            AnalyticsModelDependencyResolutionException.Code code) {
        return switch (code) {
            case MODEL_NOT_FOUND -> AnalyticsFunctionErrorCodes.MODEL_DEPENDENCY_NOT_FOUND;
            case REVISION_UNAVAILABLE ->
                    AnalyticsFunctionErrorCodes.MODEL_DEPENDENCY_REVISION_UNAVAILABLE;
        };
    }

    private static String dependencyMessage(
            AnalyticsModelDependencyResolutionException.Code code) {
        return switch (code) {
            case MODEL_NOT_FOUND -> "Analytics model dependency does not exist.";
            case REVISION_UNAVAILABLE ->
                    "Analytics model dependency revision is temporarily unavailable.";
        };
    }

    static String bundleErrorCode(
            AnalyticsBundleStoreException.Code code) {
        return switch (code) {
            case BUNDLE_NOT_REGISTERED ->
                    AnalyticsFunctionErrorCodes.BUNDLE_NOT_REGISTERED;
            case BUNDLE_UNAVAILABLE ->
                    AnalyticsFunctionErrorCodes.BUNDLE_UNAVAILABLE;
            case INVALID_BUNDLE -> AnalyticsFunctionErrorCodes.BUNDLE_INVALID;
            case BUNDLE_IDENTITY_MISMATCH ->
                    AnalyticsFunctionErrorCodes.BUNDLE_IDENTITY_MISMATCH;
            case DIGEST_MISMATCH ->
                    AnalyticsFunctionErrorCodes.BUNDLE_DIGEST_MISMATCH;
            case UNSAFE_PATH -> AnalyticsFunctionErrorCodes.BUNDLE_UNSAFE_PATH;
            case UNSUPPORTED_RESOURCE_PATH ->
                    AnalyticsFunctionErrorCodes.BUNDLE_UNSUPPORTED_RESOURCE_PATH;
            case DEPENDENCY_STALE ->
                    AnalyticsFunctionErrorCodes.BUNDLE_DEPENDENCY_STALE;
            case IMMUTABLE_BUNDLE ->
                    AnalyticsFunctionErrorCodes.BUNDLE_IMMUTABLE;
            case REVISION_CONFLICT ->
                    AnalyticsFunctionErrorCodes.BUNDLE_REVISION_CONFLICT;
            case RECOVERY_FAILED ->
                    AnalyticsFunctionErrorCodes.BUNDLE_RECOVERY_FAILED;
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

    private static String renderErrorCode(AnalyticsRenderException.Code code) {
        return switch (code) {
            case REPORT_NOT_FOUND -> AnalyticsFunctionErrorCodes.REPORT_NOT_FOUND;
            case DASHBOARD_NOT_FOUND ->
                    AnalyticsFunctionErrorCodes.DASHBOARD_NOT_FOUND;
            case QUERY_NOT_FOUND -> AnalyticsFunctionErrorCodes.QUERY_NOT_FOUND;
            case MODEL_DEPENDENCY_NOT_FOUND ->
                    AnalyticsFunctionErrorCodes.MODEL_DEPENDENCY_NOT_FOUND;
        };
    }
}
