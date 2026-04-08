package com.foggyframework.dataset.db.model.semantic.member;

import com.foggyframework.bundle.event.BundleRemovedEvent;
import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.engine.query_model.DbModelFileChangeHandler;
import com.foggyframework.dataset.db.model.event.BundleLifecycleListener;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.fsscript.loadder.FsscriptRemoveEvent;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("Synthetic member-QM 生命周期测试")
class SyntheticMemberQueryModelLifecycleTest extends EcommerceTestSupport {

    @Resource
    private BundleLifecycleListener bundleLifecycleListener;

    @Resource
    private DbModelFileChangeHandler dbModelFileChangeHandler;

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
    @DisplayName("BundleRemovedEvent 后 synthetic member-QM 可失效并重建")
    void syntheticModelShouldRebuildAfterBundleRemovedEvent() {
        QueryModel first = queryModelLoader.getJdbcQueryModel("FactSalesNestedDimQueryModel#product", null);

        bundleLifecycleListener.onBundleRemoved(new BundleRemovedEvent(
                this,
                "foggy-framework-dataset-jdbc-model-test",
                "",
                null
        ));

        QueryModel reloaded = queryModelLoader.getJdbcQueryModel("FactSalesNestedDimQueryModel#product", null);
        assertEquals("FactSalesNestedDimQueryModel#product", reloaded.getName());
        assertNotSame(first, reloaded, "BundleRemovedEvent 后应清理并重建 synthetic member-QM");
    }

    @Test
    @DisplayName("FsscriptRemoveEvent 后 synthetic member-QM 可失效并重建")
    void syntheticModelShouldRebuildAfterFileChangeEvent() {
        QueryModel first = queryModelLoader.getJdbcQueryModel("FactSalesNestedDimQueryModel#product", null);

        dbModelFileChangeHandler.onApplicationEvent(new FsscriptRemoveEvent(new ArrayList<>()));

        QueryModel reloaded = queryModelLoader.getJdbcQueryModel("FactSalesNestedDimQueryModel#product", null);
        assertEquals("FactSalesNestedDimQueryModel#product", reloaded.getName());
        assertNotSame(first, reloaded, "FsscriptRemoveEvent 后应清理并重建 synthetic member-QM");
    }
}
