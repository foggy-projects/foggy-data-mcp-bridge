package com.foggyframework.dataset.db.model.spi;

/**
 * 无操作的查询缓存提供者
 * <p>
 * 作为默认实现，不执行任何缓存操作。
 * 当未引入 foggy-dataset-model-cache 模块时使用此实现。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
public final class NoOpQueryCacheProvider implements QueryCacheProvider {

    /**
     * 单例实例
     */
    public static final NoOpQueryCacheProvider INSTANCE = new NoOpQueryCacheProvider();

    private NoOpQueryCacheProvider() {
        // 私有构造函数，使用单例
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE; // 最低优先级
    }
}
