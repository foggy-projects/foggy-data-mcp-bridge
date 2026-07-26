package com.foggyframework.dataset.mcp.spi.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogAdmissionBlockedException;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService.NamespaceCatalogView;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataset.mcp.config.McpProperties;
import com.foggyframework.dataset.mcp.service.ModelCatalogService;
import com.foggyframework.dataset.mcp.spi.SemanticServiceResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Contract tests for the single namespace catalog authority shared by model and MCP consumers. */
class CatalogNamespaceAuthorityTest {

    @Test
    void modelResolverAndMcpCatalogUseOneIdentityBoundNamespaceView() {
        NamespaceCatalogView tenantA = view("tenant-a", "a-g1", "a-r1", "AlphaModel", "AM");
        NamespaceCatalogView tenantB = view("tenant-b", "b-g1", "b-r1", "BetaModel", "BM");
        AtomicReference<Map<String, NamespaceCatalogView>> published =
                new AtomicReference<>(Map.of("tenant-a", tenantA, "tenant-b", tenantB));
        SemanticModelCatalogService authority = authority(published);
        QueryModelLoader loader = mock(QueryModelLoader.class);
        SemanticServiceResolverImpl resolver = resolver(authority, loader);
        ModelCatalogService mcpCatalog = mcpCatalog(authority, loader);

        assertLifecycleIdentity(
                authority.namespaceCatalogView("tenant-a"), tenantA);
        assertThat(resolver.getAllModelNames("tenant-a")).containsExactly("AlphaModel");
        assertCatalog(mcpCatalog, "tenant-a", tenantA);

        assertLifecycleIdentity(
                authority.namespaceCatalogView("tenant-b"), tenantB);
        assertThat(resolver.getAllModelNames("tenant-b")).containsExactly("BetaModel");
        assertCatalog(mcpCatalog, "tenant-b", tenantB);

        verify(loader, never()).getJdbcQueryModel(anyString(), anyString());
        verify(loader, never()).loadJdbcQueryModel(any());
    }

    @Test
    void publishingTenantAAtomicallySwitchesAllConsumersAndLeavesTenantBUnchanged() {
        NamespaceCatalogView tenantAOld = view("tenant-a", "a-g1", "a-r1", "AlphaOld", "AO");
        NamespaceCatalogView tenantANew = view("tenant-a", "a-g2", "a-r2", "AlphaNew", "AN");
        NamespaceCatalogView tenantB = view("tenant-b", "b-g1", "b-r1", "BetaModel", "BM");
        AtomicReference<Map<String, NamespaceCatalogView>> published =
                new AtomicReference<>(Map.of("tenant-a", tenantAOld, "tenant-b", tenantB));
        SemanticModelCatalogService authority = authority(published);
        QueryModelLoader loader = mock(QueryModelLoader.class);
        SemanticServiceResolverImpl resolver = resolver(authority, loader);
        ModelCatalogService mcpCatalog = mcpCatalog(authority, loader);

        assertThat(resolver.getAllModelNames("tenant-a")).containsExactly("AlphaOld");
        assertCatalog(mcpCatalog, "tenant-a", tenantAOld);
        CatalogIdentity tenantBIdentityBefore =
                authority.namespaceCatalogView("tenant-b").identity();
        assertCatalog(mcpCatalog, "tenant-b", tenantB);

        published.set(Map.of("tenant-a", tenantANew, "tenant-b", tenantB));

        NamespaceCatalogView modelViewAfter = authority.namespaceCatalogView("tenant-a");
        assertLifecycleIdentity(modelViewAfter, tenantANew);
        assertThat(modelViewAfter.identity()).isNotEqualTo(tenantAOld.identity());
        assertThat(bindingIdentities(modelViewAfter))
                .doesNotContainAnyElementsOf(bindingIdentities(tenantAOld));
        assertThat(resolver.getAllModelNames("tenant-a")).containsExactly("AlphaNew");
        assertCatalog(mcpCatalog, "tenant-a", tenantANew);
        assertThat(authority.namespaceCatalogView("tenant-b").identity())
                .isEqualTo(tenantBIdentityBefore);
        assertThat(resolver.getAllModelNames("tenant-b")).containsExactly("BetaModel");
        assertCatalog(mcpCatalog, "tenant-b", tenantB);

        verify(loader, never()).getJdbcQueryModel(anyString(), anyString());
        verify(loader, never()).loadJdbcQueryModel(any());
    }

    @Test
    void metadataPublicationRetriesTheWholeCatalogToOneNewGeneration() {
        NamespaceCatalogView tenantAOld = view(
                "tenant-a", "a-g1", "a-r1", "a-b1", "AlphaOld", "AO");
        NamespaceCatalogView tenantANew = view(
                "tenant-a", "a-g2", "a-r2", "a-b2", "AlphaNew", "AN");
        AtomicReference<Map<String, NamespaceCatalogView>> published =
                new AtomicReference<>(Map.of("tenant-a", tenantAOld));
        SemanticModelCatalogService authority = authority(published);
        QueryModelLoader loader = mock(QueryModelLoader.class);
        SemanticServiceResolver metadataResolver = mock(SemanticServiceResolver.class);
        AtomicInteger metadataCalls = new AtomicInteger();
        when(metadataResolver.getMetadata(any(), eq("json"), any()))
                .thenAnswer(invocation -> {
                    if (metadataCalls.incrementAndGet() == 1) {
                        published.set(Map.of("tenant-a", tenantANew));
                    }
                    return emptyMetadata();
                });
        ModelCatalogService mcpCatalog = mcpCatalog(
                authority, loader, metadataResolver);

        assertCatalog(mcpCatalog, "tenant-a", tenantANew);

        assertThat(metadataCalls).hasValue(2);
        verify(authority, times(3)).namespaceCatalogView("tenant-a");
        verify(loader, never()).getJdbcQueryModel(anyString(), anyString());
    }

    @Test
    void continuouslyChangingMetadataGenerationFailsClosedAfterBoundedRetries() {
        NamespaceCatalogView generation0 = view(
                "tenant-a", "a-g0", "a-r0", "a-b0", "Alpha0", "A0");
        NamespaceCatalogView generation1 = view(
                "tenant-a", "a-g1", "a-r1", "a-b1", "Alpha1", "A1");
        NamespaceCatalogView generation2 = view(
                "tenant-a", "a-g2", "a-r2", "a-b2", "Alpha2", "A2");
        NamespaceCatalogView generation3 = view(
                "tenant-a", "a-g3", "a-r3", "a-b3", "Alpha3", "A3");
        List<NamespaceCatalogView> generations = List.of(
                generation0, generation1, generation2, generation3);
        AtomicReference<Map<String, NamespaceCatalogView>> published =
                new AtomicReference<>(Map.of("tenant-a", generation0));
        SemanticModelCatalogService authority = authority(published);
        QueryModelLoader loader = mock(QueryModelLoader.class);
        SemanticServiceResolver metadataResolver = mock(SemanticServiceResolver.class);
        AtomicInteger metadataCalls = new AtomicInteger();
        when(metadataResolver.getMetadata(any(), eq("json"), any()))
                .thenAnswer(invocation -> {
                    int call = metadataCalls.incrementAndGet();
                    published.set(Map.of("tenant-a", generations.get(call)));
                    return emptyMetadata();
                });
        ModelCatalogService mcpCatalog = mcpCatalog(
                authority, loader, metadataResolver);

        assertThatThrownBy(() -> mcpCatalog.buildCatalogResponse(
                Map.of("format", "json", "fieldLimit", 0), "tenant-a", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CATALOG_VIEW_STALE_RETRY_EXHAUSTED")
                .hasMessageContaining("tenant-a");

        assertThat(metadataCalls).hasValue(3);
        verify(authority, times(4)).namespaceCatalogView("tenant-a");
        verify(loader, never()).getJdbcQueryModel(anyString(), anyString());
    }

    @Test
    void admissionBlockObservedAfterMetadataIsNotDowngradedToAnOldCatalog() {
        NamespaceCatalogView tenantAOld = view(
                "tenant-a", "a-g1", "a-r1", "a-b1", "AlphaOld", "AO");
        CatalogAdmissionBlockedException blocked =
                new CatalogAdmissionBlockedException(
                        "tenant-a", "REFRESH_SCOPE_UNKNOWN: controlled test block");
        SemanticModelCatalogService authority = mock(
                SemanticModelCatalogService.class);
        when(authority.namespaceCatalogView("tenant-a"))
                .thenReturn(tenantAOld)
                .thenThrow(blocked);
        QueryModelLoader loader = mock(QueryModelLoader.class);
        ModelCatalogService mcpCatalog = mcpCatalog(authority, loader);

        assertThatThrownBy(() -> mcpCatalog.buildCatalogResponse(
                Map.of("format", "json", "fieldLimit", 0), "tenant-a", null))
                .isSameAs(blocked);

        verify(authority, times(2)).namespaceCatalogView("tenant-a");
        verify(loader, never()).getJdbcQueryModel(anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void configuredDefaultCatalogUsesOnlyConfiguredModelView() {
        NamespaceCatalogView configured =
                view("", "default-g1", "default-r1", "AlphaModel", "AM");
        SemanticModelCatalogService authority =
                mock(SemanticModelCatalogService.class);
        when(authority.modelCatalogView(
                "", List.of("AlphaModel")))
                .thenReturn(configured);
        QueryModelLoader loader = mock(QueryModelLoader.class);
        SemanticServiceResolver metadataResolver =
                mock(SemanticServiceResolver.class);
        when(metadataResolver.getMetadata(any(), eq("json"), any()))
                .thenReturn(emptyMetadata());
        McpProperties properties = new McpProperties();
        properties.getSemantic().setUseAllModels(null);
        properties.getSemantic().setModelList(List.of("AlphaModel"));
        ModelCatalogService catalog = new ModelCatalogService(
                metadataResolver,
                loader,
                new ObjectMapper(),
                properties,
                authority);

        Map<String, Object> data = (Map<String, Object>) catalog
                .buildCatalogResponse(
                        Map.of("format", "json", "fieldLimit", 0),
                        "",
                        null)
                .get("data");

        assertThat((List<String>) data.get("models"))
                .containsExactly("AlphaModel");
        verify(authority, times(2)).modelCatalogView(
                "", List.of("AlphaModel"));
        verify(authority, never()).namespaceCatalogView(anyString());
        verify(loader, never()).getJdbcQueryModel(anyString(), anyString());
    }

    private static SemanticModelCatalogService authority(
            AtomicReference<Map<String, NamespaceCatalogView>> published
    ) {
        SemanticModelCatalogService authority =
                mock(SemanticModelCatalogService.class, CALLS_REAL_METHODS);
        doAnswer(invocation -> {
            String namespace = invocation.getArgument(0);
            NamespaceCatalogView view = published.get().get(namespace);
            if (view == null) {
                throw new IllegalStateException("missing test namespace view: " + namespace);
            }
            return view;
        }).when(authority).namespaceCatalogView(anyString());
        return authority;
    }

    private static SemanticServiceResolverImpl resolver(
            SemanticModelCatalogService authority,
            QueryModelLoader loader
    ) {
        return new SemanticServiceResolverImpl(
                mock(SemanticServiceV3.class),
                mock(SemanticQueryServiceV3.class),
                mock(SystemBundlesContext.class),
                loader,
                authority);
    }

    private static ModelCatalogService mcpCatalog(
            SemanticModelCatalogService authority,
            QueryModelLoader loader
    ) {
        SemanticServiceResolver metadataResolver = mock(SemanticServiceResolver.class);
        when(metadataResolver.getMetadata(any(), eq("json"), any()))
                .thenReturn(emptyMetadata());
        return mcpCatalog(authority, loader, metadataResolver);
    }

    private static ModelCatalogService mcpCatalog(
            SemanticModelCatalogService authority,
            QueryModelLoader loader,
            SemanticServiceResolver metadataResolver
    ) {
        McpProperties properties = new McpProperties();
        properties.getSemantic().setUseAllModels(true);
        return new ModelCatalogService(
                metadataResolver,
                loader,
                new ObjectMapper(),
                properties,
                authority);
    }

    @SuppressWarnings("unchecked")
    private static void assertCatalog(
            ModelCatalogService catalogService,
            String namespace,
            NamespaceCatalogView expected
    ) {
        Map<String, Object> catalog = (Map<String, Object>) catalogService
                .buildCatalogResponse(Map.of("format", "json", "fieldLimit", 0), namespace, null)
                .get("data");
        assertThat((List<String>) catalog.get("models"))
                .containsExactlyElementsOf(expected.modelNames());
        List<Map<String, Object>> items = (List<Map<String, Object>>) catalog.get("items");
        assertThat(items).hasSize(expected.modelNames().size());
        for (Map<String, Object> item : items) {
            String modelName = (String) item.get("model");
            assertThat(item.get("shortAlias"))
                    .isEqualTo(expected.aliasesByModel().get(modelName));
            assertThat(item.get("caption"))
                    .isEqualTo(modelName + " caption");
            assertThat(expected.queryModels().get(modelName)).isNotNull();
            CatalogResolution<QueryModel> resolution = expected
                    .resolutionsByModel().get(modelName);
            assertThat(resolution.catalogIdentity()).isEqualTo(expected.identity());
            assertThat(resolution.model())
                    .isSameAs(expected.queryModels().get(modelName));
            assertThat(resolution.dependencyBindings()).hasSize(1);
            assertThat(resolution.bindingIdentityComplete()).isTrue();
        }
    }

    private static NamespaceCatalogView view(
            String namespace,
            String generation,
            String sourceRevision,
            String bindingGeneration,
            String modelName,
            String alias
    ) {
        QueryModel model = mock(QueryModel.class);
        when(model.getName()).thenReturn(modelName);
        when(model.getShortAlias()).thenReturn(alias);
        when(model.getCaption()).thenReturn(modelName + " caption");
        CatalogIdentity identity = new CatalogIdentity(
                namespace,
                new CatalogGeneration(generation),
                new SourceRevision(sourceRevision));
        String bindingKey = namespace + ":primary";
        DatasourceBindingIdentity binding = new DatasourceBindingIdentity(
                bindingKey,
                "jdbc",
                new DatasourceBindingGeneration(bindingGeneration));
        CatalogResolution<QueryModel> resolution = new CatalogResolution<>(
                modelName,
                model,
                identity,
                Map.of(bindingKey, binding),
                true);
        return new NamespaceCatalogView(
                identity,
                List.of(modelName),
                Map.of(modelName, alias),
                Map.of(modelName, model),
                Map.of(modelName, resolution));
    }

    private static void assertLifecycleIdentity(
            NamespaceCatalogView actual,
            NamespaceCatalogView expected
    ) {
        assertThat(actual.identity()).isEqualTo(expected.identity());
        assertThat(actual.resolutionsByModel()).containsOnlyKeys(
                expected.modelNames().toArray(String[]::new));
        expected.modelNames().forEach(modelName -> {
            CatalogResolution<QueryModel> actualResolution = actual
                    .resolutionsByModel().get(modelName);
            CatalogResolution<QueryModel> expectedResolution = expected
                    .resolutionsByModel().get(modelName);
            assertThat(actualResolution.catalogIdentity())
                    .isEqualTo(expectedResolution.catalogIdentity());
            assertThat(actualResolution.dependencyBindings())
                    .containsExactlyInAnyOrderEntriesOf(
                            expectedResolution.dependencyBindings());
            assertThat(actualResolution.bindingIdentityComplete())
                    .isEqualTo(expectedResolution.bindingIdentityComplete());
        });
    }

    private static List<DatasourceBindingIdentity> bindingIdentities(
            NamespaceCatalogView view
    ) {
        return view.resolutionsByModel().values().stream()
                .flatMap(resolution -> resolution.dependencyBindings()
                        .values().stream())
                .toList();
    }

    private static NamespaceCatalogView view(
            String namespace,
            String generation,
            String sourceRevision,
            String modelName,
            String alias
    ) {
        return view(namespace, generation, sourceRevision,
                generation + "-binding", modelName, alias);
    }

    private static SemanticMetadataResponse emptyMetadata() {
        SemanticMetadataResponse metadata = new SemanticMetadataResponse();
        metadata.setData(Map.of("fields", Map.of(), "models", Map.of()));
        return metadata;
    }
}
