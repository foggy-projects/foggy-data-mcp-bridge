package com.foggyframework.dataset.model.core.backend;

import com.foggyframework.dataset.model.api.backend.BackendCapability;
import com.foggyframework.dataset.model.api.backend.BackendDescriptor;
import com.foggyframework.dataset.model.api.backend.BackendId;
import com.foggyframework.dataset.model.api.backend.BackendProvider;
import com.foggyframework.dataset.model.api.backend.AtomicRefreshBackendProvider;
import com.foggyframework.dataset.model.api.backend.CacheInvalidationBackendProvider;
import com.foggyframework.dataset.model.api.backend.ModelLoadBackendProvider;
import com.foggyframework.dataset.model.api.backend.QueryBackendProvider;

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
            validateMigratedCapabilityRoles(provider, descriptor);
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

    public <P extends BackendProvider> P require(
            BackendId backendId, BackendCapability capability, Class<P> providerType) {
        Objects.requireNonNull(providerType, "providerType must not be null");
        BackendProvider provider = require(backendId, capability);
        if (!providerType.isInstance(provider)) {
            throw new BackendProviderTypeMismatchException(backendId, providerType);
        }
        return providerType.cast(provider);
    }

    public List<BackendProvider> providers() {
        return Collections.unmodifiableList(providers.values().stream()
                .map(RegisteredProvider::provider)
                .toList());
    }

    /** Immutable discovery-time descriptors for diagnostics and adapter views. */
    public List<BackendDescriptor> descriptors() {
        return Collections.unmodifiableList(providers.values().stream()
                .map(RegisteredProvider::descriptor)
                .toList());
    }

    private static void validateMigratedCapabilityRoles(
            BackendProvider provider,
            BackendDescriptor descriptor
    ) {
        if (descriptor.supports(BackendCapability.QUERY)
                && !(provider instanceof QueryBackendProvider)) {
            throw new BackendProviderTypeMismatchException(
                    descriptor.backendId(), QueryBackendProvider.class);
        }
        if (descriptor.supports(BackendCapability.MODEL_LOAD)
                && !(provider instanceof ModelLoadBackendProvider)) {
            throw new BackendProviderTypeMismatchException(
                    descriptor.backendId(), ModelLoadBackendProvider.class);
        }
        if (descriptor.supports(BackendCapability.ATOMIC_REFRESH)
                && !(provider instanceof AtomicRefreshBackendProvider)) {
            throw new BackendProviderTypeMismatchException(
                    descriptor.backendId(), AtomicRefreshBackendProvider.class);
        }
        if (descriptor.supports(BackendCapability.CACHE_INVALIDATION)
                && !(provider instanceof CacheInvalidationBackendProvider)) {
            throw new BackendProviderTypeMismatchException(
                    descriptor.backendId(), CacheInvalidationBackendProvider.class);
        }
    }

    private record RegisteredProvider(BackendProvider provider, BackendDescriptor descriptor) { }
}
