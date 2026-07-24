package com.foggyframework.dataset.model.api.backend;

import java.util.Objects;
import java.util.Set;

/** Immutable provider identity and capability declaration. */
public record BackendDescriptor(BackendId backendId, Set<BackendCapability> capabilities) {

    public BackendDescriptor {
        Objects.requireNonNull(backendId, "backendId must not be null");
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }

    public boolean supports(BackendCapability capability) {
        return capabilities.contains(Objects.requireNonNull(capability, "capability must not be null"));
    }
}
