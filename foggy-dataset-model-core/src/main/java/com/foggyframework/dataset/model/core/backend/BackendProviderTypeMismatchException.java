package com.foggyframework.dataset.model.core.backend;

import com.foggyframework.dataset.model.api.backend.BackendId;

import java.util.Objects;

/** Raised when a provider advertises a capability without its required small role. */
public final class BackendProviderTypeMismatchException extends BackendProviderResolutionException {

    private final Class<?> requiredType;

    public BackendProviderTypeMismatchException(BackendId backendId, Class<?> requiredType) {
        super(backendId, "backend " + backendId + " does not implement " + requiredType.getName());
        this.requiredType = Objects.requireNonNull(requiredType, "requiredType must not be null");
    }

    public Class<?> requiredType() {
        return requiredType;
    }
}
