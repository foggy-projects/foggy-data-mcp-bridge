package com.foggyframework.analytics.runtime.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/** Shared exact-revision HTTP request for Report preview and Dashboard execution. */
public record AnalyticsRenderRequest(
        @NotBlank String expectedBundleRevision,
        Map<String, Object> parameters,
        @NotBlank String timezone,
        @NotBlank String locale,
        @Valid @NotNull AnalyticsAuthorityRequest authority,
        @NotBlank String requestId,
        @NotBlank String traceId) {

    public AnalyticsRenderRequest {
        parameters = parameters == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(parameters));
    }
}
