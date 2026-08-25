package com.foggyframework.analytics.function.contract;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Full MCP-compatible, authority-bound query-model DSL invocation. */
public record AnalyticsQueryModelFunctionRequest(
        String namespace,
        String modelName,
        String mode,
        Map<String, Object> payload,
        AnalyticsFunctionAuthority authority,
        AnalyticsFunctionRequestContext context) {

    public AnalyticsQueryModelFunctionRequest {
        namespace = AnalyticsFunctionValues.requireText("namespace", namespace);
        modelName = AnalyticsFunctionValues.requireText("modelName", modelName);
        mode = normalizeMode(mode);
        payload = AnalyticsFunctionJsonValues.normalizeObject(
                "payload", Objects.requireNonNull(payload, "payload"));
        authority = Objects.requireNonNull(authority, "authority");
        context = Objects.requireNonNull(context, "context");
    }

    private static String normalizeMode(String value) {
        String normalized = AnalyticsFunctionValues.requireText("mode", value)
                .toLowerCase(Locale.ROOT);
        if (!"validate".equals(normalized) && !"execute".equals(normalized)) {
            throw new IllegalArgumentException("mode must be validate or execute");
        }
        return normalized;
    }
}
