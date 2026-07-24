package com.foggyframework.dataset.model.core.backend;

import com.foggyframework.dataset.model.api.backend.BackendCapability;
import com.foggyframework.dataset.model.api.backend.BackendDescriptor;
import com.foggyframework.dataset.model.api.backend.BackendId;
import com.foggyframework.dataset.model.api.backend.BackendProvider;
import com.foggyframework.dataset.model.api.backend.CacheInvalidationBackendProvider;
import com.foggyframework.dataset.model.api.backend.QueryBackendProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BackendProviderCatalogTest {

    private static final BackendId MYSQL = BackendId.of("mysql");

    @Test
    void resolvesExactlyOneProviderByIdentityAndCapability() {
        BackendProvider provider = queryProvider(MYSQL);
        BackendProviderCatalog catalog = BackendProviderCatalog.of(List.of(provider));

        assertSame(provider, catalog.require(MYSQL));
        assertSame(provider, catalog.require(MYSQL, BackendCapability.QUERY));
        assertEquals(List.of(provider), catalog.providers());
        assertThrows(UnsupportedOperationException.class, () -> catalog.providers().clear());
    }

    @Test
    void duplicateMissingAndUnsupportedRoutesFailClosed() {
        BackendProvider provider = queryProvider(MYSQL);

        DuplicateBackendProviderException duplicate = assertThrows(
                DuplicateBackendProviderException.class,
                () -> BackendProviderCatalog.of(List.of(provider, provider)));
        assertEquals(MYSQL, duplicate.backendId());

        BackendProviderCatalog catalog = BackendProviderCatalog.of(List.of(provider));
        BackendId mongo = BackendId.of("mongo");
        assertFalse(catalog.find(mongo).isPresent());
        assertEquals(mongo, assertThrows(MissingBackendProviderException.class,
                () -> catalog.require(mongo)).backendId());

        UnsupportedBackendCapabilityException unsupported = assertThrows(
                UnsupportedBackendCapabilityException.class,
                () -> catalog.require(MYSQL, BackendCapability.MODEL_LOAD));
        assertEquals(MYSQL, unsupported.backendId());
        assertEquals(BackendCapability.MODEL_LOAD, unsupported.capability());
    }

    @Test
    void invalidDiscoveryEntriesAreRejected() {
        assertThrows(NullPointerException.class, () -> BackendProviderCatalog.of(null));
        assertThrows(NullPointerException.class,
                () -> BackendProviderCatalog.of(java.util.Collections.singletonList(null)));
        assertThrows(NullPointerException.class,
                () -> BackendProviderCatalog.of(List.of(() -> null)));
    }

    @Test
    void discoveryDescriptorIsSnapshottedForDeterministicRouting() {
        BackendDescriptor queryDescriptor = new BackendDescriptor(
                MYSQL, Set.of(BackendCapability.QUERY));
        AtomicReference<BackendDescriptor> current = new AtomicReference<>(queryDescriptor);
        QueryBackendProvider provider = new QueryBackendProvider() {
            @Override
            public BackendDescriptor descriptor() {
                return current.get();
            }

            @Override
            public com.foggyframework.dataset.model.api.QueryFacade queryFacade() {
                return request -> null;
            }
        };

        BackendProviderCatalog catalog = BackendProviderCatalog.of(List.of(provider));
        current.set(new BackendDescriptor(MYSQL, Set.of()));

        assertSame(provider, catalog.require(MYSQL, BackendCapability.QUERY));
        assertEquals(List.of(queryDescriptor), catalog.descriptors());
        assertThrows(UnsupportedOperationException.class, () -> catalog.descriptors().clear());
    }

    @Test
    void typedResolutionRejectsCapabilityOnlyImpostors() {
        BackendProvider provider = provider(MYSQL, BackendCapability.MODEL_LOAD);
        BackendProviderCatalog catalog = BackendProviderCatalog.of(List.of(provider));

        BackendProviderTypeMismatchException mismatch = assertThrows(
                BackendProviderTypeMismatchException.class,
                () -> catalog.require(MYSQL, BackendCapability.MODEL_LOAD, QueryBackendProvider.class));
        assertEquals(MYSQL, mismatch.backendId());
        assertEquals(QueryBackendProvider.class, mismatch.requiredType());
    }

    @Test
    void discoveryRejectsAdvertisedMigratedCapabilitiesWithoutTheirPorts() {
        BackendProviderTypeMismatchException queryMismatch = assertThrows(
                BackendProviderTypeMismatchException.class,
                () -> BackendProviderCatalog.of(List.of(
                        provider(MYSQL, BackendCapability.QUERY))));
        assertEquals(QueryBackendProvider.class, queryMismatch.requiredType());

        BackendProviderTypeMismatchException cacheMismatch = assertThrows(
                BackendProviderTypeMismatchException.class,
                () -> BackendProviderCatalog.of(List.of(
                        provider(MYSQL, BackendCapability.CACHE_INVALIDATION))));
        assertEquals(CacheInvalidationBackendProvider.class, cacheMismatch.requiredType());
    }

    private QueryBackendProvider queryProvider(BackendId backendId) {
        BackendDescriptor descriptor = new BackendDescriptor(
                backendId, Set.of(BackendCapability.QUERY));
        return new QueryBackendProvider() {
            @Override
            public BackendDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public com.foggyframework.dataset.model.api.QueryFacade queryFacade() {
                return request -> null;
            }
        };
    }

    private BackendProvider provider(BackendId backendId, BackendCapability... capabilities) {
        BackendDescriptor descriptor = new BackendDescriptor(backendId, Set.of(capabilities));
        return () -> descriptor;
    }
}
