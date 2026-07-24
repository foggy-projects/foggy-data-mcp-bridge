package com.foggyframework.dataset.model.core.backend;

import com.foggyframework.dataset.model.api.backend.BackendCapability;
import com.foggyframework.dataset.model.api.backend.BackendDescriptor;
import com.foggyframework.dataset.model.api.backend.BackendId;
import com.foggyframework.dataset.model.api.backend.BackendProvider;
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
        BackendProvider provider = provider(MYSQL, BackendCapability.QUERY);
        BackendProviderCatalog catalog = BackendProviderCatalog.of(List.of(provider));

        assertSame(provider, catalog.require(MYSQL));
        assertSame(provider, catalog.require(MYSQL, BackendCapability.QUERY));
        assertEquals(List.of(provider), catalog.providers());
        assertThrows(UnsupportedOperationException.class, () -> catalog.providers().clear());
    }

    @Test
    void duplicateMissingAndUnsupportedRoutesFailClosed() {
        BackendProvider provider = provider(MYSQL, BackendCapability.QUERY);

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
        BackendProvider provider = current::get;

        BackendProviderCatalog catalog = BackendProviderCatalog.of(List.of(provider));
        current.set(new BackendDescriptor(MYSQL, Set.of()));

        assertSame(provider, catalog.require(MYSQL, BackendCapability.QUERY));
    }

    private BackendProvider provider(BackendId backendId, BackendCapability... capabilities) {
        BackendDescriptor descriptor = new BackendDescriptor(backendId, Set.of(capabilities));
        return () -> descriptor;
    }
}
