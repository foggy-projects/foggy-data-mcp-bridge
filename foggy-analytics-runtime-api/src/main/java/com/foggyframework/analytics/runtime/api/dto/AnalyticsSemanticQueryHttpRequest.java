package com.foggyframework.analytics.runtime.api.dto;

import com.foggyframework.analytics.function.contract.AnalyticsSemanticQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** HTTP projection of the strict direct-question semantic query subset. */
public record AnalyticsSemanticQueryHttpRequest(
        @NotBlank String namespace,
        @NotBlank String expectedModelRevision,
        @NotNull AnalyticsSemanticQuery query,
        @Valid @NotNull AnalyticsAuthorityRequest authority,
        String requestId,
        String traceId) {
}
