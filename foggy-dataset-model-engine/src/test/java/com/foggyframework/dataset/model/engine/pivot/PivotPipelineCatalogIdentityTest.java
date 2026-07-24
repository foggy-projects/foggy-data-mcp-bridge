package com.foggyframework.dataset.model.engine.pivot;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PivotPipelineCatalogIdentityTest {

    private static final String MODEL = "SalesQM";
    private static final String NAMESPACE = "tenant-a";

    @Test
    void entryResolvesOncePinsEveryPhaseAndUsesStrongIdentityForReadAndWrite() {
        QueryModel model = queryModel(MODEL);
        CatalogResolution<QueryModel> resolution = resolution(model, NAMESPACE, "catalog-a", true, true);
        QueryModelLoader loader = mock(QueryModelLoader.class);
        when(loader.resolveJdbcQueryModel(MODEL, NAMESPACE)).thenReturn(resolution);
        SemanticQueryServiceV3 service = emptySemanticService();
        PivotOuterCacheProvider cache = enabledMissCache();
        PivotPipeline pipeline = pipeline(service, loader, cache,
                (namespace, requestedModel, queryModel) ->
                        new PivotOuterCacheModelIdentity("provider-bundle", "provider-freshness"),
                new PivotPipeline.OuterCacheOptions(
                        true, 60_000L, 16, "manual-bundle", "manual-freshness"));

        SemanticQueryResponse response = pipeline.execute(
                MODEL, request(), SemanticRequestContext.ofNamespace(NAMESPACE));

        verify(loader, times(1)).resolveJdbcQueryModel(MODEL, NAMESPACE);
        verify(loader, never()).getJdbcQueryModel(anyString(), anyString());
        ArgumentCaptor<SemanticRequestContext> contextCaptor =
                ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(service).queryModel(eq(MODEL), any(SemanticQueryRequest.class),
                eq("execute"), contextCaptor.capture());
        assertSame(resolution, contextCaptor.getValue().getCatalogResolution());
        verify(cache).lookup(anyString(), anyLong());
        verify(cache).store(anyString(), any(SemanticQueryResponse.class), anyLong(),
                eq(NAMESPACE), eq(MODEL));

        Map<String, Object> identity = diagnostic(response, "pivot.cache.identity");
        assertEquals(PivotOuterCacheStrongIdentity.STATUS_COMPLETE, identity.get("status"));
        assertEquals(1, identity.get("bindingCount"));
        assertEquals(true, identity.get("manualTokenPresent"));
        assertEquals(false, identity.get("supplementaryProviderFailed"));
        assertEquals(64, String.valueOf(identity.get("identityHash")).length());
        assertFalse(String.valueOf(identity).contains("provider-bundle"));
        assertFalse(String.valueOf(identity).contains("manual-bundle"));
        assertFalse(String.valueOf(identity).contains("binding:sales"));
        assertFalse(String.valueOf(identity).contains("backend:sales"));
    }

    @Test
    void completeLifecycleStillRefusesCacheWhenSupplementaryProviderThrows() {
        QueryModel model = queryModel(MODEL);
        CatalogResolution<QueryModel> resolution = resolution(model, NAMESPACE, "catalog-a", true, true);
        QueryModelLoader loader = mock(QueryModelLoader.class);
        when(loader.resolveJdbcQueryModel(MODEL, NAMESPACE)).thenReturn(resolution);
        SemanticQueryServiceV3 service = emptySemanticService();
        PivotOuterCacheProvider cache = enabledMissCache();
        PivotOuterCacheModelIdentityProvider identityProvider =
                mock(PivotOuterCacheModelIdentityProvider.class);
        when(identityProvider.resolve(NAMESPACE, MODEL, model))
                .thenThrow(new IllegalStateException("sensitive-provider-token"));
        PivotPipeline pipeline = pipeline(
                service,
                loader,
                cache,
                identityProvider,
                new PivotPipeline.OuterCacheOptions(
                        true, 60_000L, 16, "manual-sensitive-token", "manual-freshness"));

        SemanticQueryResponse response = pipeline.execute(
                MODEL, request(), SemanticRequestContext.ofNamespace(NAMESPACE));

        verify(identityProvider).resolve(NAMESPACE, MODEL, model);
        verify(cache, never()).lookup(anyString(), anyLong());
        verify(cache, never()).store(anyString(), any(), anyLong(), any(), any());
        ArgumentCaptor<SemanticRequestContext> phaseContext =
                ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(service).queryModel(eq(MODEL), any(), eq("execute"), phaseContext.capture());
        assertSame(resolution, phaseContext.getValue().getCatalogResolution(),
                "supplementary failure must not discard the lifecycle pin");

        Map<String, Object> identity = diagnostic(response, "pivot.cache.identity");
        assertEquals(PivotOuterCacheTelemetry.SUPPLEMENTARY_IDENTITY_PROVIDER_FAILED_STATUS,
                identity.get("status"));
        assertEquals(64, String.valueOf(identity.get("identityHash")).length());
        assertEquals(1, identity.get("bindingCount"));
        assertEquals(true, identity.get("manualTokenPresent"));
        assertEquals(true, identity.get("supplementaryProviderFailed"));
        assertEquals(IllegalStateException.class.getName(),
                identity.get("supplementaryProviderFailureClass"));
        assertEquals(PivotOuterCacheTelemetry.SUPPLEMENTARY_IDENTITY_PROVIDER_FAILED_REASON,
                diagnostic(response, "pivot.cache.refused").get("reason"));
        assertFalse(hasDiagnostic(response, "pivot.cache.lookup"));
        assertFalse(String.valueOf(identity).contains("sensitive-provider-token"));
        assertFalse(String.valueOf(identity).contains("manual-sensitive-token"));
    }

    @Test
    void missingLifecycleAndProviderFailureCannotUseManualTokensToReadOrWrite() {
        QueryModel model = queryModel(MODEL);
        QueryModelLoader loader = mock(QueryModelLoader.class);
        when(loader.resolveJdbcQueryModel(MODEL, NAMESPACE)).thenReturn(null);
        when(loader.getJdbcQueryModel(MODEL, NAMESPACE)).thenReturn(model);
        SemanticQueryServiceV3 service = emptySemanticService();
        PivotOuterCacheProvider cache = enabledMissCache();
        PivotPipeline pipeline = pipeline(service, loader, cache,
                (namespace, requestedModel, queryModel) -> {
                    throw new IllegalStateException("provider failed");
                },
                new PivotPipeline.OuterCacheOptions(
                        true, 60_000L, 16, "manual-bundle", "manual-freshness"));

        SemanticQueryResponse response = pipeline.execute(
                MODEL, request(), SemanticRequestContext.ofNamespace(NAMESPACE));

        verify(service).queryModel(eq(MODEL), any(), eq("execute"), any());
        verify(cache, never()).lookup(anyString(), anyLong());
        verify(cache, never()).store(anyString(), any(), anyLong(), any(), any());
        Map<String, Object> identity = diagnostic(response, "pivot.cache.identity");
        assertEquals(PivotOuterCacheTelemetry.SUPPLEMENTARY_IDENTITY_PROVIDER_FAILED_STATUS,
                identity.get("status"));
        assertEquals(0, identity.get("bindingCount"));
        assertEquals(true, identity.get("manualTokenPresent"));
        assertEquals(true, identity.get("supplementaryProviderFailed"));
        assertEquals(IllegalStateException.class.getName(),
                identity.get("supplementaryProviderFailureClass"));
        assertEquals(PivotOuterCacheTelemetry.SUPPLEMENTARY_IDENTITY_PROVIDER_FAILED_REASON,
                diagnostic(response, "pivot.cache.refused").get("reason"));
        assertFalse(hasDiagnostic(response, "pivot.cache.lookup"));
    }

    @Test
    void incompleteJdbcEmptyNamespaceAndModelConflictsContinueWithoutCacheIo() {
        QueryModel incompleteModel = queryModel(MODEL);
        assertRefusedButExecuted(
                resolution(incompleteModel, NAMESPACE, "catalog-a", false, true),
                incompleteModel,
                SemanticRequestContext.ofNamespace(NAMESPACE),
                PivotOuterCacheStrongIdentity.STATUS_INCOMPLETE,
                PivotOuterCacheStrongIdentity.REFUSAL_INCOMPLETE);

        JdbcQueryModel jdbcEmpty = mock(JdbcQueryModel.class);
        when(jdbcEmpty.getName()).thenReturn(MODEL);
        when(jdbcEmpty.getPredefinedCalculatedFields()).thenReturn(List.of());
        when(jdbcEmpty.getJdbcModelList()).thenReturn(List.of());
        assertRefusedButExecuted(
                resolution(jdbcEmpty, NAMESPACE, "catalog-a", true, false),
                jdbcEmpty,
                SemanticRequestContext.ofNamespace(NAMESPACE),
                PivotOuterCacheStrongIdentity.STATUS_INCOMPLETE,
                PivotOuterCacheStrongIdentity.REFUSAL_INCOMPLETE);

        QueryModel namespaceModel = queryModel(MODEL);
        assertRefusedButExecuted(
                resolution(namespaceModel, "tenant-b", "catalog-a", true, true),
                namespaceModel,
                SemanticRequestContext.ofNamespace(NAMESPACE),
                PivotOuterCacheStrongIdentity.STATUS_CONFLICT,
                PivotOuterCacheStrongIdentity.REFUSAL_CONFLICT);

        QueryModel modelConflict = mock(QueryModel.class);
        when(modelConflict.getName()).thenReturn(MODEL, MODEL, "OtherQM");
        when(modelConflict.getPredefinedCalculatedFields()).thenReturn(List.of());
        when(modelConflict.getJdbcModelList()).thenReturn(List.of());
        CatalogResolution<QueryModel> conflict =
                resolution(modelConflict, NAMESPACE, "catalog-a", true, true);
        assertRefusedButExecuted(
                conflict,
                modelConflict,
                SemanticRequestContext.ofNamespace(NAMESPACE),
                PivotOuterCacheStrongIdentity.STATUS_CONFLICT,
                PivotOuterCacheStrongIdentity.REFUSAL_CONFLICT);
    }

    @Test
    void midRequestGenerationSwitchFailsBeforeOldKeyCanBeStored() {
        QueryModel model = queryModel(MODEL);
        CatalogResolution<QueryModel> first = resolution(model, NAMESPACE, "catalog-a", true, true);
        CatalogResolution<QueryModel> switched = resolution(model, NAMESPACE, "catalog-b", true, true);
        QueryModelLoader loader = mock(QueryModelLoader.class);
        when(loader.resolveJdbcQueryModel(MODEL, NAMESPACE)).thenReturn(first);
        SemanticQueryServiceV3 service = mock(SemanticQueryServiceV3.class);
        when(service.queryModel(eq(MODEL), any(), eq("execute"), any()))
                .thenAnswer(invocation -> {
                    SemanticRequestContext phaseContext = invocation.getArgument(3);
                    assertSame(first, phaseContext.getCatalogResolution());
                    ModelResultContext executionContext = new ModelResultContext();
                    executionContext.pinCatalogResolution(
                            phaseContext.getCatalogResolution(), phaseContext.getNamespace());
                    executionContext.pinCatalogResolution(switched, phaseContext.getNamespace());
                    return emptyResponse();
                });
        PivotOuterCacheProvider cache = enabledMissCache();
        PivotPipeline pipeline = pipeline(service, loader, cache,
                PivotOuterCacheModelIdentityProvider.empty(),
                new PivotPipeline.OuterCacheOptions(true, 60_000L, 16));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> pipeline.execute(
                        MODEL, request(), SemanticRequestContext.ofNamespace(NAMESPACE)));

        assertEquals("CONFLICTING_CATALOG_REPIN", failure.getMessage());
        verify(cache).lookup(anyString(), anyLong());
        verify(cache, never()).store(anyString(), any(), anyLong(), any(), any());
    }

    @Test
    void directManagedRelationContextCarriesTheSameCatalogPin() {
        QueryModel model = queryModel(MODEL);
        CatalogResolution<QueryModel> resolution = resolution(model, NAMESPACE, "catalog-a", true, true);
        PivotPipeline pipeline = pipeline(
                emptySemanticService(), mock(QueryModelLoader.class), enabledMissCache(),
                PivotOuterCacheModelIdentityProvider.empty(),
                new PivotPipeline.OuterCacheOptions(true, 60_000L, 16));
        SemanticRequestContext requestContext = SemanticRequestContext.ofNamespace(NAMESPACE)
                .withCatalogResolution(resolution);
        SemanticQueryRequest flatRequest = new SemanticQueryRequest();
        flatRequest.setColumns(List.of("region", "amount"));
        flatRequest.setGroupBy(List.of(
                new SemanticQueryRequest.GroupByItem("region", null),
                new SemanticQueryRequest.GroupByItem("amount", "SUM")));
        flatRequest.setLimit(100);

        ModelResultContext resultContext = ReflectionTestUtils.invokeMethod(
                pipeline, "buildManagedRelationContext", MODEL, flatRequest, requestContext);

        assertNotNull(resultContext);
        assertSame(model, resultContext.getQueryModel());
        assertEquals(resolution.catalogIdentity(), resultContext.getCatalogIdentity());
        assertEquals(resolution.dependencyBindings(), resultContext.getDatasourceBindingIdentities());
        assertTrue(resultContext.isBindingIdentityComplete());
    }

    @Test
    void semanticServiceBuildsAResultContextWithTheExactCatalogPin() {
        QueryModel model = queryModel(MODEL);
        CatalogResolution<QueryModel> resolution = resolution(model, NAMESPACE, "catalog-a", true, true);
        SemanticRequestContext requestContext = SemanticRequestContext.ofNamespace(NAMESPACE)
                .withCatalogResolution(resolution);
        DbQueryRequestDef queryDefinition = new DbQueryRequestDef();
        queryDefinition.setQueryModel(MODEL);
        PagingRequest<DbQueryRequestDef> jdbcRequest =
                PagingRequest.buildPagingRequest(queryDefinition, 100);

        ModelResultContext resultContext = ReflectionTestUtils.invokeMethod(
                new SemanticQueryServiceV3Impl(),
                "buildSemanticResultContext",
                jdbcRequest,
                new SemanticQueryRequest(),
                requestContext);

        assertNotNull(resultContext);
        assertSame(model, resultContext.getQueryModel());
        assertEquals(resolution.canonicalName(), resultContext.getCanonicalModelName());
        assertEquals(resolution.catalogIdentity(), resultContext.getCatalogIdentity());
        assertEquals(resolution.dependencyBindings(), resultContext.getDatasourceBindingIdentities());
        assertEquals(resolution.bindingIdentityComplete(), resultContext.isBindingIdentityComplete());
    }

    @Test
    void untrackedEvaluationCannotBypassTheStoreGate() {
        PivotOuterCacheProvider cache = enabledMissCache();
        PivotPipeline pipeline = pipeline(
                emptySemanticService(), mock(QueryModelLoader.class), cache,
                PivotOuterCacheModelIdentityProvider.empty(),
                new PivotPipeline.OuterCacheOptions(true, 60_000L, 16));
        SemanticQueryResponse response = emptyResponse();
        PivotOuterCacheTelemetry.Evaluation untracked =
                new PivotOuterCacheTelemetry.Evaluation("untracked-key", "flat", null);

        pipeline.storeOuterCacheIfEligible(
                untracked,
                PivotOuterCacheTelemetry.CACHE_STAGE,
                new PivotDiagnosticCollector(MODEL),
                response,
                MODEL,
                SemanticRequestContext.ofNamespace(NAMESPACE));

        assertTrue(untracked.refused());
        assertEquals(PivotOuterCacheStrongIdentity.REFUSAL_MISSING, untracked.refusalReason());
        verify(cache, never()).store(anyString(), any(), anyLong(), any(), any());
    }

    private void assertRefusedButExecuted(CatalogResolution<QueryModel> resolution,
                                          QueryModel model,
                                          SemanticRequestContext context,
                                          String expectedStatus,
                                          String expectedReason) {
        QueryModelLoader loader = mock(QueryModelLoader.class);
        when(loader.resolveJdbcQueryModel(MODEL, context.getNamespace())).thenReturn(resolution);
        SemanticQueryServiceV3 service = emptySemanticService();
        PivotOuterCacheProvider cache = enabledMissCache();
        PivotPipeline pipeline = pipeline(service, loader, cache,
                PivotOuterCacheModelIdentityProvider.empty(),
                new PivotPipeline.OuterCacheOptions(true, 60_000L, 16));

        SemanticQueryResponse response = pipeline.execute(MODEL, request(), context);

        ArgumentCaptor<SemanticRequestContext> phaseContext =
                ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(service).queryModel(eq(MODEL), any(), eq("execute"), phaseContext.capture());
        verify(cache, never()).lookup(anyString(), anyLong());
        verify(cache, never()).store(anyString(), any(), anyLong(), any(), any());
        Map<String, Object> identityDiagnostic = diagnostic(response, "pivot.cache.identity");
        assertEquals(expectedStatus, identityDiagnostic.get("status"));
        assertEquals(resolution.dependencyBindings().size(), identityDiagnostic.get("bindingCount"));
        assertEquals(expectedReason, diagnostic(response, "pivot.cache.refused").get("reason"));
        assertFalse(hasDiagnostic(response, "pivot.cache.lookup"));
        if (PivotOuterCacheStrongIdentity.STATUS_INCOMPLETE.equals(expectedStatus)) {
            assertSame(resolution, phaseContext.getValue().getCatalogResolution(),
                    "an incomplete cache identity must still pin the catalog generation");
        } else {
            assertNull(phaseContext.getValue().getCatalogResolution(),
                    "conflicting catalog projections must not be propagated");
        }
        assertNotNull(model);
    }

    private PivotPipeline pipeline(SemanticQueryServiceV3 service,
                                   QueryModelLoader loader,
                                   PivotOuterCacheProvider cache,
                                   PivotOuterCacheModelIdentityProvider identityProvider,
                                   PivotPipeline.OuterCacheOptions options) {
        return new PivotPipeline(
                service, new CardinalityBreaker(), loader, null, options, identityProvider, cache);
    }

    private SemanticQueryServiceV3 emptySemanticService() {
        SemanticQueryServiceV3 service = mock(SemanticQueryServiceV3.class);
        when(service.queryModel(eq(MODEL), any(), eq("execute"), any()))
                .thenAnswer(invocation -> emptyResponse());
        return service;
    }

    private SemanticQueryResponse emptyResponse() {
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(List.of());
        response.setWarnings(List.of());
        return response;
    }

    private PivotOuterCacheProvider enabledMissCache() {
        PivotOuterCacheProvider cache = mock(PivotOuterCacheProvider.class);
        when(cache.isEnabled()).thenReturn(true);
        when(cache.lookup(anyString(), anyLong())).thenReturn(PivotOuterCacheProvider.LookupResult.miss());
        when(cache.name()).thenReturn("test-cache");
        when(cache.ttlMillis()).thenReturn(60_000L);
        when(cache.estimatePayloadBytes(any())).thenReturn(32);
        return cache;
    }

    private QueryModel queryModel(String name) {
        QueryModel model = mock(QueryModel.class);
        when(model.getName()).thenReturn(name);
        when(model.getPredefinedCalculatedFields()).thenReturn(List.of());
        when(model.getJdbcModelList()).thenReturn(List.of());
        return model;
    }

    private CatalogResolution<QueryModel> resolution(QueryModel model,
                                                     String namespace,
                                                     String catalogGeneration,
                                                     boolean complete,
                                                     boolean includeBinding) {
        DatasourceBindingIdentity binding = new DatasourceBindingIdentity(
                "binding:sales",
                "backend:sales",
                new DatasourceBindingGeneration("binding-generation-a"));
        return new CatalogResolution<>(
                MODEL,
                model,
                new CatalogIdentity(
                        namespace,
                        new CatalogGeneration(catalogGeneration),
                        new SourceRevision("source-a")),
                includeBinding ? Map.of(binding.bindingKey(), binding) : Map.of(),
                complete);
    }

    private SemanticQueryRequest request() {
        AxisField row = new AxisField();
        row.setField("region");
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(row));
        pivot.setColumns(List.of());
        pivot.setMetrics(List.of("amount"));
        pivot.setOutputFormat("flat");
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);
        return request;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> diagnostics(SemanticQueryResponse response) {
        return (List<Map<String, Object>>) response.getDebug().getExtra().get("pivotDiagnostics");
    }

    private Map<String, Object> diagnostic(SemanticQueryResponse response, String event) {
        return diagnostics(response).stream()
                .filter(item -> event.equals(item.get("event")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing diagnostic event " + event));
    }

    private boolean hasDiagnostic(SemanticQueryResponse response, String event) {
        return diagnostics(response).stream().anyMatch(item -> event.equals(item.get("event")));
    }
}
