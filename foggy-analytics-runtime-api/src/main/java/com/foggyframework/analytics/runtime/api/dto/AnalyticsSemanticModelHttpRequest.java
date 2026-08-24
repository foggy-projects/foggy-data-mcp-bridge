package com.foggyframework.analytics.runtime.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** HTTP projection of an exact governed semantic-model read. */
public record AnalyticsSemanticModelHttpRequest(
        @NotBlank String namespace,
        @NotBlank String expectedModelRevision,
        @Valid @NotNull AnalyticsAuthorityRequest authority,
        String requestId,
        String traceId) {
}
