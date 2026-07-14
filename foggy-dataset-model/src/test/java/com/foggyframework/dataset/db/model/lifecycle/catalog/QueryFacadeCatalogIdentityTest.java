package com.foggyframework.dataset.db.model.lifecycle.catalog;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.db.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.DataSetResultFilterManager;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.service.impl.QueryFacadeImpl;
import com.foggyframework.dataset.db.model.spi.QueryEngine;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.PagingResultImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryFacadeCatalogIdentityTest {

    @Test
    void prePinnedSameResolutionIsIdempotentAtQueryFacadeBoundary() {
        String namespace = "tenant-catalog";
        String modelName = "FactOrderQueryModel";
        QueryModel model = mock(QueryModel.class);
        CatalogResolution<QueryModel> resolution = resolution(
                modelName, model, namespace, "catalog-a", "source-a", "binding-a");
        QueryModelLoader loader = mock(QueryModelLoader.class);
        when(loader.resolveJdbcQueryModel(modelName, namespace)).thenReturn(resolution);
        DataSetResultFilterManager filters = mock(DataSetResultFilterManager.class);
        doAnswer(invocation -> {
            ModelResultContext executionContext = invocation.getArgument(0);
            PagingResultImpl paging = new PagingResultImpl();
            executionContext.setPagingResult(paging);
            executionContext.setQueryResult(DbQueryResult.of(paging, null));
            executionContext.setSkipQuery(true);
            return null;
        }).when(filters).beforeQuery(any(ModelResultContext.class));
        QueryFacadeImpl facade = facade(loader, filters);
        ModelResultContext context = context(modelName, namespace);
        context.pinCatalogResolution(resolution, namespace);

        facade.queryModelResult(context);

        assertSame(model, context.getQueryModel());
        assertEquals(resolution.catalogIdentity(), context.getCatalogIdentity());
        assertEquals(resolution.dependencyBindings(), context.getDatasourceBindingIdentities());
        verify(loader).resolveJdbcQueryModel(modelName, namespace);
        verify(loader, never()).getJdbcQueryModel(any(), any());
    }

    @Test
    void prePinnedGenerationSwitchIsRejectedBeforeFiltersOrQueryExecution() {
        String namespace = "tenant-catalog";
        String modelName = "FactOrderQueryModel";
        QueryModel model = mock(QueryModel.class);
        CatalogResolution<QueryModel> first = resolution(
                modelName, model, namespace, "catalog-a", "source-a", "binding-a");
        CatalogResolution<QueryModel> switched = resolution(
                modelName, model, namespace, "catalog-b", "source-b", "binding-b");
        QueryModelLoader loader = mock(QueryModelLoader.class);
        when(loader.resolveJdbcQueryModel(modelName, namespace)).thenReturn(switched);
        DataSetResultFilterManager filters = mock(DataSetResultFilterManager.class);
        QueryFacadeImpl facade = facade(loader, filters);
        ModelResultContext context = context(modelName, namespace);
        context.pinCatalogResolution(first, namespace);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> facade.queryModelResult(context));

        assertEquals("CONFLICTING_CATALOG_REPIN", failure.getMessage());
        assertSame(model, context.getQueryModel());
        assertEquals(first.catalogIdentity(), context.getCatalogIdentity());
        assertEquals(first.dependencyBindings(), context.getDatasourceBindingIdentities());
        verify(filters, never()).beforeQuery(any(ModelResultContext.class));
        verify(model, never()).query(any(SystemBundlesContext.class), any(ModelResultContext.class));
    }

    @Test
    void queryEntryMustPinTheAtomicCatalogResolutionIntoExecutionContext() {
        String namespace = "tenant-catalog";
        String modelName = "FactOrderQueryModel";
        QueryModel model = mock(QueryModel.class);
        CatalogIdentity catalogIdentity = new CatalogIdentity(
                namespace,
                new CatalogGeneration("catalog-controlled"),
                new SourceRevision("source-controlled")
        );
        DatasourceBindingIdentity bindingIdentity = new DatasourceBindingIdentity(
                "runtime:namespace-default:" + namespace,
                "runtime-registry:sales",
                new DatasourceBindingGeneration("binding-controlled")
        );
        CatalogResolution<QueryModel> resolution = new CatalogResolution<>(
                modelName,
                model,
                catalogIdentity,
                Map.of(bindingIdentity.bindingKey(), bindingIdentity),
                true
        );
        QueryModelLoader loader = mock(QueryModelLoader.class);
        when(loader.resolveJdbcQueryModel(modelName, namespace)).thenReturn(resolution);

        DataSetResultFilterManager filters = mock(DataSetResultFilterManager.class);
        doAnswer(invocation -> {
            ModelResultContext context = invocation.getArgument(0);
            PagingResultImpl paging = new PagingResultImpl();
            context.setPagingResult(paging);
            context.setQueryResult(DbQueryResult.of(paging, null));
            context.setSkipQuery(true);
            return null;
        }).when(filters).beforeQuery(any(ModelResultContext.class));

        QueryFacadeImpl facade = new QueryFacadeImpl();
        ReflectionTestUtils.setField(facade, "queryModelLoader", loader);
        ReflectionTestUtils.setField(facade, "systemBundlesContext", mock(SystemBundlesContext.class));
        ReflectionTestUtils.setField(facade, "dataSetResultFilterManager", filters);

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(modelName);
        ModelResultContext context = new ModelResultContext(
                PagingRequest.buildPagingRequest(request, 20), null);
        context.setNamespace(namespace);

        facade.queryModelResult(context);

        assertSame(model, context.getQueryModel());
        assertEquals(catalogIdentity, context.getCatalogIdentity());
        assertEquals(Map.of(bindingIdentity.bindingKey(), bindingIdentity),
                context.getDatasourceBindingIdentities());
        assertEquals(true, context.isBindingIdentityComplete());
    }

    @Test
    void queryEngineFromAnotherModelMustFailClosedWithoutReplacingPinnedIdentity() {
        String namespace = "tenant-catalog";
        String modelName = "FactOrderQueryModel";
        QueryModel pinnedModel = mock(QueryModel.class);
        QueryModel differentModel = mock(QueryModel.class);
        CatalogIdentity catalogIdentity = new CatalogIdentity(
                namespace,
                new CatalogGeneration("catalog-pinned"),
                new SourceRevision("source-pinned")
        );
        DatasourceBindingIdentity bindingIdentity = new DatasourceBindingIdentity(
                "runtime:namespace-default:" + namespace,
                "runtime-registry:sales",
                new DatasourceBindingGeneration("binding-pinned")
        );
        CatalogResolution<QueryModel> resolution = new CatalogResolution<>(
                modelName,
                pinnedModel,
                catalogIdentity,
                Map.of(bindingIdentity.bindingKey(), bindingIdentity),
                true
        );
        QueryModelLoader loader = mock(QueryModelLoader.class);
        when(loader.resolveJdbcQueryModel(modelName, namespace)).thenReturn(resolution);

        QueryEngine queryEngine = mock(QueryEngine.class);
        when(queryEngine.getJdbcQueryModel()).thenReturn(differentModel);
        PagingResultImpl pagingResult = new PagingResultImpl();
        when(pinnedModel.query(any(SystemBundlesContext.class), any(ModelResultContext.class)))
                .thenReturn(DbQueryResult.of(pagingResult, queryEngine));
        DataSetResultFilterManager filters = mock(DataSetResultFilterManager.class);

        QueryFacadeImpl facade = new QueryFacadeImpl();
        ReflectionTestUtils.setField(facade, "queryModelLoader", loader);
        ReflectionTestUtils.setField(facade, "systemBundlesContext", mock(SystemBundlesContext.class));
        ReflectionTestUtils.setField(facade, "dataSetResultFilterManager", filters);

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(modelName);
        ModelResultContext context = new ModelResultContext(
                PagingRequest.buildPagingRequest(request, 20), null);
        context.setNamespace(namespace);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> facade.queryModelResult(context)
        );

        assertEquals(
                "QUERY_ENGINE_MODEL_MISMATCH: query engine did not retain the pinned query model",
                failure.getMessage()
        );
        assertSame(pinnedModel, context.getQueryModel());
        assertEquals(catalogIdentity, context.getCatalogIdentity());
        assertEquals(Map.of(bindingIdentity.bindingKey(), bindingIdentity),
                context.getDatasourceBindingIdentities());
        assertEquals(true, context.isBindingIdentityComplete());
        verify(queryEngine, never()).getJdbcQuery();
        verify(filters, never()).process(any(ModelResultContext.class));
    }

    @Test
    void atomicPinMustRejectNamespaceMismatchWithoutPartialMutation() {
        QueryModel model = mock(QueryModel.class);
        CatalogIdentity identity = new CatalogIdentity(
                "tenant-a",
                new CatalogGeneration("catalog-a"),
                new SourceRevision("source-a")
        );
        CatalogResolution<QueryModel> resolution = new CatalogResolution<>(
                "FactOrderQueryModel", model, identity, Map.of(), true);
        ModelResultContext context = new ModelResultContext();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> context.pinCatalogResolution(resolution, "tenant-b")
        );

        assertEquals("catalog resolution namespace mismatch", failure.getMessage());
        assertNull(context.getQueryModel());
        assertNull(context.getCatalogIdentity());
        assertEquals(Map.of(), context.getDatasourceBindingIdentities());
        assertEquals(false, context.isBindingIdentityComplete());
    }

    @Test
    void atomicPinMustRejectConflictingRepinAndPreserveOriginalIdentity() {
        String namespace = "tenant-a";
        QueryModel firstModel = mock(QueryModel.class);
        QueryModel secondModel = mock(QueryModel.class);
        CatalogIdentity firstIdentity = new CatalogIdentity(
                namespace,
                new CatalogGeneration("catalog-a"),
                new SourceRevision("source-a")
        );
        CatalogIdentity secondIdentity = new CatalogIdentity(
                namespace,
                new CatalogGeneration("catalog-b"),
                new SourceRevision("source-b")
        );
        DatasourceBindingIdentity firstBinding = new DatasourceBindingIdentity(
                "runtime:namespace-default:" + namespace,
                "runtime-registry:sales",
                new DatasourceBindingGeneration("binding-a")
        );
        CatalogResolution<QueryModel> first = new CatalogResolution<>(
                "FactOrderQueryModel",
                firstModel,
                firstIdentity,
                Map.of(firstBinding.bindingKey(), firstBinding),
                true
        );
        CatalogResolution<QueryModel> second = new CatalogResolution<>(
                "FactOrderQueryModel", secondModel, secondIdentity, Map.of(), false);
        ModelResultContext context = new ModelResultContext();
        context.pinCatalogResolution(first, namespace);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> context.pinCatalogResolution(second, namespace)
        );

        assertEquals("CONFLICTING_CATALOG_REPIN", failure.getMessage());
        assertSame(firstModel, context.getQueryModel());
        assertEquals(firstIdentity, context.getCatalogIdentity());
        assertEquals(Map.of(firstBinding.bindingKey(), firstBinding),
                context.getDatasourceBindingIdentities());
        assertEquals(true, context.isBindingIdentityComplete());
    }

    private CatalogResolution<QueryModel> resolution(String modelName,
                                                     QueryModel model,
                                                     String namespace,
                                                     String catalogGeneration,
                                                     String sourceRevision,
                                                     String bindingGeneration) {
        DatasourceBindingIdentity binding = new DatasourceBindingIdentity(
                "runtime:namespace-default:" + namespace,
                "runtime-registry:sales",
                new DatasourceBindingGeneration(bindingGeneration));
        return new CatalogResolution<>(
                modelName,
                model,
                new CatalogIdentity(
                        namespace,
                        new CatalogGeneration(catalogGeneration),
                        new SourceRevision(sourceRevision)),
                Map.of(binding.bindingKey(), binding),
                true);
    }

    private QueryFacadeImpl facade(QueryModelLoader loader, DataSetResultFilterManager filters) {
        QueryFacadeImpl facade = new QueryFacadeImpl();
        ReflectionTestUtils.setField(facade, "queryModelLoader", loader);
        ReflectionTestUtils.setField(facade, "systemBundlesContext", mock(SystemBundlesContext.class));
        ReflectionTestUtils.setField(facade, "dataSetResultFilterManager", filters);
        return facade;
    }

    private ModelResultContext context(String modelName, String namespace) {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(modelName);
        ModelResultContext context = new ModelResultContext(
                PagingRequest.buildPagingRequest(request, 20), null);
        context.setNamespace(namespace);
        return context;
    }
}
