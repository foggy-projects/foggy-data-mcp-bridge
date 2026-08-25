package com.foggyframework.analytics.runtime.core.function;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable semantic Function failure before transport sanitization. */
public final class AnalyticsSemanticFunctionException extends RuntimeException {

    private static final Pattern VALIDATION_CODE =
            Pattern.compile("[A-Z][A-Z0-9_]{2,63}");

    public enum Code {
        MODEL_NOT_FOUND,
        MODEL_REVISION_CONFLICT,
        QUERY_INVALID,
        QUERY_FAILED,
        COMPOSE_INVALID,
        COMPOSE_SANDBOX,
        COMPOSE_FAILED,
        RESPONSE_INVALID
    }

    private final Code code;
    private final String validationCode;

    public AnalyticsSemanticFunctionException(Code code, String message) {
        this(code, message, null, null);
    }

    public AnalyticsSemanticFunctionException(Code code, String message, Throwable cause) {
        this(code, message, null, cause);
    }

    public AnalyticsSemanticFunctionException(
            Code code,
            String message,
            String validationCode,
            Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        if (validationCode != null
                && !VALIDATION_CODE.matcher(validationCode).matches()) {
            throw new IllegalArgumentException("validationCode must be a stable message key");
        }
        if (validationCode != null && code != Code.QUERY_INVALID) {
            throw new IllegalArgumentException(
                    "validationCode is only valid for QUERY_INVALID failures");
        }
        this.validationCode = validationCode;
    }

    public Code code() {
        return code;
    }

    /** Stable, value-free validator key safe to expose as repair metadata. */
    public String validationCode() {
        return validationCode;
    }
}
