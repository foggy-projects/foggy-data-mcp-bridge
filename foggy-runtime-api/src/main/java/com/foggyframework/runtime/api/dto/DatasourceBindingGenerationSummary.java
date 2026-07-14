package com.foggyframework.runtime.api.dto;

/** Sanitized, opaque datasource binding identity exposed by lifecycle responses. */
public record DatasourceBindingGenerationSummary(
        String bindingKey,
        String backendId,
        String generation
) {
    public DatasourceBindingGenerationSummary {
        bindingKey = RuntimeLifecycleSanitizer.sanitizeOpaqueIdentity(
                bindingKey, "bindingKey");
        backendId = RuntimeLifecycleSanitizer.sanitizeOpaqueIdentity(
                backendId, "backendId");
        generation = RuntimeLifecycleSanitizer.sanitizeOpaqueIdentity(
                generation, "generation");
    }
}
