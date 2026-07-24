package com.foggyframework.dataset.model.event;

import com.foggyframework.bundle.event.BundleAddedEvent;
import com.foggyframework.bundle.event.BundleRemovedEvent;
import com.foggyframework.dataset.model.engine.pivot.PivotOuterCacheInvalidationBroadcaster;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshCoordinator;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshRequest;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshTrigger;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Bundle生命周期事件监听器
 *
 * <p>监听 Bundle 的添加和移除事件，通过不可见候选目录完成原子刷新。
 *
 * @author foggy-framework
 * @since 1.0.0
 */
@Component
@Slf4j
public class BundleLifecycleListener {

    @Resource
    private CatalogRefreshCoordinator catalogRefreshCoordinator;

    @Resource
    private CatalogSnapshotStore catalogSnapshotStore;

    @Resource
    private PivotOuterCacheInvalidationBroadcaster pivotOuterCacheInvalidationBroadcaster;

    @EventListener
    @Order(100)
    public void onBundleAdded(BundleAddedEvent event) {
        refresh(event.getBundleName(), event.getNamespace(),
                event.isScopeKnown(), CatalogRefreshTrigger.BUNDLE);
    }

    @EventListener
    @Order(100)
    public void onBundleRemoved(BundleRemovedEvent event) {
        refresh(event.getBundleName(), event.getNamespace(),
                event.isScopeKnown(), CatalogRefreshTrigger.BUNDLE);
    }

    private void refresh(
            String bundleName,
            String namespace,
            boolean scopeKnown,
            CatalogRefreshTrigger trigger
    ) {
        String displayNamespace = namespace == null || namespace.isEmpty() ? "默认" : namespace;

        if (!scopeKnown) {
            int blocked = catalogSnapshotStore
                    .markKnownNamespacesStaleAdmissionBlocked(
                            "REFRESH_SCOPE_UNKNOWN: committed bundle mutation scope is not provable")
                    .size();
            log.warn("Committed bundle mutation has unknown scope: "
                            + "bundleName={}, blockedNamespaces={}",
                    bundleName, blocked);
            return;
        }

        try {
            var result = catalogRefreshCoordinator.refresh(
                    CatalogRefreshRequest.namespace(namespace, trigger));

            int outerCacheRemoved = 0;
            try {
                outerCacheRemoved = pivotOuterCacheInvalidationBroadcaster.evict(namespace, null);
            } catch (Exception e) {
                log.warn("namespace=[{}] catalog 已切换，但 Pivot outer-cache "
                                + "失效广播失败: {}",
                        displayNamespace, e.getClass().getSimpleName());
            }

            log.info("Bundle catalog refresh published: bundleName={}, namespace={}, "
                            + "generation={}, pivotOuterCacheRemoved={}",
                    bundleName, displayNamespace,
                    result.afterIdentity().generation().value(),
                    outerCacheRemoved);
        } catch (RuntimeException failure) {
            // Source mutation is already committed. Coordinator failure policy
            // preserves an admissible old catalog or keeps admission blocked.
            log.error("Bundle catalog refresh failed after source commit: "
                            + "bundleName={}, namespace={}, reason={}",
                    bundleName, displayNamespace,
                    failure.getClass().getSimpleName());
        }
    }
}
