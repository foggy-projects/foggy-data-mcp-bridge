package com.foggyframework.dataset.db.model.event;

import com.foggyframework.bundle.event.BundleRemovedEvent;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationBroadcaster;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.spi.TableModelLoaderManager;
import com.foggyframework.dataset.db.model.test.JdbcModelTestApplication;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BundleLifecycleListener 集成测试
 *
 * <p>验证 Bundle 事件监听器的注册和缓存清理行为。</p>
 */
@Slf4j
@SpringBootTest(classes = JdbcModelTestApplication.class)
@DisplayName("Bundle 生命周期监听器测试")
class BundleLifecycleListenerTest {

    @Resource
    private BundleLifecycleListener bundleLifecycleListener;

    @Resource
    private TableModelLoaderManager tableModelLoaderManager;

    @Resource
    private QueryModelLoader queryModelLoader;

    // ==========================================
    // Bean 注册验证
    // ==========================================

    @Test
    @DisplayName("BundleLifecycleListener Bean 已注册")
    void testListenerRegistered() {
        assertNotNull(bundleLifecycleListener, "BundleLifecycleListener should be registered as bean");
    }

    // ==========================================
    // 缓存清理验证
    // ==========================================

    @Test
    @DisplayName("加载模型后缓存存在 - 基础验证")
    void testModelCacheExists() {
        // 先加载一个模型，确保缓存中有数据
        TableModel model = tableModelLoaderManager.load("DimDateModel");
        assertNotNull(model, "DimDateModel should be loadable");

        // 再次加载应该从缓存返回（相同实例或相同数据）
        TableModel model2 = tableModelLoaderManager.load("DimDateModel");
        assertNotNull(model2);

        log.info("First load: {}, Second load: {}", model.getName(), model2.getName());
    }

    @Test
    @DisplayName("clearByNamespace - 默认命名空间清理不影响重新加载")
    void testClearByNamespaceDefault() {
        // 先加载模型
        TableModel model = tableModelLoaderManager.load("DimDateModel");
        assertNotNull(model);

        // 清理默认命名空间缓存
        tableModelLoaderManager.clearByNamespace("");

        // 清理后应该能重新加载
        TableModel reloaded = tableModelLoaderManager.load("DimDateModel");
        assertNotNull(reloaded, "Model should be re-loadable after cache clear");
        assertEquals("DimDateModel", reloaded.getName());
    }

    @Test
    @DisplayName("clearByNamespace - 非默认命名空间清理不影响默认模型")
    void testClearByNonDefaultNamespace() {
        // 加载默认命名空间模型
        TableModel model = tableModelLoaderManager.load("DimDateModel");
        assertNotNull(model);

        // 清理一个不相关的命名空间
        tableModelLoaderManager.clearByNamespace("nonexistent_namespace");

        // 默认命名空间的模型应不受影响
        TableModel afterClear = tableModelLoaderManager.load("DimDateModel");
        assertNotNull(afterClear);
    }

    @Test
    @DisplayName("QueryModelLoader clearByNamespace 不抛异常")
    void testQueryModelClearByNamespace() {
        // 确保清理操作不会抛异常
        assertDoesNotThrow(() -> queryModelLoader.clearByNamespace(""),
                "clearByNamespace should not throw for default namespace");
        assertDoesNotThrow(() -> queryModelLoader.clearByNamespace("nonexistent"),
                "clearByNamespace should not throw for nonexistent namespace");
    }

    @Test
    @DisplayName("BundleRemovedEvent 中 outer-cache broadcaster 异常不向外冒出")
    void testBundleRemovedSwallowsOuterCacheBroadcasterFailure() {
        PivotOuterCacheInvalidationBroadcaster original =
                (PivotOuterCacheInvalidationBroadcaster) ReflectionTestUtils.getField(
                        bundleLifecycleListener, "pivotOuterCacheInvalidationBroadcaster");
        try {
            ReflectionTestUtils.setField(bundleLifecycleListener, "pivotOuterCacheInvalidationBroadcaster",
                    (PivotOuterCacheInvalidationBroadcaster) (namespace, model) -> {
                        throw new IllegalStateException("publish failed");
                    });

            assertDoesNotThrow(() -> bundleLifecycleListener.onBundleRemoved(new BundleRemovedEvent(
                    this,
                    "foggy-framework-dataset-jdbc-model-test",
                    "",
                    null
            )));
        } finally {
            ReflectionTestUtils.setField(bundleLifecycleListener, "pivotOuterCacheInvalidationBroadcaster", original);
        }
    }
}
