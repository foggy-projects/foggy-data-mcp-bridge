package com.foggyframework.dataset.db.model.lifecycle.catalog;

import com.foggyframework.dataset.db.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.db.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.db.model.semantic.service.impl.SemanticServiceV3Impl;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SemanticMetadataCatalogIdentityTest {

    @Test
    @SuppressWarnings("unchecked")
    void multiModelMetadataMustUseModelsFromOneFinalSnapshot() {
        QueryModel firstMaterialized = mock(QueryModel.class);
        QueryModel secondMaterialized = mock(QueryModel.class);
        QueryModel firstPinned = mock(QueryModel.class);
        QueryModel secondPinned = mock(QueryModel.class);
        CatalogIdentity firstGeneration = identity("catalog:boot:1");
        CatalogIdentity finalGeneration = identity("catalog:boot:2");
        CatalogResolution<QueryModel> firstMaterializedResolution =
                resolution("FirstQueryModel", firstMaterialized, firstGeneration);
        CatalogResolution<QueryModel> secondMaterializedResolution =
                resolution("SecondQueryModel", secondMaterialized, finalGeneration);
        Map<String, CatalogResolution<QueryModel>> finalResolutions = Map.of(
                "FirstQueryModel",
                resolution("FirstQueryModel", firstPinned, finalGeneration),
                "SecondQueryModel",
                resolution("SecondQueryModel", secondPinned, finalGeneration));
        QueryModelLoader loader = mock(QueryModelLoader.class);
        when(loader.resolveJdbcQueryModel("FirstQueryModel", "tenant-a"))
                .thenReturn(firstMaterializedResolution);
        when(loader.resolveJdbcQueryModel("SecondQueryModel", "tenant-a"))
                .thenReturn(secondMaterializedResolution);
        when(loader.resolveJdbcQueryModels(anyCollection(), eq("tenant-a")))
                .thenReturn(finalResolutions);

        Object batch = ReflectionTestUtils.invokeMethod(
                service(loader),
                "resolveCatalogModels",
                List.of("FirstQueryModel", "SecondQueryModel"),
                "tenant-a",
                true);
        Map<String, QueryModel> models = (Map<String, QueryModel>)
                ReflectionTestUtils.getField(batch, "models");
        Map<String, CatalogResolution<QueryModel>> resolutions =
                (Map<String, CatalogResolution<QueryModel>>)
                        ReflectionTestUtils.getField(batch, "resolutions");

        assertSame(firstPinned, models.get("FirstQueryModel"));
        assertSame(secondPinned, models.get("SecondQueryModel"));
        assertEquals(finalGeneration, ReflectionTestUtils.getField(batch, "catalogIdentity"));
        assertEquals(finalGeneration,
                resolutions.get("FirstQueryModel").catalogIdentity());
        assertEquals(finalGeneration,
                resolutions.get("SecondQueryModel").catalogIdentity());
        assertTrue((Boolean) ReflectionTestUtils.getField(batch, "lifecycleTracked"));
    }

    @Test
    void multiModelMetadataMustRejectMixedFinalCatalogIdentities() {
        QueryModel first = mock(QueryModel.class);
        QueryModel second = mock(QueryModel.class);
        CatalogIdentity generationOne = identity("catalog:boot:1");
        CatalogIdentity generationTwo = identity("catalog:boot:2");
        CatalogResolution<QueryModel> firstResolution =
                resolution("FirstQueryModel", first, generationOne);
        CatalogResolution<QueryModel> secondResolution =
                resolution("SecondQueryModel", second, generationTwo);
        Map<String, CatalogResolution<QueryModel>> mixedResolutions = Map.of(
                "FirstQueryModel", firstResolution,
                "SecondQueryModel", secondResolution);
        QueryModelLoader loader = mock(QueryModelLoader.class);
        when(loader.resolveJdbcQueryModel("FirstQueryModel", "tenant-a"))
                .thenReturn(firstResolution);
        when(loader.resolveJdbcQueryModel("SecondQueryModel", "tenant-a"))
                .thenReturn(secondResolution);
        when(loader.resolveJdbcQueryModels(anyCollection(), eq("tenant-a")))
                .thenReturn(mixedResolutions);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        service(loader),
                        "resolveCatalogModels",
                        List.of("FirstQueryModel", "SecondQueryModel"),
                        "tenant-a",
                        true));

        assertEquals("metadata models resolved from different catalog generations",
                failure.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void legacyBulkResolverFallbackMustBeExplicitlyUntracked() {
        QueryModel first = mock(QueryModel.class);
        QueryModel second = mock(QueryModel.class);
        QueryModelLoader loader = mock(QueryModelLoader.class);
        when(loader.getJdbcQueryModel("FirstQueryModel", "tenant-a")).thenReturn(first);
        when(loader.getJdbcQueryModel("SecondQueryModel", "tenant-a")).thenReturn(second);
        when(loader.resolveJdbcQueryModels(anyCollection(), eq("tenant-a"))).thenReturn(null);

        Object batch = ReflectionTestUtils.invokeMethod(
                service(loader),
                "resolveCatalogModels",
                List.of("FirstQueryModel", "SecondQueryModel"),
                "tenant-a",
                true);

        Map<String, QueryModel> models = (Map<String, QueryModel>)
                ReflectionTestUtils.getField(batch, "models");
        Map<String, CatalogResolution<QueryModel>> resolutions =
                (Map<String, CatalogResolution<QueryModel>>)
                        ReflectionTestUtils.getField(batch, "resolutions");
        assertSame(first, models.get("FirstQueryModel"));
        assertSame(second, models.get("SecondQueryModel"));
        assertEquals(null, ReflectionTestUtils.getField(batch, "catalogIdentity"));
        assertTrue(resolutions.isEmpty());
        assertFalse((Boolean) ReflectionTestUtils.getField(batch, "lifecycleTracked"));
    }

    private SemanticServiceV3Impl service(QueryModelLoader loader) {
        SemanticServiceV3Impl service = new SemanticServiceV3Impl();
        ReflectionTestUtils.setField(service, "queryModelLoader", loader);
        return service;
    }

    private CatalogResolution<QueryModel> resolution(
            String name,
            QueryModel model,
            CatalogIdentity identity
    ) {
        return new CatalogResolution<>(name, model, identity, Map.of(), true);
    }

    private CatalogIdentity identity(String generation) {
        return new CatalogIdentity(
                "tenant-a",
                new CatalogGeneration(generation),
                new SourceRevision("source:boot:1"));
    }
}
