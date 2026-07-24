package com.foggyframework.dataset.model.api.backend;

/** Small stable port for namespace-isolated model loading. */
@FunctionalInterface
public interface ModelLoadPort {

    ModelLoadResult load(ModelLoadRequest request);
}
