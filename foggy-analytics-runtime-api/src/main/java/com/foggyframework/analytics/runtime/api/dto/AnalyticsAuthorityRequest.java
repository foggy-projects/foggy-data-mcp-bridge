package com.foggyframework.analytics.runtime.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Opaque host authority handle; raw ACL/filter payloads are intentionally excluded. */
public record AnalyticsAuthorityRequest(
        @NotBlank String provider,
        @NotBlank String reference) {
}
