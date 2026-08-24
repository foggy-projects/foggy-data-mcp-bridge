package com.foggyframework.analytics.runtime.core.function;

import java.util.Objects;

/** Stable semantic Function failure before transport sanitization. */
public final class AnalyticsSemanticFunctionException extends RuntimeException {

    public enum Code {
        MODEL_NOT_FOUND,
        MODEL_REVISION_CONFLICT,
        QUERY_INVALID,
        QUERY_FAILED,
        RESPONSE_INVALID
    }

    private final Code code;

    public AnalyticsSemanticFunctionException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public AnalyticsSemanticFunctionException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }
}
