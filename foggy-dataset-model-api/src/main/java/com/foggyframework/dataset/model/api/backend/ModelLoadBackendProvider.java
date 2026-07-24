package com.foggyframework.dataset.model.api.backend;

/** Provider role required when MODEL_LOAD is advertised. */
public interface ModelLoadBackendProvider extends BackendProvider {

    ModelLoadPort modelLoader();
}
