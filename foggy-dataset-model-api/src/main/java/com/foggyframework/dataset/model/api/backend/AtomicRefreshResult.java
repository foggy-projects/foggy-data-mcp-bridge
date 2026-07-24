package com.foggyframework.dataset.model.api.backend;

import java.util.Set;

/** Stable projection of a successful atomic catalog publication. */
public record AtomicRefreshResult(
        String namespace,
        String beforeGeneration,
        String afterGeneration,
        String sourceRevision,
        Set<String> refreshedModels,
        Set<String> preservedModels,
        long durationMs
) {

    public AtomicRefreshResult {
        namespace = namespace == null || namespace.isBlank() ? "" : namespace.trim();
        if (afterGeneration == null || afterGeneration.isBlank()) {
            throw new IllegalArgumentException("afterGeneration must not be blank");
        }
        if (sourceRevision == null || sourceRevision.isBlank()) {
            throw new IllegalArgumentException("sourceRevision must not be blank");
        }
        refreshedModels = refreshedModels == null ? Set.of() : Set.copyOf(refreshedModels);
        preservedModels = preservedModels == null ? Set.of() : Set.copyOf(preservedModels);
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
    }
}
