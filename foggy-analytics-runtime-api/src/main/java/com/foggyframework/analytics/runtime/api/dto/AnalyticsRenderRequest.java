package com.foggyframework.analytics.runtime.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionJsonValues;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/** Shared exact-revision HTTP request for Report preview and Dashboard execution. */
public record AnalyticsRenderRequest(
        @NotBlank String expectedBundleRevision,
        @JsonDeserialize(using = AnalyticsFunctionParametersDeserializer.class)
        Map<String, Object> parameters,
        @NotBlank String timezone,
        @NotBlank String locale,
        @Valid @NotNull AnalyticsAuthorityRequest authority,
        String requestId,
        String traceId) {

    public AnalyticsRenderRequest {
        parameters = parameters == null
                ? Map.of()
                : AnalyticsFunctionJsonValues.normalizeObject(
                        "parameters", parameters);
    }
}
