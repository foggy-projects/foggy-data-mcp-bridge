package com.foggyframework.analytics.runtime.core.function;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Stable semantic Function failure before transport sanitization. */
public final class AnalyticsSemanticFunctionException extends RuntimeException {

    private static final Pattern VALIDATION_CODE =
            Pattern.compile("[A-Z][A-Z0-9_]{2,63}");

    public enum Code {
        MODEL_NOT_FOUND,
        QUERY_INVALID,
        QUERY_FAILED,
        COMPOSE_INVALID,
        COMPOSE_SANDBOX,
        COMPOSE_FAILED,
        RESPONSE_INVALID
    }

    private final Code code;
    private final String validationCode;
    private final List<Map<String, Object>> violations;

    public AnalyticsSemanticFunctionException(Code code, String message) {
        this(code, message, null, List.of(), null);
    }

    public AnalyticsSemanticFunctionException(Code code, String message, Throwable cause) {
        this(code, message, null, List.of(), cause);
    }

    public AnalyticsSemanticFunctionException(
            Code code,
            String message,
            String validationCode,
            Throwable cause) {
        this(code, message, validationCode, List.of(), cause);
    }

    public AnalyticsSemanticFunctionException(
            Code code,
            String message,
            String validationCode,
            List<Map<String, Object>> violations,
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
        if (violations != null && !violations.isEmpty() && code != Code.QUERY_INVALID) {
            throw new IllegalArgumentException(
                    "violations are only valid for QUERY_INVALID failures");
        }
        this.validationCode = validationCode;
        this.violations = violations == null
                ? List.of()
                : violations.stream()
                        .map(value -> Collections.unmodifiableMap(new LinkedHashMap<>(value)))
                        .toList();
    }

    public Code code() {
        return code;
    }

    /** Stable, value-free validator key safe to expose as repair metadata. */
    public String validationCode() {
        return validationCode;
    }

    /** Stable, value-free Query DSL violations safe to expose to Function transports. */
    public List<Map<String, Object>> violations() {
        return violations;
    }
}
