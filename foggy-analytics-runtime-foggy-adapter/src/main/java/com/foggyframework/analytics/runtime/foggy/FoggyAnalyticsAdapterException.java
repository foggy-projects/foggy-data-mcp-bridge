package com.foggyframework.analytics.runtime.foggy;

import java.util.Objects;

/** Stable, sanitizable failures raised at the Foggy Analytics adapter boundary. */
public final class FoggyAnalyticsAdapterException extends RuntimeException {

    public enum Code {
        UNSUPPORTED_MODEL_KIND,
        UNTRACKED_CATALOG,
        MODEL_NOT_FOUND,
        MODEL_NAME_NOT_CANONICAL,
        MODEL_DIGEST_UNAVAILABLE,
        MODEL_DIGEST_MISMATCH,
        AUTHORITY_RESOLUTION_FAILED,
        AUTHORITY_CONTEXT_MISSING,
        AUTHORITY_NAMESPACE_MISMATCH,
        AUTHORITY_CATALOG_CONFLICT,
        AUTHORITY_MISMATCH,
        QUERY_PARAMETERS_UNSUPPORTED,
        QUERY_RESPONSE_MISSING,
        QUERY_NOT_EXECUTED,
        QUERY_SCHEMA_INVALID
    }

    private final Code code;

    public FoggyAnalyticsAdapterException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public FoggyAnalyticsAdapterException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }
}
