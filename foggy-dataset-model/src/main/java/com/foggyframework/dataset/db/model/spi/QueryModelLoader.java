package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.bundle.BundleResource;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogResolution;

import java.util.Collection;
import java.util.Map;

public interface QueryModelLoader {
    /**
     * 清除所有命名空间的缓存
     */
    void clearAll();

    /**
     * 清除指定命名空间的缓存
     *
     * @param namespace 命名空间（null或空字符串表示默认命名空间）
     */
    void clearByNamespace(String namespace);

    /**
     * 获取查询模型（从指定命名空间）
     *
     * @param queryModelName 查询模型名称
     * @param namespace      命名空间（null或空字符串表示默认命名空间）
     * @return 查询模型
     */
    QueryModel getJdbcQueryModel(String queryModelName, String namespace);

    /**
     * Resolve a query model together with the exact catalog and datasource
     * binding identities that supplied it.  Consumers that execute work must
     * use this atomic projection instead of reading a model and then sampling
     * the active generation in a second step.
     */
    default CatalogResolution<QueryModel> resolveJdbcQueryModel(
            String queryModelName,
            String namespace
    ) {
        return null;
    }

    /**
     * Resolve multiple already/materialized models from one final immutable
     * catalog view. All non-null resolutions returned by a lifecycle-aware
     * implementation must carry the same CatalogIdentity.
     *
     * <p>The null default preserves third-party loader and Mockito compatibility;
     * such callers remain untracked and must not be used for reusable cache keys.</p>
     */
    default Map<String, CatalogResolution<QueryModel>> resolveJdbcQueryModels(
            Collection<String> queryModelNames,
            String namespace
    ) {
        return null;
    }

    /**
     * 从BundleResource加载查询模型
     *
     * @param bundleResource Bundle资源
     * @return 查询模型
     */
    QueryModel loadJdbcQueryModel(BundleResource bundleResource);
}
