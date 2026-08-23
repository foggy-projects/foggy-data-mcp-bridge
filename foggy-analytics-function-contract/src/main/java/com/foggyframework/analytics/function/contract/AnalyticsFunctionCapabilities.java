package com.foggyframework.analytics.function.contract;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Runtime composition and operation registry for Analytics Function v1. */
public record AnalyticsFunctionCapabilities(
        String api,
        String apiVersion,
        String schemaVersion,
        boolean enabled,
        String securityMode,
        Map<String, String> operations,
        Limits limits,
        List<String> warnings) {

    public AnalyticsFunctionCapabilities {
        api = AnalyticsFunctionValues.requireText("api", api);
        apiVersion = AnalyticsFunctionValues.requireText("apiVersion", apiVersion);
        schemaVersion = AnalyticsFunctionValues.requireText(
                "schemaVersion", schemaVersion);
        securityMode = AnalyticsFunctionValues.requireText(
                "securityMode", securityMode);
        operations = Map.copyOf(Objects.requireNonNull(operations, "operations"));
        limits = Objects.requireNonNull(limits, "limits");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }

    public record Limits(int maxRows, int configuredBundles) {

        public Limits {
            if (maxRows <= 0) {
                throw new IllegalArgumentException("maxRows must be positive");
            }
            if (configuredBundles < 0) {
                throw new IllegalArgumentException(
                        "configuredBundles must not be negative");
            }
        }
    }
}
