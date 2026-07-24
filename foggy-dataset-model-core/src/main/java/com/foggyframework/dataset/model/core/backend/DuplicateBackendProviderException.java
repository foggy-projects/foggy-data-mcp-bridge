package com.foggyframework.dataset.model.core.backend;

import com.foggyframework.dataset.model.api.backend.BackendId;

/** Raised when discovery contains more than one provider for a backend id. */
public final class DuplicateBackendProviderException extends BackendProviderResolutionException {

    public DuplicateBackendProviderException(BackendId backendId) {
        super(backendId, "duplicate backend provider: " + backendId);
    }
}
