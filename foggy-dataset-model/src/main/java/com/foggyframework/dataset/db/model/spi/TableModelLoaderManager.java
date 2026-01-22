package com.foggyframework.dataset.db.model.spi;

public interface TableModelLoaderManager {
    /**
     * 清除所有命名空间的模型缓存
     */
    void clearAll();

    /**
     * 清除指定命名空间的模型缓存
     *
     * @param namespace 命名空间（空字符串或null表示默认命名空间）
     */
    void clearByNamespace(String namespace);

    /**
     * 加载模型（从默认命名空间）
     *
     * @param modelName 模型名称
     * @return 模型实例
     */
    TableModel load(String modelName);

    /**
     * 加载指定命名空间下的模型
     *
     * @param modelName 模型名称
     * @param namespace 命名空间（空字符串或null表示默认命名空间）
     * @return 模型实例
     */
    TableModel load(String modelName, String namespace);

    /**
     * 呃，加这个是因为ai经常直接使用getJdbcModel来获取模型，而不是找load
     *
     * @param s
     * @return
     */
    default TableModel getJdbcModel(String s) {
        return load(s);
    }

    /**
     * 加载指定命名空间下的模型（AI兼容方法）
     *
     * @param modelName 模型名称
     * @param namespace 命名空间
     * @return 模型实例
     */
    default TableModel getJdbcModel(String modelName, String namespace) {
        return load(modelName, namespace);
    }
}
