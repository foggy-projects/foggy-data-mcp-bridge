package com.foggyframework.dataset.model.semantic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.engine.query_model.QueryModelLoaderImpl;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogAdmissionBlockedException;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogBuildView;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogCandidate;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshCoordinator;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshRequest;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshScope;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshTrigger;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.model.semantic.support.SemanticQueryPayloadMapper;
import com.foggyframework.dataset.model.spi.NamespaceContext;
import com.foggyframework.dataset.model.spi.NamespaceScope;
import com.foggyframework.dataset.model.spi.QueryModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SemanticModelCatalogServiceTest {

    @Test
    void noArgDiscoveryInheritsScopeWhileExplicitEmptyUsesDefaultNamespace() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        publishExact(store, "tenant-a", versions("TenantAModel", "a1"));
        publishExact(store, "tenant-b", versions("TenantBModel", "b1"));
        publishExact(store, "", versions("DefaultModel", "d1"));
        Fixture fixture = fixture(store);

        try (NamespaceScope ignored = NamespaceContext.open("tenant-a")) {
            assertThat(fixture.service().getAllModelNames())
                    .containsExactly("TenantAModel");
            assertThat(fixture.service().getAllModelNames(""))
                    .as("explicit empty namespace must hide an outer named scope")
                    .containsExactly("DefaultModel");
        }
        try (NamespaceScope ignored = NamespaceContext.open("tenant-b")) {
            assertThat(fixture.service().getAllModelNames())
                    .containsExactly("TenantBModel");
        }

        assertThat(NamespaceContext.getNamespace()).isNull();
        verifyNoInteractions(fixture.coordinator());
    }

    @Test
    void namespaceViewPinsIdentityAliasesModelsAndExactBindingsAcrossNamespacesAndGeneration() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        publishExact(store, "tenant-a", versions("SharedModel", "a1"));
        publishExact(store, "tenant-b", versions("SharedModel", "b1"));
        Fixture fixture = fixture(store);

        SemanticModelCatalogService.NamespaceCatalogView a1 =
                fixture.service().namespaceCatalogView("tenant-a");
        SemanticModelCatalogService.NamespaceCatalogView b1 =
                fixture.service().namespaceCatalogView("tenant-b");
        DatasourceBindingIdentity a1Binding = binding("tenant-a", "a1");
        DatasourceBindingIdentity b1Binding = binding("tenant-b", "b1");

        assertCompleteTrackedView(a1, store.readCurrent("tenant-a").orElseThrow());
        assertCompleteTrackedView(b1, store.readCurrent("tenant-b").orElseThrow());
        assertThat(a1.resolutionsByModel().get("SharedModel").dependencyBindings())
                .containsExactlyEntriesOf(Map.of(a1Binding.bindingKey(), a1Binding));
        assertThat(b1.resolutionsByModel().get("SharedModel").dependencyBindings())
                .containsExactlyEntriesOf(Map.of(b1Binding.bindingKey(), b1Binding));

        publishExact(store, "tenant-a", versions("SharedModel", "a2"));
        SemanticModelCatalogService.NamespaceCatalogView a2 =
                fixture.service().namespaceCatalogView("tenant-a");
        SemanticModelCatalogService.NamespaceCatalogView bStill1 =
                fixture.service().namespaceCatalogView("tenant-b");
        DatasourceBindingIdentity a2Binding = binding("tenant-a", "a2");

        assertThat(a2.identity()).isNotEqualTo(a1.identity());
        assertThat(a2.resolutionsByModel().get("SharedModel").dependencyBindings())
                .containsExactlyEntriesOf(Map.of(a2Binding.bindingKey(), a2Binding));
        assertThat(a2.resolutionsByModel().get("SharedModel").bindingIdentityComplete())
                .isTrue();
        assertThat(bStill1.identity()).isEqualTo(b1.identity());
        assertThat(bStill1.resolutionsByModel().get("SharedModel").dependencyBindings())
                .containsExactlyEntriesOf(Map.of(b1Binding.bindingKey(), b1Binding));
        verifyNoInteractions(fixture.coordinator());
    }

    @Test
    void coldAndIncompleteCatalogsRecoverOnlyThroughCoordinator() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogSnapshot incomplete = publishIncomplete(
                store, "tenant-incomplete", Set.of("RecoveredModel"));
        Fixture fixture = fixture(store);
        when(fixture.coordinator().refresh(any(CatalogRefreshRequest.class)))
                .thenAnswer(invocation -> {
                    CatalogRefreshRequest request = invocation.getArgument(0);
                    Map<String, ModelVersion> recoveredVersions =
                            request.namespace().equals("tenant-cold")
                                    ? Map.of()
                                    : versions("RecoveredModel", "recovered1");
                    publishExact(store, request.namespace(), recoveredVersions);
                    return null;
                });

        SemanticModelCatalogService.NamespaceCatalogView cold =
                fixture.service().namespaceCatalogView("tenant-cold");
        SemanticModelCatalogService.NamespaceCatalogView recovered =
                fixture.service().namespaceCatalogView("tenant-incomplete");
        fixture.service().namespaceCatalogView("tenant-cold");

        assertThat(cold.identity()).isNotNull();
        assertThat(cold.modelNames()).isEmpty();
        assertThat(cold.aliasesByModel()).isEmpty();
        assertThat(cold.queryModels()).isEmpty();
        assertThat(cold.resolutionsByModel()).isEmpty();
        assertThat(recovered.identity()).isNotEqualTo(incomplete.identity());
        assertThat(recovered.modelNames()).containsExactly("RecoveredModel");
        ArgumentCaptor<CatalogRefreshRequest> requests =
                ArgumentCaptor.forClass(CatalogRefreshRequest.class);
        verify(fixture.coordinator(), times(2)).refresh(requests.capture());
        assertThat(requests.getAllValues())
                .extracting(CatalogRefreshRequest::namespace)
                .containsExactly("tenant-cold", "tenant-incomplete");
        assertThat(requests.getAllValues())
                .allSatisfy(request -> {
                    assertThat(request.scope()).isEqualTo(CatalogRefreshScope.NAMESPACE);
                    assertThat(request.trigger()).isEqualTo(
                            CatalogRefreshTrigger.EXPLICIT_RECOVERY);
                });
        verifyNoInteractions(fixture.loader());
    }

    @Test
    void recoveryFailurePreservesPriorSnapshotAndNeverReturnsPartialView() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogSnapshot prior = publishIncomplete(
                store, "tenant-a", Set.of("PartialModel"));
        Fixture fixture = fixture(store);
        IllegalStateException marker = new IllegalStateException(
                "controlled recovery failure");
        when(fixture.coordinator().refresh(any(CatalogRefreshRequest.class)))
                .thenThrow(marker);

        assertSame(marker, assertThrows(IllegalStateException.class,
                () -> fixture.service().namespaceCatalogView("tenant-a")));

        assertThat(store.current("tenant-a").orElseThrow()).isSameAs(prior);
        assertThat(store.current("tenant-a").orElseThrow().queryModels()).isEmpty();
        verify(fixture.coordinator(), times(1)).refresh(any(CatalogRefreshRequest.class));
        verifyNoInteractions(fixture.loader());
    }

    @Test
    void exactEmptySnapshotAfterDeletionDoesNotRetainOldNames() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogSnapshot before = publishExact(
                store, "tenant-a", versions("DeletedModel", "before-delete"));
        CatalogSnapshot after = publishExact(store, "tenant-a", Map.of());
        Fixture fixture = fixture(store);

        SemanticModelCatalogService.NamespaceCatalogView view =
                fixture.service().namespaceCatalogView("tenant-a");

        assertThat(after.identity()).isNotEqualTo(before.identity());
        assertThat(view.identity()).isEqualTo(after.identity());
        assertThat(view.modelNames()).isEmpty();
        assertThat(view.aliasesByModel()).isEmpty();
        assertThat(view.queryModels()).isEmpty();
        assertThat(view.resolutionsByModel()).isEmpty();
        verifyNoInteractions(fixture.coordinator());
    }

    @Test
    void blockedAdmissionFailsBeforeRecoveryCoordinator() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        publishExact(store, "tenant-a", versions("AlphaModel", "a1"));
        store.markStaleAdmissionBlocked("tenant-a", "controlled unknown scope");
        Fixture fixture = fixture(store);

        assertThrows(CatalogAdmissionBlockedException.class,
                () -> fixture.service().namespaceCatalogView("tenant-a"));

        verifyNoInteractions(fixture.coordinator(), fixture.loader());
    }

    @Test
    void trackedViewFailsClosedWhenQueryProvenanceIsAbsent() {
        CatalogSnapshotStore store = mock(CatalogSnapshotStore.class);
        CatalogSnapshot snapshot = mock(CatalogSnapshot.class);
        QueryModel model = mock(QueryModel.class);
        CatalogSnapshot identitySource = publishExact(
                new CatalogSnapshotStore(), "tenant-a",
                versions("IdentityModel", "identity"));
        when(store.readCurrent("tenant-a")).thenReturn(Optional.of(snapshot));
        when(snapshot.identity()).thenReturn(identitySource.identity());
        when(snapshot.discoveredQueryModelNames()).thenReturn(Set.of("AlphaModel"));
        when(snapshot.queryModels()).thenReturn(Map.of("AlphaModel", model));
        when(snapshot.syntheticQueryModels()).thenReturn(Map.of());
        when(snapshot.canonicalToAlias()).thenReturn(Map.of("AlphaModel", "A"));
        when(snapshot.resolveQueryModel("AlphaModel")).thenReturn(Optional.of(model));
        when(snapshot.queryModelProvenance("AlphaModel")).thenReturn(Optional.empty());
        Fixture fixture = fixture(store);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> fixture.service().namespaceCatalogView("tenant-a"));

        assertThat(failure).hasMessageContaining("CATALOG_QUERY_PROVENANCE_ABSENT");
        verifyNoInteractions(fixture.coordinator());
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildCatalogUsesPinnedViewWithoutPerModelActiveReads() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        publishExact(store, "tenant-a", Map.of(
                "AlphaModel", new ModelVersion("Alpha caption", "a1"),
                "BetaModel", new ModelVersion("Beta caption", "a1")));
        Fixture fixture = fixture(store);

        Map<String, Object> catalog = fixture.service().buildCatalog(
                Map.of("fieldLimit", 0), "tenant-a", null);

        assertThat((List<String>) catalog.get("models"))
                .containsExactly("AlphaModel", "BetaModel");
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) catalog.get("items");
        assertThat(items).extracting(item -> item.get("shortAlias"))
                .containsExactly("A", "B");
        assertThat(items).extracting(item -> item.get("bundleName"))
                .containsOnly("stable-bundle");
        assertThat(items).extracting(item -> item.get("resourceIdentity"))
                .containsExactly(
                        "/models/AlphaModel.qm",
                        "/models/BetaModel.qm");
        verify(fixture.loader(), never()).getJdbcQueryModel(anyString(), anyString());
        verifyNoInteractions(fixture.coordinator());
    }

    @Test
    @SuppressWarnings("unchecked")
    void metadataPublicationRetriesWholeCatalogAndReturnsOnlyCompleteNewGeneration() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        publishExact(store, "tenant-a", Map.of(
                "AlphaModel", new ModelVersion("Alpha model", "a1")));
        SemanticServiceV3 semanticService = mock(SemanticServiceV3.class);
        Fixture fixture = fixture(store, semanticService);
        AtomicInteger metadataCalls = new AtomicInteger();
        when(semanticService.getMetadata(any(), anyString(), any()))
                .thenAnswer(invocation -> {
                    if (metadataCalls.incrementAndGet() == 1) {
                        publishExact(store, "tenant-a", Map.of(
                                "BetaModel", new ModelVersion("Beta model", "b1")));
                        return metadata("AlphaModel", "Alpha metadata");
                    }
                    return metadata("BetaModel", "Beta metadata");
                });

        Map<String, Object> catalog = fixture.service().buildCatalog(
                Map.of("fieldLimit", 0), "tenant-a", null);

        assertThat(metadataCalls).hasValue(2);
        assertThat((List<String>) catalog.get("models")).containsExactly("BetaModel");
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) catalog.get("items");
        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.get("model")).isEqualTo("BetaModel");
            assertThat(item.get("caption")).isEqualTo("Beta metadata");
            assertThat(item.get("shortAlias")).isEqualTo("B");
        });
        verifyNoInteractions(fixture.coordinator());
    }

    @Test
    void metadataGenerationChurnExhaustsBoundedRetryFailClosed() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        publishExact(store, "tenant-a", versions("AlphaModel", "a0"));
        SemanticServiceV3 semanticService = mock(SemanticServiceV3.class);
        Fixture fixture = fixture(store, semanticService);
        AtomicInteger metadataCalls = new AtomicInteger();
        when(semanticService.getMetadata(any(), anyString(), any()))
                .thenAnswer(invocation -> {
                    int call = metadataCalls.incrementAndGet();
                    publishExact(store, "tenant-a",
                            versions("AlphaModel", "a" + call));
                    return metadata("AlphaModel", "metadata " + call);
                });

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> fixture.service().buildCatalog(
                        Map.of("fieldLimit", 0), "tenant-a", null));

        assertThat(failure)
                .hasMessageContaining("CATALOG_BUILD_STALE_RETRY_EXHAUSTED");
        assertThat(metadataCalls).hasValue(3);
        verifyNoInteractions(fixture.coordinator());
    }

    @Test
    void metadataCallbackAdmissionBlockFailsClosed() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        publishExact(store, "tenant-a", versions("AlphaModel", "a1"));
        SemanticServiceV3 semanticService = mock(SemanticServiceV3.class);
        Fixture fixture = fixture(store, semanticService);
        when(semanticService.getMetadata(any(), anyString(), any()))
                .thenAnswer(invocation -> {
                    store.markStaleAdmissionBlocked(
                            "tenant-a", "controlled metadata block");
                    return metadata("AlphaModel", "blocked metadata");
                });

        assertThrows(CatalogAdmissionBlockedException.class,
                () -> fixture.service().buildCatalog(
                        Map.of("fieldLimit", 0), "tenant-a", null));

        verify(semanticService, times(1)).getMetadata(any(), anyString(), any());
        verifyNoInteractions(fixture.coordinator());
    }

    private static void assertCompleteTrackedView(
            SemanticModelCatalogService.NamespaceCatalogView view,
            CatalogSnapshot snapshot
    ) {
        assertThat(view.identity()).isEqualTo(snapshot.identity());
        assertThat(view.modelNames()).containsExactlyElementsOf(
                snapshot.discoveredQueryModelNames());
        assertThat(view.aliasesByModel()).containsExactlyEntriesOf(
                snapshot.canonicalToAlias());
        assertThat(view.queryModels()).containsOnlyKeys(view.modelNames());
        assertThat(view.resolutionsByModel()).containsOnlyKeys(view.modelNames());
        view.modelNames().forEach(modelName -> {
            assertThat(view.queryModels().get(modelName))
                    .isSameAs(snapshot.resolveQueryModel(modelName).orElseThrow());
            assertThat(view.resolutionsByModel().get(modelName).catalogIdentity())
                    .isEqualTo(view.identity());
            assertThat(view.resolutionsByModel().get(modelName).model())
                    .isSameAs(view.queryModels().get(modelName));
        });
    }

    private static Fixture fixture(CatalogSnapshotStore store) {
        return fixture(store, mock(SemanticServiceV3.class));
    }

    private static Fixture fixture(
            CatalogSnapshotStore store,
            SemanticServiceV3 semanticService
    ) {
        QueryModelLoaderImpl loader = mock(QueryModelLoaderImpl.class);
        CatalogRefreshCoordinator coordinator = mock(CatalogRefreshCoordinator.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SemanticModelCatalogService service = new SemanticModelCatalogService(
                semanticService,
                loader,
                mock(SystemBundlesContext.class),
                objectMapper,
                new SemanticQueryPayloadMapper(objectMapper),
                store,
                coordinator);
        return new Fixture(service, loader, coordinator);
    }

    private static CatalogSnapshot publishIncomplete(
            CatalogSnapshotStore store,
            String namespace,
            Set<String> modelNames
    ) {
        CatalogBuildView buildView = store.capture(namespace);
        try (CatalogSnapshotStore.CandidateScope scope =
                     store.openCandidate(buildView)) {
            scope.candidate().resetForNamespaceRefresh(modelNames);
            return scope.commit();
        }
    }

    private static CatalogSnapshot publishExact(
            CatalogSnapshotStore store,
            String namespace,
            Map<String, ModelVersion> versions
    ) {
        CatalogBuildView buildView = store.capture(namespace);
        try (CatalogSnapshotStore.CandidateScope scope =
                     store.openCandidate(buildView)) {
            CatalogCandidate candidate = scope.candidate();
            candidate.resetForNamespaceRefresh(versions.keySet());
            versions.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> stageModel(
                            candidate, namespace, entry.getKey(), entry.getValue()));
            return scope.commit();
        }
    }

    private static void stageModel(
            CatalogCandidate candidate,
            String namespace,
            String modelName,
            ModelVersion version
    ) {
        String alias = candidate.aliasFor(modelName);
        QueryModel model = mock(QueryModel.class);
        when(model.getName()).thenReturn(modelName);
        when(model.getShortAlias()).thenReturn(alias);
        when(model.getCaption()).thenReturn(version.caption());
        DatasourceBindingIdentity binding = binding(namespace, version.bindingGeneration());
        candidate.putQueryModel(
                modelName,
                model,
                new ModelProvenance(
                        modelName,
                        ModelProvenance.ModelKind.QUERY,
                        candidate.sourceRevision(),
                        Set.of(),
                        Map.of(binding.bindingKey(), binding),
                        true,
                        List.of(),
                        new ModelProvenance.ModelSource(
                                "stable-bundle",
                                namespace,
                                "/models/" + modelName + ".qm")));
    }

    private static DatasourceBindingIdentity binding(
            String namespace,
            String generation
    ) {
        String canonical = namespace == null || namespace.isBlank()
                ? "default"
                : namespace.trim();
        return new DatasourceBindingIdentity(
                canonical + ":primary",
                "jdbc",
                new DatasourceBindingGeneration("binding:" + generation));
    }

    private static Map<String, ModelVersion> versions(
            String modelName,
            String generation
    ) {
        return Map.of(modelName,
                new ModelVersion(modelName + " caption " + generation, generation));
    }

    private static SemanticMetadataResponse metadata(
            String modelName,
            String caption
    ) {
        SemanticMetadataResponse response = new SemanticMetadataResponse();
        Map<String, Object> models = new LinkedHashMap<>();
        models.put(modelName, Map.of("name", caption));
        response.setData(Map.of("models", models, "fields", Map.of()));
        return response;
    }

    private record Fixture(
            SemanticModelCatalogService service,
            QueryModelLoaderImpl loader,
            CatalogRefreshCoordinator coordinator
    ) {
    }

    private record ModelVersion(String caption, String bindingGeneration) {
    }
}
