package com.foggyframework.dataset.db.model.lifecycle.namespace;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.dataset.db.model.engine.query_model.QueryModelLoaderImpl;
import com.foggyframework.dataset.db.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogCandidate;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.db.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.dataset.db.model.spi.NamespaceContext;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * QueryModelLoader 的显式 namespace 必须在 cache hit 和失败路径遮蔽外层并精确恢复。
 */
@SuppressWarnings({"deprecation", "rawtypes", "unchecked"})
class QueryModelLoaderNamespaceScopeTest {

    private static final String NAMED_MODEL = "NamedCacheHitModel";
    private static final String DEFAULT_MODEL = "DefaultCacheHitModel";
    private static final String NAMED_NAMESPACE = "loader-named";
    private static final String OUTER_NAMESPACE = "outer-loader";

    @AfterEach
    void clearNamespace() {
        NamespaceContext.clear();
    }

    @Test
    void getMustMaskOuterAndRestoreItForNamedAndDefaultCacheHits() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        QueryModelLoaderImpl loader = loader(
                mock(SystemBundlesContext.class), mock(FileFsscriptLoader.class), store);
        QueryModelSupport namedModel = mock(QueryModelSupport.class);
        QueryModelSupport defaultModel = mock(QueryModelSupport.class);
        seed(store, NAMED_NAMESPACE, NAMED_MODEL, namedModel);
        seed(store, "", DEFAULT_MODEL, defaultModel);

        NamespaceContext.setNamespace(OUTER_NAMESPACE);
        try {
            QueryModel namedResult = loader.getJdbcQueryModel(NAMED_MODEL, "  " + NAMED_NAMESPACE + "  ");
            assertEquals(OUTER_NAMESPACE, NamespaceContext.getNamespace(),
                    "named cache hit must restore the outer namespace");

            QueryModel defaultResult = loader.getJdbcQueryModel(DEFAULT_MODEL, null);

            assertAll(
                    () -> assertSame(namedModel, namedResult),
                    () -> assertSame(defaultModel, defaultResult),
                    () -> assertEquals(OUTER_NAMESPACE, NamespaceContext.getNamespace(),
                            "default cache hit must restore the outer namespace")
            );
        } finally {
            NamespaceContext.clear();
        }
    }

    @Test
    void getMustMaskOuterAndRestoreItForNamedAndDefaultLookupFailures() {
        IllegalArgumentException marker = new IllegalArgumentException("controlled resource lookup failure");
        List<LookupObservation> observations = new ArrayList<>();
        SystemBundlesContext bundles = mock(SystemBundlesContext.class);
        when(bundles.findResourceByName(anyString(), anyString(), anyBoolean())).thenAnswer(invocation -> {
            observations.add(new LookupObservation(
                    invocation.getArgument(1),
                    NamespaceContext.getNamespace()
            ));
            throw marker;
        });
        QueryModelLoaderImpl loader = loader(bundles, mock(FileFsscriptLoader.class), new CatalogSnapshotStore());

        NamespaceContext.setNamespace(OUTER_NAMESPACE);
        try {
            IllegalArgumentException namedFailure = assertThrows(
                    IllegalArgumentException.class,
                    () -> loader.getJdbcQueryModel("NamedMissingModel", "  " + NAMED_NAMESPACE + "  ")
            );
            assertEquals(OUTER_NAMESPACE, NamespaceContext.getNamespace(),
                    "named lookup failure must restore the outer namespace");

            IllegalArgumentException defaultFailure = assertThrows(
                    IllegalArgumentException.class,
                    () -> loader.getJdbcQueryModel("DefaultMissingModel", null)
            );

            assertAll(
                    () -> assertSame(marker, namedFailure),
                    () -> assertSame(marker, defaultFailure),
                    () -> assertEquals(List.of(
                                    new LookupObservation(NAMED_NAMESPACE, NAMED_NAMESPACE),
                                    new LookupObservation("", "")
                            ), observations,
                            "resource lookup argument and active scope must stay aligned"),
                    () -> assertEquals(OUTER_NAMESPACE, NamespaceContext.getNamespace(),
                            "default lookup failure must restore the outer namespace")
            );
        } finally {
            NamespaceContext.clear();
        }
    }

    @Test
    void loadMustMaskOuterAndRestoreItForNamedAndDefaultScriptFailures() {
        IllegalStateException marker = new IllegalStateException("controlled script load failure");
        List<String> contextsSeenByScriptLoader = new ArrayList<>();
        FileFsscriptLoader fileLoader = mock(FileFsscriptLoader.class);
        when(fileLoader.findLoadFsscript(any(BundleResource.class))).thenAnswer(invocation -> {
            contextsSeenByScriptLoader.add(NamespaceContext.getNamespace());
            throw marker;
        });
        QueryModelLoaderImpl loader = loader(
                mock(SystemBundlesContext.class), fileLoader, new CatalogSnapshotStore());

        NamespaceContext.setNamespace(OUTER_NAMESPACE);
        try {
            IllegalStateException namedFailure = assertThrows(
                    IllegalStateException.class,
                    () -> loader.loadJdbcQueryModel(bundleResource(NAMED_NAMESPACE))
            );
            assertEquals(OUTER_NAMESPACE, NamespaceContext.getNamespace(),
                    "named script failure must restore the outer namespace");

            IllegalStateException defaultFailure = assertThrows(
                    IllegalStateException.class,
                    () -> loader.loadJdbcQueryModel(bundleResource(null))
            );

            assertAll(
                    () -> assertSame(marker, namedFailure),
                    () -> assertSame(marker, defaultFailure),
                    () -> assertEquals(List.of(NAMED_NAMESPACE, ""), contextsSeenByScriptLoader,
                            "BundleResource named/default values must explicitly mask the outer namespace"),
                    () -> assertEquals(OUTER_NAMESPACE, NamespaceContext.getNamespace(),
                            "default script failure must restore the outer namespace")
            );
        } finally {
            NamespaceContext.clear();
        }
    }

    private QueryModelLoaderImpl loader(SystemBundlesContext bundles, FileFsscriptLoader fileLoader) {
        return loader(bundles, fileLoader, new CatalogSnapshotStore());
    }

    private QueryModelLoaderImpl loader(
            SystemBundlesContext bundles,
            FileFsscriptLoader fileLoader,
            CatalogSnapshotStore store
    ) {
        return new QueryModelLoaderImpl(null, bundles, fileLoader, List.of(), store);
    }

    private BundleResource bundleResource(String namespace) {
        BundleDefinition definition = mock(BundleDefinition.class);
        when(definition.getNamespace()).thenReturn(namespace);
        Bundle bundle = mock(Bundle.class);
        when(bundle.getDefinition()).thenReturn(definition);
        BundleResource resource = mock(BundleResource.class);
        when(resource.getBundle()).thenReturn(bundle);
        return resource;
    }

    private void seed(
            CatalogSnapshotStore store,
            String namespace,
            String name,
            QueryModelSupport model
    ) {
        when(model.getName()).thenReturn(name);
        when(model.getShortAlias()).thenReturn(name + "Alias");
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate(namespace)) {
            CatalogCandidate candidate = scope.candidate();
            candidate.discoverQueryModels(Set.of(name));
            when(model.getShortAlias()).thenReturn(candidate.aliasFor(name));
            candidate.putQueryModel(name, model, new ModelProvenance(
                    name,
                    ModelProvenance.ModelKind.QUERY,
                    candidate.sourceRevision(),
                    Set.of(),
                    Map.of(),
                    false,
                    List.of()
            ));
            scope.commit();
        }
    }

    private record LookupObservation(String argumentNamespace, String activeNamespace) {
    }
}
