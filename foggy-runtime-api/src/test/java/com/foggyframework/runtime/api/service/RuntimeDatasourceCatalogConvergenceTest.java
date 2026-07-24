package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogAdmissionBlockedException;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogAdmissionState;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogCandidate;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.dataset.model.lifecycle.port.RevokeMode;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshCoordinator;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshRequest;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshTrigger;
import com.foggyframework.dataset.model.spi.TableModel;
import com.foggyframework.runtime.api.controller.RuntimeDatasourcesController;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.DatasourceRequest;
import com.foggyframework.runtime.api.service.RuntimeDatasourceRegistryService.RuntimeDatasourceRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RuntimeDatasourceCatalogConvergenceTest {

    @TempDir
    Path tempDir;

    private final List<ManagedDataSourcePoolManager> poolManagers = new ArrayList<>();

    @AfterEach
    void destroyPools() {
        poolManagers.forEach(ManagedDataSourcePoolManager::destroy);
    }

    @Test
    void namedUpdateBlocksAtAdmissionBoundaryThenRefreshesOnlyConsumingNamespaces() {
        RuntimeDatasourceRegistryService registry = service("named-update.json");
        RuntimeDatasourceRecord sales = save(registry, "sales", true, "old");
        RuntimeDatasourceRecord inventory = save(registry, "inventory", true, "inventory");
        DatasourceBindingIdentity oldSales = namedIdentity(sales);
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogIdentity tenantABefore = publishCatalog(store, "tenant-a", oldSales);
        CatalogIdentity tenantBBefore = publishCatalog(store, "tenant-b", namedIdentity(inventory));
        CatalogRefreshCoordinator coordinator = mock(CatalogRefreshCoordinator.class);
        AtomicReference<CatalogRefreshRequest> observed = new AtomicReference<>();

        doAnswer(invocation -> {
            CatalogRefreshRequest request = invocation.getArgument(0);
            observed.set(request);
            assertThat(store.admissionState("tenant-a"))
                    .isEqualTo(CatalogAdmissionState.STALE_ADMISSION_BLOCKED);
            assertThat(registry.currentness(oldSales)).isEqualTo(BindingCurrentness.STALE);
            publishCatalog(store, request.namespace(), namedIdentity(
                    registry.find("sales").orElseThrow()));
            return null;
        }).when(coordinator).refresh(any(CatalogRefreshRequest.class));
        registry.configureCatalogConvergence(store, coordinator);

        RuntimeDatasourceRecord changed = save(registry, "sales", true, "new");

        assertThat(observed.get().namespace()).isEqualTo("tenant-a");
        assertThat(observed.get().trigger()).isEqualTo(CatalogRefreshTrigger.DATASOURCE);
        assertThat(store.admissionState("tenant-a")).isEqualTo(CatalogAdmissionState.ACTIVE);
        assertThat(store.current("tenant-a").orElseThrow().identity())
                .isNotEqualTo(tenantABefore);
        assertThat(store.current("tenant-b").orElseThrow().identity())
                .isEqualTo(tenantBBefore);
        assertThat(registry.currentness(namedIdentity(changed)))
                .isEqualTo(BindingCurrentness.CURRENT);
        verify(coordinator).refresh(any(CatalogRefreshRequest.class));
    }

    @Test
    void namespaceRebindUsesTheExactNamespaceEvenWithoutNamedBindingProvenance() {
        RuntimeDatasourceRegistryService registry = service("namespace-rebind.json");
        save(registry, "sales-a", true, "a");
        save(registry, "sales-b", true, "b");
        registry.bindNamespace("tenant-a", "sales-a");
        DatasourceBindingIdentity oldBinding = namespaceIdentity(registry, "tenant-a", "sales-a");
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogIdentity before = publishCatalog(store, "tenant-a", oldBinding);
        CatalogRefreshCoordinator coordinator = mock(CatalogRefreshCoordinator.class);

        doAnswer(invocation -> {
            CatalogRefreshRequest request = invocation.getArgument(0);
            assertThat(request.namespace()).isEqualTo("tenant-a");
            assertThat(store.admissionState("tenant-a"))
                    .isEqualTo(CatalogAdmissionState.STALE_ADMISSION_BLOCKED);
            assertThat(registry.currentness(oldBinding)).isEqualTo(BindingCurrentness.STALE);
            publishCatalog(store, "tenant-a", namespaceIdentity(
                    registry, "tenant-a", "sales-b"));
            return null;
        }).when(coordinator).refresh(any(CatalogRefreshRequest.class));
        registry.configureCatalogConvergence(store, coordinator);

        registry.bindNamespace("tenant-a", "sales-b");

        assertThat(store.admissionState("tenant-a")).isEqualTo(CatalogAdmissionState.ACTIVE);
        assertThat(store.current("tenant-a").orElseThrow().identity()).isNotEqualTo(before);
        assertThat(registry.getNamespaceDatasource("tenant-a")).contains("sales-b");
    }

    @Test
    void disableCommitRetainsOldSnapshotButRefreshFailureKeepsAdmissionBlocked() {
        RuntimeDatasourceRegistryService registry = service("disable.json");
        RuntimeDatasourceRecord enabled = save(registry, "sales", true, "old");
        DatasourceBindingIdentity oldBinding = namedIdentity(enabled);
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogIdentity before = publishCatalog(store, "tenant-a", oldBinding);
        CatalogRefreshCoordinator coordinator = mock(CatalogRefreshCoordinator.class);
        doThrow(new IllegalStateException("detached build failed"))
                .when(coordinator).refresh(any(CatalogRefreshRequest.class));
        registry.configureCatalogConvergence(store, coordinator);

        assertThatThrownBy(() -> save(registry, "sales", false, "disabled"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("detached build failed");

        assertThat(registry.find("sales").orElseThrow().enabled()).isFalse();
        assertThat(registry.currentness(oldBinding)).isEqualTo(BindingCurrentness.STALE);
        assertFailClosedWithRetainedSnapshot(store, "tenant-a", before);
    }

    @Test
    void removeCommitRetainsOldSnapshotButCannotReacquireRemovedBinding() {
        RuntimeDatasourceRegistryService registry = service("remove.json");
        RuntimeDatasourceRecord enabled = save(registry, "sales", true, "old");
        DatasourceBindingIdentity oldBinding = namedIdentity(enabled);
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogIdentity before = publishCatalog(store, "tenant-a", oldBinding);
        CatalogRefreshCoordinator coordinator = mock(CatalogRefreshCoordinator.class);
        doThrow(new IllegalStateException("removed binding unavailable"))
                .when(coordinator).refresh(any(CatalogRefreshRequest.class));
        registry.configureCatalogConvergence(store, coordinator);

        assertThatThrownBy(() -> registry.remove("sales"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("removed binding unavailable");

        assertThat(registry.find("sales")).isEmpty();
        assertThat(registry.currentness(oldBinding)).isEqualTo(BindingCurrentness.STALE);
        assertFailClosedWithRetainedSnapshot(store, "tenant-a", before);
    }

    @Test
    void optionalSpringProvidersPreserveRegistryOnlyUsageWhenCatalogIsAbsent() {
        RuntimeDatasourceRegistryService registry = service("no-catalog.json");
        StaticListableBeanFactory empty = new StaticListableBeanFactory();
        registry.configureCatalogConvergenceProviders(
                empty.getBeanProvider(CatalogSnapshotStore.class),
                empty.getBeanProvider(CatalogRefreshCoordinator.class));

        RuntimeDatasourceRecord saved = save(registry, "sales", true, "standalone");

        assertThat(registry.find("sales")).contains(saved);
        assertThat(registry.currentness(namedIdentity(saved)))
                .isEqualTo(BindingCurrentness.CURRENT);
    }

    @Test
    void committedSaveBlocksOldCatalogAndSkipsRefreshWhenPoolCallbackFails() {
        ManagedDataSourcePoolManager poolManager = mock(ManagedDataSourcePoolManager.class);
        RuntimeDatasourceRegistryService registry = service("callback-failure.json", poolManager);
        RuntimeDatasourceRecord beforeRecord = save(registry, "sales", true, "old");
        DatasourceBindingIdentity oldBinding = namedIdentity(beforeRecord);
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogIdentity beforeCatalog = publishCatalog(store, "tenant-a", oldBinding);
        CatalogRefreshCoordinator coordinator = mock(CatalogRefreshCoordinator.class);
        registry.configureCatalogConvergence(store, coordinator);
        doThrow(new IllegalStateException("pool callback failed"))
                .when(poolManager).onRecordSaved(any(), any(), any(RevokeMode.class));

        assertThatThrownBy(() -> save(registry, "sales", true, "new"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("pool callback failed");

        RuntimeDatasourceRecord committed = registry.find("sales").orElseThrow();
        assertThat(committed.jdbcUrl()).endsWith(":new");
        assertThat(committed.bindingGeneration())
                .isNotEqualTo(beforeRecord.bindingGeneration());
        assertThat(registry.currentness(oldBinding)).isEqualTo(BindingCurrentness.STALE);
        assertFailClosedWithRetainedSnapshot(store, "tenant-a", beforeCatalog);
        verifyNoInteractions(coordinator);
    }

    @Test
    void committedSaveMustBlockCatalogBeforeAPoolCallbackCanComplete() throws Exception {
        ManagedDataSourcePoolManager poolManager = mock(ManagedDataSourcePoolManager.class);
        RuntimeDatasourceRegistryService registry = service("callback-window.json", poolManager);
        RuntimeDatasourceRecord beforeRecord = save(registry, "sales", true, "old");
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        publishCatalog(store, "tenant-a", namedIdentity(beforeRecord));
        CatalogRefreshCoordinator coordinator = mock(CatalogRefreshCoordinator.class);
        registry.configureCatalogConvergence(store, coordinator);
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        doAnswer(ignored -> {
            callbackEntered.countDown();
            assertThat(releaseCallback.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(poolManager).onRecordSaved(any(), any(), any(RevokeMode.class));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<RuntimeDatasourceRecord> mutation = executor.submit(
                    () -> save(registry, "sales", true, "new"));
            assertThat(callbackEntered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(store.admissionState("tenant-a"))
                    .isEqualTo(CatalogAdmissionState.STALE_ADMISSION_BLOCKED);
            assertThatThrownBy(() -> store.readCurrent("tenant-a"))
                    .isInstanceOf(CatalogAdmissionBlockedException.class);

            releaseCallback.countDown();
            mutation.get(5, TimeUnit.SECONDS);
        } finally {
            releaseCallback.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void controllerCanonicalizesLogicalNameWithoutChangingConnectionCredentials() {
        RuntimeDatasourceRegistryService registry = service("controller-canonical.json");
        RuntimeDatasourcesController controller = new RuntimeDatasourcesController(
                new RuntimeApiResponseFactory(new FoggyRuntimeApiProperties()),
                registry);
        String username = "  padded user  ";
        String password = "  padded secret  ";

        assertThat(controller.addDatasource(new DatasourceRequest(
                "  sales  ",
                "  H2  ",
                "jdbc:h2:mem:controller-canonical",
                username,
                password,
                null,
                false,
                true,
                null)).success()).isTrue();

        RuntimeDatasourceRecord stored = registry.find("sales").orElseThrow();
        assertThat(stored.name()).isEqualTo("sales");
        assertThat(stored.type()).isEqualTo("h2");
        assertThat(stored.username()).isEqualTo(username);
        assertThat(stored.password()).isEqualTo(password);
    }

    private RuntimeDatasourceRegistryService service(String fileName) {
        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.getDatasourceRegistry().setPath(tempDir.resolve(fileName).toString());
        ManagedDataSourcePoolManager poolManager = new ManagedDataSourcePoolManager(
                properties,
                (record, password, settings) -> {
                    throw new AssertionError("convergence tests must not open a physical pool");
                },
                Clock.systemUTC(),
                null,
                false
        );
        poolManagers.add(poolManager);
        return service(fileName, poolManager);
    }

    private RuntimeDatasourceRegistryService service(
            String fileName,
            ManagedDataSourcePoolManager poolManager
    ) {
        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.getDatasourceRegistry().setPath(tempDir.resolve(fileName).toString());
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        return new RuntimeDatasourceRegistryService(
                properties,
                beanFactory.getBeanProvider(DataSource.class),
                new ObjectMapper().findAndRegisterModules(),
                poolManager
        );
    }

    private static RuntimeDatasourceRecord save(
            RuntimeDatasourceRegistryService registry,
            String name,
            boolean enabled,
            String database
    ) {
        return registry.save(registry.newRecord(
                name,
                "h2",
                "jdbc:h2:mem:" + database,
                "sa",
                null,
                null,
                enabled));
    }

    private static DatasourceBindingIdentity namedIdentity(RuntimeDatasourceRecord record) {
        return new DatasourceBindingIdentity(
                "runtime:named:" + record.name(),
                "runtime-registry:" + record.name(),
                new DatasourceBindingGeneration(record.bindingGeneration()));
    }

    private static DatasourceBindingIdentity namespaceIdentity(
            RuntimeDatasourceRegistryService registry,
            String namespace,
            String dataSourceName
    ) {
        return new DatasourceBindingIdentity(
                "runtime:namespace-default:" + namespace,
                "runtime-registry:" + dataSourceName,
                new DatasourceBindingGeneration(
                        registry.getNamespaceBindingGeneration(namespace).orElseThrow()));
    }

    private static CatalogIdentity publishCatalog(
            CatalogSnapshotStore store,
            String namespace,
            DatasourceBindingIdentity binding
    ) {
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate(namespace)) {
            CatalogCandidate candidate = scope.candidate();
            candidate.resetForNamespaceRefresh(Set.of());
            TableModel model = mock(TableModel.class);
            when(model.getName()).thenReturn("Orders");
            candidate.putTableModel(
                    "Orders",
                    model,
                    new ModelProvenance(
                            "Orders",
                            ModelProvenance.ModelKind.TABLE,
                            candidate.sourceRevision(),
                            Set.of(),
                            Map.of(binding.bindingKey(), binding),
                            true,
                            List.of()));
            return scope.commit().identity();
        }
    }

    private static void assertFailClosedWithRetainedSnapshot(
            CatalogSnapshotStore store,
            String namespace,
            CatalogIdentity before
    ) {
        assertThat(store.admissionState(namespace))
                .isEqualTo(CatalogAdmissionState.STALE_ADMISSION_BLOCKED);
        assertThat(store.current(namespace).orElseThrow().identity()).isEqualTo(before);
        assertThatThrownBy(() -> store.readCurrent(namespace))
                .isInstanceOf(CatalogAdmissionBlockedException.class);
    }
}
