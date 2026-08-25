package com.foggyframework.analytics.runtime.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/** HTTP projection of restricted SemanticDSL Compose/CTE. */
public record AnalyticsComposeHttpRequest(
        @NotBlank String namespace,
        @NotBlank String mode,
        @NotBlank String script,
        Map<String, Object> params,
        @Valid @NotNull AnalyticsAuthorityRequest authority,
        String requestId,
        String traceId) {
}
