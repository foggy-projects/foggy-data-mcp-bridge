package com.foggyframework.dataset.model.engine.pivot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PivotOuterCacheDistributedProviderContractTest {

    @Test
    @DisplayName("distributed provider contract defines neutral JSON payload and key layout")
    void testDistributedProviderContractDefinesKeyLayout() {
        PivotOuterCacheDistributedProviderContract contract =
                PivotOuterCacheDistributedProviderContract.defaultJson(60_000L);

        assertEquals("foggy:pivot:outer", contract.keyPrefix());
        assertEquals("v1", contract.payloadVersion());
        assertEquals("application/json", contract.payloadContentType());
        assertEquals(60_000L, contract.ttlMillis());
        assertTrue(contract.storesAbsoluteExpiresAtMillis());
        assertTrue(contract.requiresNamespaceModelIndex());
        assertTrue(contract.requiresCopyIsolation());
        assertEquals("foggy:pivot:outer:response:abc123", contract.responseKey("abc123"));
        assertEquals("foggy:pivot:outer:idx:namespace:default", contract.namespaceIndexKey(""));
        assertEquals("foggy:pivot:outer:idx:namespace:ns-a:model:SalesQM",
                contract.modelIndexKey("ns-a", "SalesQM"));
    }

    @Test
    @DisplayName("distributed provider contract keeps eviction scope semantics aligned with provider")
    void testDistributedProviderContractKeepsEvictionScopeSemantics() {
        PivotOuterCacheDistributedProviderContract contract =
                PivotOuterCacheDistributedProviderContract.json(" test:prefix ", 1_000L);

        assertEquals("test:prefix", contract.keyPrefix());
        assertEquals("all-namespaces/model", contract.evictionScope(null, "SalesQM").scope());
        assertEquals("namespace/all-models", contract.evictionScope(" ", " ").scope());
        assertEquals("", contract.evictionScope(" ", " ").namespace());
        assertEquals("test:prefix:idx:namespace:all", contract.namespaceIndexKey(null));
    }

    @Test
    @DisplayName("distributed provider contract rejects ambiguous TTL and index keys")
    void testDistributedProviderContractRejectsAmbiguousInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> PivotOuterCacheDistributedProviderContract.defaultJson(0L));
        assertThrows(IllegalArgumentException.class,
                () -> PivotOuterCacheDistributedProviderContract.json(" ", 1L));
        assertThrows(IllegalArgumentException.class,
                () -> PivotOuterCacheDistributedProviderContract.defaultJson(1L).responseKey(" "));
        assertThrows(IllegalArgumentException.class,
                () -> PivotOuterCacheDistributedProviderContract.defaultJson(1L).modelIndexKey("ns-a", " "));
    }
}
