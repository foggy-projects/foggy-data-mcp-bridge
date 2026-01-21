package com.foggyframework.dataset.db.model.event;

import com.foggyframework.bundle.event.BundleRemovedEvent;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.db.model.spi.TableModelLoaderManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Bundle生命周期事件监听器
 *
 * <p>监听Bundle的添加和移除事件，自动清理相关的模型缓存。
 *
 * @author foggy-framework
 * @since 1.0.0
 */
@Component
@Slf4j
public class BundleLifecycleListener {

    @Resource
    private TableModelLoaderManager tableModelLoaderManager;

    @Resource
    private QueryModelLoader queryModelLoader;

    /**
     * 监听Bundle移除事件
     *
     * <p>当Bundle被移除时，自动清除该Bundle所属命名空间的所有模型缓存。
     *
     * @param event Bundle移除事件
     */
    @EventListener
    @Order(100)
    public void onBundleRemoved(BundleRemovedEvent event) {
        String bundleName = event.getBundleName();
        String namespace = event.getNamespace();

        log.info("监听到Bundle移除事件: bundleName={}, namespace={}",
                bundleName, namespace.isEmpty() ? "默认" : namespace);

        try {
            // 清除TM缓存（表模型）
            log.debug("开始清除TableModel缓存: namespace={}", namespace);
            tableModelLoaderManager.clearByNamespace(namespace);

            // 清除QM缓存（查询模型）
            log.debug("开始清除QueryModel缓存: namespace={}", namespace);
            queryModelLoader.clearByNamespace(namespace);

            log.info("已清除namespace=[{}] 的所有模型缓存", namespace.isEmpty() ? "默认" : namespace);

        } catch (Exception e) {
            log.error("清除namespace=[{}] 的缓存时发生异常: {}",
                    namespace.isEmpty() ? "默认" : namespace, e.getMessage(), e);
        }
    }
}
