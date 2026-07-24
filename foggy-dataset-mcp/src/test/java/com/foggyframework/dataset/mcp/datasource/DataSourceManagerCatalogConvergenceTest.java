package com.foggyframework.dataset.mcp.datasource;

import com.foggyframework.dataset.model.lifecycle.catalog.CatalogAdmissionBlockedException;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogAdmissionState;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogCandidate;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.dataset.model.lifecycle.port.ResolvedDatasourceBinding;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshCoordinator;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshRequest;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshTrigger;
import com.foggyframework.dataset.model.spi.TableModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataSourceManagerCatalogConvergenceTest {

    private final List<DataSourceManager> managers = new ArrayList<>();

    @AfterEach
    void closeManagers() {
        managers.forEach(DataSourceManager::close);
    }

    @Test
    void configureBlocksAtMutationBoundaryThenRefreshesOnlyBindingConsumers() throws Exception {
        DataSourceManager manager = manager();
        manager.configure("orders", config("old"));
        manager.configure("inventory", config("inventory"));
        ResolvedDatasourceBinding oldOrders = manager.resolveBinding("orders");
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogIdentity tenantABefore = publishCatalog(store, "tenant-a", oldOrders.identity());
        CatalogIdentity tenantBBefore = publishCatalog(
                store, "tenant-b", manager.getBindingIdentity("inventory"));
        CatalogRefreshCoordinator coordinator = mock(CatalogRefreshCoordinator.class);
        AtomicReference<CatalogRefreshRequest> observed = new AtomicReference<>();

        doAnswer(invocation -> {
            CatalogRefreshRequest request = invocation.getArgument(0);
            observed.set(request);
            assertThat(store.admissionState("tenant-a"))
                    .isEqualTo(CatalogAdmissionState.STALE_ADMISSION_BLOCKED);
            assertThat(manager.currentness(oldOrders.identity()))
                    .isEqualTo(BindingCurrentness.STALE);
            publishCatalog(store, request.namespace(), manager.getBindingIdentity("orders"));
            return null;
        }).when(coordinator).refresh(any(CatalogRefreshRequest.class));
        manager.configureCatalogConvergence(store, coordinator);

        manager.configure("orders", config("new"));

        assertThat(observed.get().namespace()).isEqualTo("tenant-a");
        assertThat(observed.get().trigger()).isEqualTo(CatalogRefreshTrigger.DATASOURCE);
        assertThat(store.admissionState("tenant-a")).isEqualTo(CatalogAdmissionState.ACTIVE);
        assertThat(store.current("tenant-a").orElseThrow().identity())
                .isNotEqualTo(tenantABefore);
        assertThat(store.current("tenant-b").orElseThrow().identity())
                .isEqualTo(tenantBBefore);
        assertThat(manager.currentness(manager.getBindingIdentity("orders")))
                .isEqualTo(BindingCurrentness.CURRENT);
        assertThatThrownBy(oldOrders.dataSource()::getConnection)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("DATASOURCE_BINDING_REVOKED");
        verify(coordinator).refresh(any(CatalogRefreshRequest.class));
    }

    @Test
    void removeCommitCannotReacquireOldBindingAndFailedRefreshStaysBlocked() {
        DataSourceManager manager = manager();
        manager.configure("orders", config("old"));
        ResolvedDatasourceBinding oldOrders = manager.resolveBinding("orders");
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogIdentity before = publishCatalog(store, "tenant-a", oldOrders.identity());
        CatalogRefreshCoordinator coordinator = mock(CatalogRefreshCoordinator.class);
        doThrow(new IllegalStateException("removed binding unavailable"))
                .when(coordinator).refresh(any(CatalogRefreshRequest.class));
        manager.configureCatalogConvergence(store, coordinator);

        assertThatThrownBy(() -> manager.remove("orders"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("removed binding unavailable");

        assertThat(manager.resolveBinding("orders")).isNull();
        assertThat(manager.currentness(oldOrders.identity())).isEqualTo(BindingCurrentness.STALE);
        assertThatThrownBy(oldOrders.dataSource()::getConnection)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("DATASOURCE_BINDING_REVOKED");
        assertThat(store.admissionState("tenant-a"))
                .isEqualTo(CatalogAdmissionState.STALE_ADMISSION_BLOCKED);
        assertThat(store.current("tenant-a").orElseThrow().identity()).isEqualTo(before);
        assertThatThrownBy(() -> store.readCurrent("tenant-a"))
                .isInstanceOf(CatalogAdmissionBlockedException.class);
    }

    @Test
    void optionalSpringProvidersPreserveNamedManagerUsageWhenCatalogIsAbsent() {
        DataSourceManager manager = manager();
        StaticListableBeanFactory empty = new StaticListableBeanFactory();
        manager.configureCatalogConvergenceProviders(
                empty.getBeanProvider(CatalogSnapshotStore.class),
                empty.getBeanProvider(CatalogRefreshCoordinator.class));

        manager.configure("orders", config("standalone"));

        assertThat(manager.resolveBinding("orders")).isNotNull();
        assertThat(manager.currentness(manager.getBindingIdentity("orders")))
                .isEqualTo(BindingCurrentness.CURRENT);
    }

    private DataSourceManager manager() {
        DataSourceConfigPersistence persistence = mock(DataSourceConfigPersistence.class);
        DataSourceManager manager = new DataSourceManager(
                persistence,
                ignored -> mock(DataSource.class),
                (task, delayMillis) -> () -> { },
                1_000L,
                "mcp-convergence-test");
        managers.add(manager);
        return manager;
    }

    private static DataSourceManager.DataSourceConfig config(String database) {
        return DataSourceManager.DataSourceConfig.builder()
                .host("db.internal")
                .port(5432)
                .database(database)
                .username("service")
                .password("secret")
                .driver("postgresql")
                .build();
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
}
