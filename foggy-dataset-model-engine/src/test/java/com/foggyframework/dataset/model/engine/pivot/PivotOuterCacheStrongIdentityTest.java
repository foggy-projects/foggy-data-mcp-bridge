package com.foggyframework.dataset.model.engine.pivot;

import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.permission.AuthorizationSignature;
import com.foggyframework.dataset.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.spi.QueryModel;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PivotOuterCacheStrongIdentityTest {

    @Test
    void completeIdentityUsesFullSha256AndSortsExactDatasourceBindings() {
        QueryModel model = queryModel("SalesQM");
        DatasourceBindingIdentity alpha = binding("binding:alpha", "backend:a", "gen:a");
        DatasourceBindingIdentity beta = binding("binding:beta", "backend:b", "gen:b");

        Map<String, DatasourceBindingIdentity> alphaThenBeta = new LinkedHashMap<>();
        alphaThenBeta.put(alpha.bindingKey(), alpha);
        alphaThenBeta.put(beta.bindingKey(), beta);
        Map<String, DatasourceBindingIdentity> betaThenAlpha = new LinkedHashMap<>();
        betaThenAlpha.put(beta.bindingKey(), beta);
        betaThenAlpha.put(alpha.bindingKey(), alpha);

        PivotOuterCacheStrongIdentity.Assessment first = PivotOuterCacheStrongIdentity.assess(
                resolution(model, "ns-a", "catalog-1", "source-1", alphaThenBeta, true), "ns-a");
        PivotOuterCacheStrongIdentity.Assessment reordered = PivotOuterCacheStrongIdentity.assess(
                resolution(model, "ns-a", "catalog-1", "source-1", betaThenAlpha, true), "ns-a");

        assertTrue(first.cacheable());
        assertEquals(PivotOuterCacheStrongIdentity.STATUS_COMPLETE, first.status());
        assertEquals(2, first.bindingCount());
        assertEquals(64, first.identityHash().length());
        assertEquals(first.identityHash(), reordered.identityHash(),
                "map insertion order must not change the lifecycle identity");

        PivotOuterCacheTelemetry.Evaluation evaluation = evaluation(first,
                PivotOuterCacheModelIdentity.empty(), "", "");
        assertTrue(evaluation.keyHash().startsWith("v3:"));
        assertEquals(67, evaluation.keyHash().length(), "cache key must retain the full SHA-256");
        assertFalse(evaluation.refused());
        assertEquals(first.identityHash(), evaluation.identityHash());
    }

    @Test
    void everyCatalogAndBindingIdentityComponentChangesTheHash() {
        QueryModel model = queryModel("SalesQM");
        DatasourceBindingIdentity baseBinding = binding("binding:a", "backend:a", "binding-gen:a");
        String base = identityHash(model, "ns-a", "catalog-a", "source-a", baseBinding);

        assertNotEquals(base, identityHash(model, "ns-b", "catalog-a", "source-a", baseBinding));
        assertNotEquals(base, identityHash(model, "ns-a", "catalog-b", "source-a", baseBinding));
        assertNotEquals(base, identityHash(model, "ns-a", "catalog-a", "source-b", baseBinding));
        assertNotEquals(base, identityHash(model, "ns-a", "catalog-a", "source-a",
                binding("binding:b", "backend:a", "binding-gen:a")));
        assertNotEquals(base, identityHash(model, "ns-a", "catalog-a", "source-a",
                binding("binding:a", "backend:b", "binding-gen:a")));
        assertNotEquals(base, identityHash(model, "ns-a", "catalog-a", "source-a",
                binding("binding:a", "backend:a", "binding-gen:b")));
    }

    @Test
    void supplementaryProviderAndManualTokensAreIndependentAndCannotRescueMissingLifecycle() {
        QueryModel model = queryModel("SalesQM");
        DatasourceBindingIdentity binding = binding("binding:a", "backend:a", "gen:a");
        PivotOuterCacheStrongIdentity.Assessment complete = PivotOuterCacheStrongIdentity.assess(
                resolution(model, "ns-a", "catalog-a", "source-a",
                        Map.of(binding.bindingKey(), binding), true), "ns-a");
        PivotOuterCacheTelemetry.Evaluation baseline = evaluation(
                complete, new PivotOuterCacheModelIdentity("provider-a", "provider-fresh-a"),
                "manual-a", "manual-fresh-a");
        PivotOuterCacheTelemetry.Evaluation providerChanged = evaluation(
                complete, new PivotOuterCacheModelIdentity("provider-b", "provider-fresh-a"),
                "manual-a", "manual-fresh-a");
        PivotOuterCacheTelemetry.Evaluation manualChanged = evaluation(
                complete, new PivotOuterCacheModelIdentity("provider-a", "provider-fresh-a"),
                "manual-b", "manual-fresh-a");

        assertFalse(baseline.refused());
        assertNotEquals(baseline.keyHash(), providerChanged.keyHash());
        assertNotEquals(baseline.keyHash(), manualChanged.keyHash());

        PivotOuterCacheStrongIdentity.Assessment missing =
                PivotOuterCacheStrongIdentity.assess(null, "ns-a");

        PivotOuterCacheTelemetry.Evaluation providerOnly = evaluation(
                missing, new PivotOuterCacheModelIdentity("provider-bundle", "provider-freshness"), "", "");
        PivotOuterCacheTelemetry.Evaluation manualOnly = evaluation(
                missing, PivotOuterCacheModelIdentity.empty(), "manual-bundle", "manual-freshness");
        PivotOuterCacheTelemetry.Evaluation both = evaluation(
                missing,
                new PivotOuterCacheModelIdentity("provider-bundle", "provider-freshness"),
                "manual-bundle", "manual-freshness");

        assertEquals(PivotOuterCacheStrongIdentity.REFUSAL_MISSING, providerOnly.refusalReason());
        assertEquals(PivotOuterCacheStrongIdentity.REFUSAL_MISSING, manualOnly.refusalReason());
        assertEquals(PivotOuterCacheStrongIdentity.REFUSAL_MISSING, both.refusalReason());
        assertNull(both.identityHash());
        assertTrue(manualOnly.manualTokenPresent());
        assertTrue(both.manualTokenPresent());
        assertNotEquals(providerOnly.keyHash(), manualOnly.keyHash());
        assertNotEquals(manualOnly.keyHash(), both.keyHash(),
                "provider and manual tokens must remain additive instead of first-nonblank alternatives");
    }

    @Test
    void incompleteJdbcEmptyNamespaceAndModelConflictsAreRefused() {
        QueryModel incompleteModel = queryModel("SalesQM");
        DatasourceBindingIdentity binding = binding("binding:a", "backend:a", "gen:a");
        PivotOuterCacheStrongIdentity.Assessment incomplete = PivotOuterCacheStrongIdentity.assess(
                resolution(incompleteModel, "ns-a", "catalog-a", "source-a",
                        Map.of(binding.bindingKey(), binding), false), "ns-a");

        JdbcQueryModel jdbcModel = mock(JdbcQueryModel.class);
        when(jdbcModel.getName()).thenReturn("SalesQM");
        PivotOuterCacheStrongIdentity.Assessment jdbcEmpty = PivotOuterCacheStrongIdentity.assess(
                resolution(jdbcModel, "ns-a", "catalog-a", "source-a", Map.of(), true), "ns-a");

        QueryModel namespaceModel = queryModel("SalesQM");
        PivotOuterCacheStrongIdentity.Assessment namespaceConflict = PivotOuterCacheStrongIdentity.assess(
                resolution(namespaceModel, "ns-b", "catalog-a", "source-a",
                        Map.of(binding.bindingKey(), binding), true), "ns-a");

        QueryModel modelConflict = mock(QueryModel.class);
        when(modelConflict.getName()).thenReturn("SalesQM", "SalesQM", "OtherQM");
        CatalogResolution<QueryModel> conflictResolution = resolution(
                modelConflict, "ns-a", "catalog-a", "source-a",
                Map.of(binding.bindingKey(), binding), true);
        PivotOuterCacheStrongIdentity.Assessment conflictingModel =
                PivotOuterCacheStrongIdentity.assess(conflictResolution, "ns-a");

        assertRefused(incomplete, PivotOuterCacheStrongIdentity.STATUS_INCOMPLETE,
                PivotOuterCacheStrongIdentity.REFUSAL_INCOMPLETE);
        assertRefused(jdbcEmpty, PivotOuterCacheStrongIdentity.STATUS_INCOMPLETE,
                PivotOuterCacheStrongIdentity.REFUSAL_INCOMPLETE);
        assertRefused(namespaceConflict, PivotOuterCacheStrongIdentity.STATUS_CONFLICT,
                PivotOuterCacheStrongIdentity.REFUSAL_CONFLICT);
        assertRefused(conflictingModel, PivotOuterCacheStrongIdentity.STATUS_CONFLICT,
                PivotOuterCacheStrongIdentity.REFUSAL_CONFLICT);
        assertTrue(incomplete.pinnable());
        assertTrue(jdbcEmpty.pinnable());
        assertFalse(namespaceConflict.pinnable());
        assertFalse(conflictingModel.pinnable());
        assertEquals(1, incomplete.bindingCount());
        assertEquals(0, jdbcEmpty.bindingCount());
        assertEquals(1, namespaceConflict.bindingCount());
        assertEquals(1, conflictingModel.bindingCount());
    }

    private void assertRefused(PivotOuterCacheStrongIdentity.Assessment assessment,
                               String status,
                               String refusalReason) {
        assertFalse(assessment.cacheable());
        assertEquals(status, assessment.status());
        assertEquals(refusalReason, assessment.refusalReason());
        assertNull(assessment.identityHash());
    }

    private String identityHash(QueryModel model,
                                String namespace,
                                String catalogGeneration,
                                String sourceRevision,
                                DatasourceBindingIdentity binding) {
        return PivotOuterCacheStrongIdentity.assess(
                resolution(model, namespace, catalogGeneration, sourceRevision,
                        Map.of(binding.bindingKey(), binding), true), namespace).identityHash();
    }

    private PivotOuterCacheTelemetry.Evaluation evaluation(
            PivotOuterCacheStrongIdentity.Assessment assessment,
            PivotOuterCacheModelIdentity providerIdentity,
            String manualBundle,
            String manualFreshness) {
        return PivotOuterCacheTelemetry.evaluate(
                "SalesQM", queryModel("SalesQM"), request(), SemanticRequestContext.ofNamespace("ns-a"),
                false, false, PivotOuterCacheTelemetry.CACHE_STAGE,
                PivotOuterCacheTelemetry.ModelIdentity.from(
                        assessment, providerIdentity, manualBundle, manualFreshness),
                new AuthorizationSignature("PUBLIC:test", true, null));
    }

    private CatalogResolution<QueryModel> resolution(QueryModel model,
                                                     String namespace,
                                                     String catalogGeneration,
                                                     String sourceRevision,
                                                     Map<String, DatasourceBindingIdentity> bindings,
                                                     boolean complete) {
        return new CatalogResolution<>(
                "SalesQM",
                model,
                new CatalogIdentity(namespace,
                        new CatalogGeneration(catalogGeneration),
                        new SourceRevision(sourceRevision)),
                bindings,
                complete);
    }

    private QueryModel queryModel(String name) {
        QueryModel model = mock(QueryModel.class);
        when(model.getName()).thenReturn(name);
        when(model.getPredefinedCalculatedFields()).thenReturn(List.of());
        when(model.getJdbcModelList()).thenReturn(List.of());
        return model;
    }

    private DatasourceBindingIdentity binding(String key, String backend, String generation) {
        return new DatasourceBindingIdentity(
                key, backend, new DatasourceBindingGeneration(generation));
    }

    private SemanticQueryRequest request() {
        AxisField row = new AxisField();
        row.setField("region");
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(row));
        pivot.setMetrics(List.of("amount"));
        pivot.setOutputFormat("flat");
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);
        return request;
    }
}
