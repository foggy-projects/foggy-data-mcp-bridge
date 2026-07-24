package com.foggyframework.dataset.model.api;

import com.foggyframework.dataset.model.api.backend.BackendCapability;
import com.foggyframework.dataset.model.api.backend.BackendDescriptor;
import com.foggyframework.dataset.model.api.backend.BackendId;
import com.foggyframework.dataset.model.api.backend.BackendProvider;
import com.foggyframework.dataset.model.api.backend.AtomicRefreshBackendProvider;
import com.foggyframework.dataset.model.api.backend.AtomicRefreshPort;
import com.foggyframework.dataset.model.api.backend.CacheInvalidationBackendProvider;
import com.foggyframework.dataset.model.api.backend.CacheInvalidationPort;
import com.foggyframework.dataset.model.api.backend.ModelLoadBackendProvider;
import com.foggyframework.dataset.model.api.backend.ModelLoadPort;
import com.foggyframework.dataset.model.api.backend.QueryBackendProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelApiDependencyBoundaryTest {

    @Test
    void providerContractRemainsSmallAndJdkOnly() {
        Method[] methods = BackendProvider.class.getDeclaredMethods();
        assertEquals(1, methods.length);
        assertEquals(BackendDescriptor.class, methods[0].getReturnType());

        Method[] queryMethods = QueryBackendProvider.class.getDeclaredMethods();
        assertEquals(1, queryMethods.length);
        assertEquals(QueryFacade.class, queryMethods[0].getReturnType());

        Method[] cacheProviderMethods = CacheInvalidationBackendProvider.class.getDeclaredMethods();
        assertEquals(1, cacheProviderMethods.length);
        assertEquals(CacheInvalidationPort.class, cacheProviderMethods[0].getReturnType());
        assertEquals(2, CacheInvalidationPort.class.getDeclaredMethods().length);

        Method[] modelLoadMethods = ModelLoadBackendProvider.class.getDeclaredMethods();
        assertEquals(1, modelLoadMethods.length);
        assertEquals(ModelLoadPort.class, modelLoadMethods[0].getReturnType());
        assertEquals(1, ModelLoadPort.class.getDeclaredMethods().length);

        Method[] atomicRefreshMethods = AtomicRefreshBackendProvider.class.getDeclaredMethods();
        assertEquals(1, atomicRefreshMethods.length);
        assertEquals(AtomicRefreshPort.class, atomicRefreshMethods[0].getReturnType());
        assertEquals(1, AtomicRefreshPort.class.getDeclaredMethods().length);

        Set<Class<?>> apiTypes = Set.of(BackendId.class, BackendDescriptor.class,
                BackendCapability.class, BackendProvider.class, QueryBackendProvider.class,
                ModelLoadBackendProvider.class, ModelLoadPort.class,
                AtomicRefreshBackendProvider.class, AtomicRefreshPort.class,
                CacheInvalidationBackendProvider.class, CacheInvalidationPort.class);
        apiTypes.forEach(type -> Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .forEach(field -> assertFalse(isForbidden(field.getType()),
                        () -> type.getName() + " leaks " + field.getType().getName())));
    }

    @Test
    void backendIdentityAndCapabilitiesFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> BackendId.of("Mongo"));
        assertThrows(IllegalArgumentException.class, () -> BackendId.of("mongo backend"));

        BackendDescriptor descriptor = new BackendDescriptor(
                BackendId.of("mongo"), Set.of(BackendCapability.QUERY));
        assertTrue(descriptor.supports(BackendCapability.QUERY));
        assertFalse(descriptor.supports(BackendCapability.MODEL_LOAD));
        assertThrows(UnsupportedOperationException.class,
                () -> descriptor.capabilities().add(BackendCapability.MODEL_LOAD));
    }

    private boolean isForbidden(Class<?> type) {
        String name = type.getName();
        return name.startsWith("org.springframework.")
                || name.startsWith("java.sql.")
                || name.startsWith("javax.sql.")
                || name.startsWith("jakarta.servlet.")
                || name.contains(".impl.")
                || name.contains(".controller.");
    }
}
