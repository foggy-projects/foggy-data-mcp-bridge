package com.foggyframework.dataset.model.core.backend;

import com.foggyframework.dataset.model.api.backend.BackendCapability;
import com.foggyframework.dataset.model.api.backend.BackendDescriptor;
import com.foggyframework.dataset.model.api.backend.BackendId;
import com.foggyframework.dataset.model.api.backend.BackendProvider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable provider catalog used by adapter-specific factories and runtimes.
 * Duplicate, missing and unsupported routes fail closed at the core boundary.
 */
public final class BackendProviderCatalog {

    private final Map<BackendId, RegisteredProvider> providers;

    private BackendProviderCatalog(Map<BackendId, RegisteredProvider> providers) {
        this.providers = Collections.unmodifiableMap(new LinkedHashMap<>(providers));
    }

    public static BackendProviderCatalog of(Iterable<? extends BackendProvider> discoveredProviders) {
        Objects.requireNonNull(discoveredProviders, "discoveredProviders must not be null");
        Map<BackendId, RegisteredProvider> indexed = new LinkedHashMap<>();
        for (BackendProvider provider : discoveredProviders) {
            Objects.requireNonNull(provider, "provider must not be null");
            BackendDescriptor descriptor = Objects.requireNonNull(
                    provider.descriptor(), "provider descriptor must not be null");
            BackendId backendId = descriptor.backendId();
            if (indexed.putIfAbsent(backendId, new RegisteredProvider(provider, descriptor)) != null) {
                throw new DuplicateBackendProviderException(backendId);
            }
        }
        return new BackendProviderCatalog(indexed);
    }

    public Optional<BackendProvider> find(BackendId backendId) {
        RegisteredProvider registered = providers.get(Objects.requireNonNull(
                backendId, "backendId must not be null"));
        return registered == null ? Optional.empty() : Optional.of(registered.provider());
    }

    public BackendProvider require(BackendId backendId) {
        return find(backendId).orElseThrow(() -> new MissingBackendProviderException(backendId));
    }

    public BackendProvider require(BackendId backendId, BackendCapability capability) {
        Objects.requireNonNull(capability, "capability must not be null");
        RegisteredProvider registered = providers.get(backendId);
        if (registered == null) {
            throw new MissingBackendProviderException(backendId);
        }
        if (!registered.descriptor().supports(capability)) {
            throw new UnsupportedBackendCapabilityException(backendId, capability);
        }
        return registered.provider();
    }

    public List<BackendProvider> providers() {
        return Collections.unmodifiableList(providers.values().stream()
                .map(RegisteredProvider::provider)
                .toList());
    }

    private record RegisteredProvider(BackendProvider provider, BackendDescriptor descriptor) { }
}
