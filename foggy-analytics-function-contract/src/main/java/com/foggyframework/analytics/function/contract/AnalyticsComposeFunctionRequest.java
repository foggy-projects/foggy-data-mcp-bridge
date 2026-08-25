package com.foggyframework.analytics.function.contract;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Restricted SemanticDSL Compose/CTE invocation against one governed namespace. */
public record AnalyticsComposeFunctionRequest(
        String namespace,
        String mode,
        String script,
        Map<String, Object> params,
        AnalyticsFunctionAuthority authority,
        AnalyticsFunctionRequestContext context) {

    private static final int MAX_SCRIPT_LENGTH = 262_144;

    public AnalyticsComposeFunctionRequest {
        namespace = AnalyticsFunctionValues.requireText("namespace", namespace);
        mode = normalizeMode(mode);
        script = AnalyticsFunctionValues.requireText("script", script);
        if (script.length() > MAX_SCRIPT_LENGTH) {
            throw new IllegalArgumentException("script exceeds the maximum length");
        }
        params = AnalyticsFunctionJsonValues.normalizeObject(
                "params", params == null ? Map.of() : params);
        authority = Objects.requireNonNull(authority, "authority");
        context = Objects.requireNonNull(context, "context");
    }

    private static String normalizeMode(String value) {
        String normalized = AnalyticsFunctionValues.requireText("mode", value)
                .toLowerCase(Locale.ROOT);
        if (!"validate".equals(normalized)
                && !"preview".equals(normalized)
                && !"execute".equals(normalized)) {
            throw new IllegalArgumentException(
                    "mode must be validate, preview or execute");
        }
        return normalized;
    }
}
