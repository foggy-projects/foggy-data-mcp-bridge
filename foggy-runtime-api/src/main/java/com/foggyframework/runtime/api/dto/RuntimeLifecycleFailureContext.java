package com.foggyframework.runtime.api.dto;

import java.util.List;

/** Typed lifecycle context carried by RuntimeError for a failed lifecycle attempt. */
public record RuntimeLifecycleFailureContext(
        String namespace,
        String beforeCatalogGeneration,
        String afterCatalogGeneration,
        String sourceRevision,
        RuntimeCatalogState catalogState,
        List<DatasourceBindingGenerationSummary> affectedBindingGenerations,
        List<String> failedTargets,
        List<RuntimeLifecycleFailureDiagnostic> diagnostics
) {
    public RuntimeLifecycleFailureContext {
        namespace = namespace == null ? "" : namespace.trim();
        if (afterCatalogGeneration != null) {
            throw new IllegalArgumentException(
                    "failed lifecycle attempt must not expose an after generation");
        }
        if (catalogState == null) {
            throw new IllegalArgumentException("catalogState must not be null");
        }
        affectedBindingGenerations = RuntimeLifecycleSanitizer.sortedBindings(
                affectedBindingGenerations);
        failedTargets = RuntimeLifecycleSanitizer.failedTargets(failedTargets);
        diagnostics = RuntimeLifecycleSanitizer.diagnostics(diagnostics);
    }
}
