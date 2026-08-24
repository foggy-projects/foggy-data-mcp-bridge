package com.foggyframework.analytics.console.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/** Exact subset of the FAP Provider callback required by the Analytics adapter. */
public record FapAnalyticsCallbackRequest(
        @NotBlank String type,
        @Valid @NotNull Meta meta,
        @NotBlank String providerRef,
        @NotBlank String tenantRef,
        @NotBlank String providerSubjectRef,
        @NotBlank String externalSubjectRef,
        @NotBlank String askInvocationRef,
        @NotBlank String askRequestId,
        @NotBlank String externalConversationRef,
        @Valid @NotNull Binding binding,
        @NotBlank String functionInvocationId,
        @NotBlank String capabilityId,
        long capabilityRevision,
        @NotBlank String functionRef,
        @NotNull Map<String, Object> arguments,
        @NotBlank String requestDigest) {

    public record Meta(@NotBlank String contractVersion, @NotBlank String requestId) {
    }

    public record Binding(
            @NotBlank String workerIdentityRef,
            @NotBlank String runtimeExecutionId,
            @NotBlank String runtimeTaskId) {
    }
}
