package com.foggyframework.dataset.model.core.backend;

import com.foggyframework.dataset.model.api.backend.BackendCapability;
import com.foggyframework.dataset.model.api.backend.BackendId;

import java.util.Objects;

/** Raised instead of silently routing an unsupported backend capability. */
public final class UnsupportedBackendCapabilityException extends BackendProviderResolutionException {

    private final BackendCapability capability;

    public UnsupportedBackendCapabilityException(BackendId backendId, BackendCapability capability) {
        super(backendId, "backend " + backendId + " does not support " + capability);
        this.capability = Objects.requireNonNull(capability, "capability must not be null");
    }

    public BackendCapability capability() {
        return capability;
    }
}
