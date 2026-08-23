package com.foggyframework.analytics.runtime.api.dto;

import java.util.List;
import java.util.Map;

/** Independent Analytics operation registry and host composition state. */
public record AnalyticsCapabilitiesResponse(
        String api,
        String apiVersion,
        String schemaVersion,
        boolean enabled,
        String securityMode,
        Map<String, String> operations,
        Limits limits,
        List<String> warnings) {

    public AnalyticsCapabilitiesResponse {
        operations = Map.copyOf(operations);
        warnings = List.copyOf(warnings);
    }

    public record Limits(int maxRows, int configuredBundles) {
    }
}
