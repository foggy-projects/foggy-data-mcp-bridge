package com.foggyframework.runtime.api.dto;

import java.util.List;

public record ModelRefreshResponse(
        String namespace,
        String scope,
        List<String> clearedCaches,
        List<String> refreshedModels,
        int loadedCount,
        int failedCount,
        List<ModelRefreshFailure> failures,
        List<String> warnings,
        String beforeCatalogGeneration,
        String afterCatalogGeneration,
        String sourceRevision,
        List<DatasourceBindingGenerationSummary> affectedBindingGenerations,
        int refreshedCount,
        int preservedCount,
        Long durationMs,
        RuntimeCatalogState catalogState
) {
    public ModelRefreshResponse {
        clearedCaches = clearedCaches == null ? List.of() : List.copyOf(clearedCaches);
        refreshedModels = refreshedModels == null ? List.of() : List.copyOf(refreshedModels);
        failures = failures == null ? List.of() : List.copyOf(failures);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        affectedBindingGenerations = RuntimeLifecycleSanitizer.sortedBindings(
                affectedBindingGenerations);
        if (loadedCount < 0 || failedCount < 0
                || refreshedCount < 0 || preservedCount < 0) {
            throw new IllegalArgumentException("lifecycle counts must not be negative");
        }
        if (loadedCount != refreshedCount) {
            throw new IllegalArgumentException(
                    "loadedCount must equal refreshedCount during compatibility period");
        }
        if (durationMs != null && durationMs < 0L) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
        if (catalogState == null) {
            throw new IllegalArgumentException("catalogState must not be null");
        }
    }

    /** Compatibility constructor retaining the original Runtime API Java surface. */
    public ModelRefreshResponse(
            String namespace,
            String scope,
            List<String> clearedCaches,
            List<String> refreshedModels,
            int loadedCount,
            int failedCount,
            List<ModelRefreshFailure> failures,
            List<String> warnings
    ) {
        this(namespace, scope, clearedCaches, refreshedModels, loadedCount, failedCount,
                failures, warnings, null, null, null, List.of(), loadedCount, 0,
                null, RuntimeCatalogState.ABSENT);
    }
}
