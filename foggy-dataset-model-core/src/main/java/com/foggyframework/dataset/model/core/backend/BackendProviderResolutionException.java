package com.foggyframework.dataset.model.core.backend;

import com.foggyframework.dataset.model.api.backend.BackendId;

import java.util.Objects;

/** Base error for deterministic backend provider resolution failures. */
public abstract class BackendProviderResolutionException extends IllegalStateException {

    private final BackendId backendId;

    protected BackendProviderResolutionException(BackendId backendId, String message) {
        super(message);
        this.backendId = Objects.requireNonNull(backendId, "backendId must not be null");
    }

    public BackendId backendId() {
        return backendId;
    }
}
