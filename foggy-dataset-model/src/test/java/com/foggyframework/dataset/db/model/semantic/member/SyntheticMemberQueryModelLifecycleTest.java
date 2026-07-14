package com.foggyframework.dataset.db.model.semantic.member;

import com.foggyframework.bundle.event.BundleRemovedEvent;
import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.engine.query_model.DbModelFileChangeHandler;
import com.foggyframework.dataset.db.model.event.BundleLifecycleListener;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogAdmissionBlockedException;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogAdmissionState;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshCoordinator;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshRequest;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshTrigger;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.fsscript.loadder.FsscriptRemoveEvent;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Synthetic member-QM 生命周期测试")
class SyntheticMemberQueryModelLifecycleTest extends EcommerceTestSupport {

    @Resource
    private BundleLifecycleListener bundleLifecycleListener;

    @Resource
    private DbModelFileChangeHandler dbModelFileChangeHandler;

    @Resource
    private CatalogRefreshCoordinator catalogRefreshCoordinator;

    @Resource
    private CatalogSnapshotStore catalogSnapshotStore;

    @Test
    @DisplayName("同一 namespace 下 synthetic member-QM 命中缓存复用同一实例")
    void syntheticModelShouldReuseNamespaceCache() {
        QueryModel first = queryModelLoader.getJdbcQueryModel("FactSalesNestedDimQueryModel#product", null);
        QueryModel second = queryModelLoader.getJdbcQueryModel("FactSalesNestedDimQueryModel#product", null);

        assertSame(first, second, "同一 namespace 下重复装载应复用同一 synthetic member-QM 实例");
    }

    @Test
    @DisplayName("clearByNamespace 后 synthetic member-QM 可失效并重建")
    void syntheticModelShouldRebuildAfterNamespaceClear() {
        QueryModel first = queryModelLoader.getJdbcQueryModel("FactSalesNestedDimQueryModel#product", null);

        queryModelLoader.clearByNamespace("");

        QueryModel reloaded = queryModelLoader.getJdbcQueryModel("FactSalesNestedDimQueryModel#product", null);
        assertEquals("FactSalesNestedDimQueryModel#product", reloaded.getName());
        assertNotSame(first, reloaded, "clearByNamespace 后应重建 synthetic member-QM");
    }

    @Test
    @DisplayName("Bundle 全量刷新失败后保留可用的旧 synthetic member-QM")
    void failedBundleRefreshShouldPreserveTheOldSyntheticModel() {
        QueryModel first = queryModelLoader.getJdbcQueryModel("FactSalesNestedDimQueryModel#product", null);

        bundleLifecycleListener.onBundleRemoved(new BundleRemovedEvent(
                this,
                "foggy-framework-dataset-jdbc-model-test",
                "",
                null
        ));

        QueryModel retained = queryModelLoader.getJdbcQueryModel(
                "FactSalesNestedDimQueryModel#product", null);
        assertSame(first, retained, "失败的原子刷新不得清空或半替换旧 synthetic member-QM");
        assertEquals(CatalogAdmissionState.ACTIVE_OLD_PRESERVED,
                catalogSnapshotStore.admissionState(""));
    }

    @Test
    @DisplayName("未知范围 FsscriptRemoveEvent 阻断新读，显式全量刷新后恢复")
    void unknownFileChangeMustBlockUntilScopedRecovery() {
        QueryModel first = queryModelLoader.getJdbcQueryModel("FactSalesNestedDimQueryModel#product", null);

        dbModelFileChangeHandler.onApplicationEvent(new FsscriptRemoveEvent(new ArrayList<>()));

        assertThrows(CatalogAdmissionBlockedException.class,
                () -> queryModelLoader.getJdbcQueryModel(
                        "FactSalesNestedDimQueryModel#product", null));

        catalogRefreshCoordinator.refresh(CatalogRefreshRequest.models(
                "",
                Set.of(CatalogModelKey.query("FactSalesNestedDimQueryModel")),
                CatalogRefreshTrigger.EXPLICIT_RECOVERY));
        QueryModel reloaded = queryModelLoader.getJdbcQueryModel(
                "FactSalesNestedDimQueryModel#product", null);
        assertEquals("FactSalesNestedDimQueryModel#product", reloaded.getName());
        assertNotSame(first, reloaded,
                "显式命名空间刷新应以新一代 synthetic member-QM 恢复 admission");
    }
}
