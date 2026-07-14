package com.foggyframework.dataset.db.model.lifecycle.namespace;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.impl.SemanticServiceV3Impl;
import com.foggyframework.dataset.db.model.spi.NamespaceContext;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Semantic metadata 入口必须把 null/blank/named 都作为显式 namespace，且精确恢复调用方。
 */
@SuppressWarnings("deprecation")
class SemanticServiceNamespaceScopeTest {

    private static final String MODEL = "NamespaceScopeMetadataProbe";
    private static final String OUTER_NAMESPACE = "outer-semantic";
    private static final NamespaceCase[] CASES = {
            new NamespaceCase("null-default", null, ""),
            new NamespaceCase("blank-default", "  ", ""),
            new NamespaceCase("named", "  inner-semantic  ", "inner-semantic")
    };

    @AfterEach
    void clearNamespace() {
        NamespaceContext.clear();
    }

    @Test
    void normalReturnMustUseEffectiveExplicitNamespaceAndRestoreOuter() {
        for (NamespaceCase namespaceCase : CASES) {
            AtomicReference<String> contextSeenByLoader = new AtomicReference<>();
            AtomicReference<String> argumentSeenByLoader = new AtomicReference<>();
            QueryModelLoader loader = mock(QueryModelLoader.class);
            when(loader.getJdbcQueryModel(eq(MODEL), any())).thenAnswer(invocation -> {
                contextSeenByLoader.set(NamespaceContext.getNamespace());
                argumentSeenByLoader.set(invocation.getArgument(1));
                return null;
            });

            SemanticServiceV3Impl service = service(loader);
            NamespaceContext.setNamespace(OUTER_NAMESPACE);
            try {
                SemanticMetadataResponse response = service.getMetadata(
                        request(),
                        "markdown",
                        SemanticRequestContext.ofNamespace(namespaceCase.input())
                );

                assertAll(
                        namespaceCase.label(),
                        () -> assertNotNull(response),
                        () -> assertEquals(namespaceCase.effective(), contextSeenByLoader.get(),
                                "scope must expose the canonical effective namespace"),
                        () -> assertEquals(namespaceCase.effective(), argumentSeenByLoader.get(),
                                "loader argument must match the scope effective namespace"),
                        () -> assertEquals(OUTER_NAMESPACE, NamespaceContext.getNamespace(),
                                "normal return must restore the caller namespace")
                );
            } finally {
                NamespaceContext.clear();
            }
        }
    }

    @Test
    void exceptionalReturnMustUseEffectiveExplicitNamespaceAndRestoreOuter() {
        for (NamespaceCase namespaceCase : CASES) {
            AtomicReference<String> contextSeenByLoader = new AtomicReference<>();
            AtomicReference<String> argumentSeenByLoader = new AtomicReference<>();
            IllegalStateException marker = new IllegalStateException("controlled semantic load failure");
            QueryModelLoader loader = mock(QueryModelLoader.class);
            when(loader.getJdbcQueryModel(eq(MODEL), any())).thenAnswer(invocation -> {
                contextSeenByLoader.set(NamespaceContext.getNamespace());
                argumentSeenByLoader.set(invocation.getArgument(1));
                throw marker;
            });

            SemanticServiceV3Impl service = service(loader);
            NamespaceContext.setNamespace(OUTER_NAMESPACE);
            try {
                IllegalStateException thrown = assertThrows(
                        IllegalStateException.class,
                        () -> service.getMetadata(
                                request(),
                                "markdown",
                                SemanticRequestContext.ofNamespace(namespaceCase.input())
                        ),
                        namespaceCase.label()
                );

                assertAll(
                        namespaceCase.label(),
                        () -> assertSame(marker, thrown, "the original failure must propagate"),
                        () -> assertEquals(namespaceCase.effective(), contextSeenByLoader.get(),
                                "exceptional path must run in the canonical effective namespace"),
                        () -> assertEquals(namespaceCase.effective(), argumentSeenByLoader.get(),
                                "loader argument must match the exceptional scope"),
                        () -> assertEquals(OUTER_NAMESPACE, NamespaceContext.getNamespace(),
                                "exceptional return must restore the caller namespace")
                );
            } finally {
                NamespaceContext.clear();
            }
        }
    }

    private SemanticServiceV3Impl service(QueryModelLoader loader) {
        SemanticServiceV3Impl service = new SemanticServiceV3Impl();
        ReflectionTestUtils.setField(service, "queryModelLoader", loader);
        return service;
    }

    private SemanticMetadataRequest request() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(List.of(MODEL));
        request.setLevels(List.of(1));
        return request;
    }

    private record NamespaceCase(String label, String input, String effective) {
    }
}
