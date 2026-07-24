package com.foggyframework.dataset.model.tck;

import com.foggyframework.dataset.model.api.backend.BackendCapability;
import com.foggyframework.dataset.model.api.backend.BackendDescriptor;
import com.foggyframework.dataset.model.api.backend.BackendId;
import com.foggyframework.dataset.model.api.backend.BackendProvider;
import com.foggyframework.dataset.model.api.backend.AtomicRefreshBackendProvider;
import com.foggyframework.dataset.model.api.backend.CacheInvalidationBackendProvider;
import com.foggyframework.dataset.model.api.backend.ModelLoadBackendProvider;
import com.foggyframework.dataset.model.api.backend.QueryBackendProvider;
import com.foggyframework.dataset.model.core.backend.BackendProviderCatalog;
import com.foggyframework.dataset.model.core.backend.DuplicateBackendProviderException;
import com.foggyframework.dataset.model.core.backend.MissingBackendProviderException;
import com.foggyframework.dataset.model.core.backend.UnsupportedBackendCapabilityException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reusable SPI v2 provider contract.
 *
 * <p>Addon tests extend this class with a real compatibility adapter. The TCK
 * intentionally checks only the migrated identity/capability/role surface.</p>
 */
public abstract class BackendProviderTck<P extends BackendProvider> {

    protected abstract P createProvider();

    protected abstract BackendId expectedBackendId();

    protected abstract Set<BackendCapability> expectedCapabilities();

    protected abstract Class<P> expectedProviderRole();

    /** Optional provider-specific assertion for the operational port. */
    protected void verifyOperationalPort(P provider) {
        // Default is intentionally empty for compatibility adapters whose
        // operation already has a dedicated focused test.
    }

    @Test
    public final void descriptorMatchesTheDeclaredMigrationSurface() {
        P provider = createProvider();
        BackendDescriptor descriptor = provider.descriptor();
        assertEquals(expectedBackendId(), descriptor.backendId());
        assertEquals(expectedCapabilities(), descriptor.capabilities());
        assertEquals(descriptor, provider.descriptor(),
                "provider descriptor must be deterministic across reads");
        assertThrows(UnsupportedOperationException.class,
                () -> descriptor.capabilities().clear());
    }

    @Test
    public final void catalogResolvesEveryAdvertisedCapabilityThroughTheExpectedRole() {
        P provider = createProvider();
        BackendProviderCatalog catalog = BackendProviderCatalog.of(List.of(provider));

        assertSame(provider, catalog.require(expectedBackendId()));
        assertTrue(expectedProviderRole().isInstance(provider),
                "provider must implement its declared primary TCK role");
        for (BackendCapability capability : expectedCapabilities()) {
            assertSame(provider, catalog.require(
                    expectedBackendId(), capability, requiredRole(capability)));
        }
        verifyOperationalPort(provider);
    }

    @Test
    public final void duplicateMissingAndUnsupportedRoutesFailClosed() {
        P provider = createProvider();
        assertThrows(DuplicateBackendProviderException.class,
                () -> BackendProviderCatalog.of(List.of(provider, createProvider())));

        BackendProviderCatalog catalog = BackendProviderCatalog.of(List.of(provider));
        assertThrows(MissingBackendProviderException.class,
                () -> catalog.require(BackendId.of("missing-" + expectedBackendId().value())));

        BackendCapability unsupported = Set.of(BackendCapability.values()).stream()
                .filter(capability -> !expectedCapabilities().contains(capability))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "TCK provider must leave at least one capability unsupported"));
        UnsupportedBackendCapabilityException error = assertThrows(
                UnsupportedBackendCapabilityException.class,
                () -> catalog.require(expectedBackendId(), unsupported));
        assertEquals(expectedBackendId(), error.backendId());
        assertEquals(unsupported, error.capability());
        assertTrue(catalog.find(expectedBackendId()).isPresent());
    }

    private static Class<? extends BackendProvider> requiredRole(BackendCapability capability) {
        return switch (capability) {
            case QUERY -> QueryBackendProvider.class;
            case MODEL_LOAD -> ModelLoadBackendProvider.class;
            case ATOMIC_REFRESH -> AtomicRefreshBackendProvider.class;
            case CACHE_INVALIDATION -> CacheInvalidationBackendProvider.class;
        };
    }
}
