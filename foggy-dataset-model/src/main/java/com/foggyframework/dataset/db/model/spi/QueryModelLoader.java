package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.bundle.BundleResource;

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
     * 从BundleResource加载查询模型
     *
     * @param bundleResource Bundle资源
     * @return 查询模型
     */
    QueryModel loadJdbcQueryModel(BundleResource bundleResource);
}
