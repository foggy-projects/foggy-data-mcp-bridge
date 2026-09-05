package com.foggyframework.analytics.function.contract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable sanitized error shared by embedded and HTTP transports. */
public record AnalyticsFunctionError(
        String code,
        String phase,
        String message,
        boolean retryable,
        List<Map<String, Object>> violations) {

    public AnalyticsFunctionError {
        code = AnalyticsFunctionValues.requireText("code", code);
        phase = AnalyticsFunctionValues.requireText("phase", phase);
        message = AnalyticsFunctionValues.requireText("message", message);
        violations = violations == null || violations.isEmpty()
                ? null
                : violations.stream()
                        .map(value -> Collections.unmodifiableMap(new LinkedHashMap<>(value)))
                        .toList();
    }

    public AnalyticsFunctionError(
            String code,
            String phase,
            String message,
            boolean retryable) {
        this(code, phase, message, retryable, null);
    }
}
