package com.foggyframework.dataset.model.core.backend;

import com.foggyframework.dataset.model.api.backend.BackendId;

/** Raised when no provider is registered for a requested backend id. */
public final class MissingBackendProviderException extends BackendProviderResolutionException {

    public MissingBackendProviderException(BackendId backendId) {
        super(backendId, "missing backend provider: " + backendId);
    }
}
