package com.foggyframework.dataset.model.api.backend;

/** Stable request for ensuring one query model is loaded in an explicit namespace. */
public record ModelLoadRequest(String modelName, String namespace) {

    public ModelLoadRequest {
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("modelName must not be blank");
        }
        modelName = modelName.trim();
        namespace = namespace == null || namespace.isBlank() ? "" : namespace.trim();
    }
}
