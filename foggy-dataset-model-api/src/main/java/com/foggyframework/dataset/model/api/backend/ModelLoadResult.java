package com.foggyframework.dataset.model.api.backend;

/** Immutable identity of the catalog view that supplied a loaded model. */
public record ModelLoadResult(
        String modelName,
        String namespace,
        String catalogGeneration,
        String sourceRevision,
        boolean datasourceIdentityComplete
) {

    public ModelLoadResult {
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("modelName must not be blank");
        }
        modelName = modelName.trim();
        namespace = namespace == null || namespace.isBlank() ? "" : namespace.trim();
        if (catalogGeneration == null || catalogGeneration.isBlank()) {
            throw new IllegalArgumentException("catalogGeneration must not be blank");
        }
        if (sourceRevision == null || sourceRevision.isBlank()) {
            throw new IllegalArgumentException("sourceRevision must not be blank");
        }
    }
}
