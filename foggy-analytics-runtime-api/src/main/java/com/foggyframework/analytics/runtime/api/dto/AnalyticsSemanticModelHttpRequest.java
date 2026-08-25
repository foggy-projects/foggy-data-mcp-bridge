package com.foggyframework.analytics.runtime.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** HTTP projection of a governed current semantic-model read. */
public record AnalyticsSemanticModelHttpRequest(
        @NotBlank String namespace,
        @Valid @NotNull AnalyticsAuthorityRequest authority,
        String requestId,
        String traceId) {
}
