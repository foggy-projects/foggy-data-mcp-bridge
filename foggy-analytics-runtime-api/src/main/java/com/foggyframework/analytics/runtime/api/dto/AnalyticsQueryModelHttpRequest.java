package com.foggyframework.analytics.runtime.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/** HTTP projection of the full query-model DSL Function request. */
public record AnalyticsQueryModelHttpRequest(
        @NotBlank String namespace,
        @NotBlank String expectedModelRevision,
        @NotBlank String mode,
        @NotNull Map<String, Object> payload,
        @Valid @NotNull AnalyticsAuthorityRequest authority,
        String requestId,
        String traceId) {
}
