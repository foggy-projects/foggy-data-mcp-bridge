package com.foggyframework.analytics.function.contract;

/** Stable Function v1 error-code registry shared by Core and transports. */
public final class AnalyticsFunctionErrorCodes {

    public static final String INVALID_REQUEST = "ANALYTICS_INVALID_REQUEST";
    public static final String INTERNAL_ERROR = "ANALYTICS_INTERNAL_ERROR";
    public static final String RENDER_UNAVAILABLE = "ANALYTICS_RENDER_UNAVAILABLE";

    public static final String BUNDLE_NOT_REGISTERED =
            "ANALYTICS_BUNDLE_NOT_REGISTERED";
    public static final String BUNDLE_UNAVAILABLE = "ANALYTICS_BUNDLE_UNAVAILABLE";
    public static final String BUNDLE_INVALID = "ANALYTICS_BUNDLE_INVALID_BUNDLE";
    public static final String BUNDLE_IDENTITY_MISMATCH =
            "ANALYTICS_BUNDLE_IDENTITY_MISMATCH";
    public static final String BUNDLE_DIGEST_MISMATCH =
            "ANALYTICS_BUNDLE_DIGEST_MISMATCH";
    public static final String BUNDLE_UNSAFE_PATH = "ANALYTICS_BUNDLE_UNSAFE_PATH";
    public static final String BUNDLE_UNSUPPORTED_RESOURCE_PATH =
            "ANALYTICS_BUNDLE_UNSUPPORTED_RESOURCE_PATH";
    public static final String BUNDLE_DEPENDENCY_STALE =
            "ANALYTICS_BUNDLE_DEPENDENCY_STALE";
    public static final String BUNDLE_IMMUTABLE =
            "ANALYTICS_BUNDLE_IMMUTABLE_BUNDLE";
    public static final String BUNDLE_REVISION_CONFLICT =
            "ANALYTICS_BUNDLE_REVISION_CONFLICT";
    public static final String BUNDLE_RECOVERY_FAILED =
            "ANALYTICS_BUNDLE_RECOVERY_FAILED";

    public static final String REPORT_NOT_FOUND = "ANALYTICS_REPORT_NOT_FOUND";
    public static final String DASHBOARD_NOT_FOUND =
            "ANALYTICS_DASHBOARD_NOT_FOUND";
    public static final String QUERY_NOT_FOUND = "ANALYTICS_QUERY_NOT_FOUND";
    public static final String MODEL_DEPENDENCY_NOT_FOUND =
            "ANALYTICS_MODEL_DEPENDENCY_NOT_FOUND";

    public static final String CLIENT_TRANSPORT_ERROR =
            "ANALYTICS_CLIENT_TRANSPORT_ERROR";
    public static final String CLIENT_PROTOCOL_ERROR =
            "ANALYTICS_CLIENT_PROTOCOL_ERROR";

    private AnalyticsFunctionErrorCodes() {
    }
}
