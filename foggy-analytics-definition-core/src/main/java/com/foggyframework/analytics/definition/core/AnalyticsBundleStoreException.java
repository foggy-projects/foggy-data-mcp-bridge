package com.foggyframework.analytics.definition.core;

import java.util.Objects;

/** Stable failure categories for Definition Store adapters and Runtime API error projection. */
public final class AnalyticsBundleStoreException extends RuntimeException {

    public enum Code {
        BUNDLE_NOT_REGISTERED,
        BUNDLE_UNAVAILABLE,
        INVALID_BUNDLE,
        BUNDLE_IDENTITY_MISMATCH,
        DIGEST_MISMATCH,
        DEPENDENCY_STALE,
        IMMUTABLE_BUNDLE,
        REVISION_CONFLICT,
        UNSAFE_PATH,
        UNSUPPORTED_RESOURCE_PATH,
        RECOVERY_FAILED
    }

    private final Code code;

    public AnalyticsBundleStoreException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public AnalyticsBundleStoreException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }
}
