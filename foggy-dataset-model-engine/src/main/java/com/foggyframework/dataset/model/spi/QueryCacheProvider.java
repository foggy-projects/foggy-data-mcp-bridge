package com.foggyframework.dataset.model.spi;

import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext.QueryCacheConfig;
import com.foggyframework.dataset.model.PagingResultImpl;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 查询缓存提供者 SPI（双层缓存）
 * <p>
 * 提供两层缓存机制：
 * <ul>
 *   <li><b>L1 缓存（Token 级别）</b>：基于授权令牌和请求参数，可跳过 SQL 构建</li>
 *   <li><b>L2 缓存（SQL 级别）</b>：基于最终 SQL 和参数，精确匹配</li>
 * </ul>
 * </p>
 * <p>
 * 默认使用 {@link NoOpQueryCacheProvider}（无操作）。
 * 引入 foggy-dataset-model-cache 模块后可启用具体实现。
 * </p>
 * <p>
 * 缓存配置通过 {@link ModelResultContext#getCacheConfig()} 获取，
 * 使用 {@link QueryCacheConfig} 类型安全地配置 L1/L2 缓存行为。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
public interface QueryCacheProvider {

    // ==================== L1 缓存：Token 级别 ====================

    /**
     * L1 缓存检查（在 beforeQuery 之后、query 之前）
     * <p>
     * 基于授权令牌和请求参数判断缓存命中。
     * 命中时可完全跳过 SQL 构建和执行。
     * </p>
     * <p>
     * 调用条件：
     * <ul>
     *   <li>authorization 不为空</li>
     *   <li>context.cacheConfig.l1Enabled = true</li>
     * </ul>
     * </p>
     *
     * @param context       查询上下文
     * @param authorization 授权令牌
     * @return 命中的缓存结果，或 null 表示未命中
     */
    default PagingResultImpl checkL1Cache(ModelResultContext context, String authorization) {
        return null;
    }

    /**
     * L1 缓存写入
     *
     * @param context       查询上下文
     * @param authorization 授权令牌
     * @param result        查询结果
     */
    default void writeL1Cache(ModelResultContext context, String authorization, PagingResultImpl result) {
        // no-op
    }

    // ==================== L2 缓存：SQL 级别 ====================

    /**
     * L2 缓存检查（在 SQL 生成后、执行前）
     * <p>
     * 基于最终 SQL（包含权限条件）和参数判断缓存命中。
     * 命中时可跳过 SQL 执行，但 SQL 构建已完成。
     * </p>
     * <p>
     * 调用条件：
     * <ul>
     *   <li>context.cacheConfig.l2Enabled = true（默认 true）</li>
     * </ul>
     * </p>
     *
     * @param modelName 模型名称
     * @param sql       最终 SQL（包含权限条件）
     * @param params    参数列表
     * @param context   查询上下文（用于获取分页信息等）
     * @return 命中的缓存结果，或 null 表示未命中
     */
    default PagingResultImpl checkL2Cache(String modelName, String sql, List<?> params, ModelResultContext context) {
        return null;
    }

    /**
     * L2 缓存写入
     *
     * @param modelName 模型名称
     * @param sql       最终 SQL
     * @param params    参数列表
     * @param result    查询结果
     * @param context   查询上下文
     */
    default void writeL2Cache(String modelName, String sql, List<?> params, PagingResultImpl result, ModelResultContext context) {
        // no-op
    }

    // ==================== 缓存管理 ====================

    /**
     * 清除指定模型的所有缓存（L1 + L2）
     *
     * @param modelName 模型名称
     */
    default void evict(String modelName) {
        // no-op
    }

    /**
     * 清除所有查询缓存（L1 + L2）
     */
    default void evictAll() {
        // no-op
    }

    /**
     * 获取缓存统计信息
     * <p>
     * 返回的 Map 可包含以下键：
     * <ul>
     *   <li>l1Hits / l1Misses：L1 缓存命中/未命中次数</li>
     *   <li>l2Hits / l2Misses：L2 缓存命中/未命中次数</li>
     *   <li>hitRate：总命中率</li>
     *   <li>type：缓存类型</li>
     * </ul>
     * </p>
     *
     * @return 统计信息
     */
    default Map<String, Object> getStats() {
        return Collections.emptyMap();
    }

    /**
     * 获取提供者优先级
     *
     * @return 优先级，数字越大优先级越高
     */
    default int getOrder() {
        return 0;
    }

    // ==================== 辅助方法 ====================

    /**
     * 判断是否启用 L1 缓存
     *
     * @param context 查询上下文
     * @return true 表示启用
     */
    static boolean isL1Enabled(ModelResultContext context) {
        if (context == null) {
            return false;
        }
        QueryCacheConfig config = context.getCacheConfig();
        return config != null && config.isL1Enabled();
    }

    /**
     * 判断是否启用 L2 缓存
     * <p>
     * 默认启用，除非 cacheConfig 显式设置为 false。
     * </p>
     *
     * @param context 查询上下文
     * @return true 表示启用
     */
    static boolean isL2Enabled(ModelResultContext context) {
        if (context == null) {
            return true; // 默认启用
        }
        QueryCacheConfig config = context.getCacheConfig();
        if (config == null) {
            return true; // 未配置时默认启用
        }
        return config.isL2Enabled();
    }

    /**
     * 获取授权令牌
     *
     * @param context 查询上下文
     * @return 授权令牌，或 null
     */
    static String getAuthorization(ModelResultContext context) {
        if (context == null) {
            return null;
        }
        // 从 SecurityContext 获取
        if (context.getSecurityContext() != null && context.getSecurityContext().getAuthorization() != null) {
            return context.getSecurityContext().getAuthorization();
        }
        return null;
    }

    /**
     * 获取或创建缓存配置
     * <p>
     * 如果 context 未设置 cacheConfig，返回默认配置（L2 启用）。
     * </p>
     *
     * @param context 查询上下文
     * @return 缓存配置，不会返回 null
     */
    static QueryCacheConfig getOrCreateConfig(ModelResultContext context) {
        if (context == null) {
            return QueryCacheConfig.defaultConfig();
        }
        QueryCacheConfig config = context.getCacheConfig();
        if (config == null) {
            config = QueryCacheConfig.defaultConfig();
            context.setCacheConfig(config);
        }
        return config;
    }

    /**
     * 标记 L1 缓存命中
     *
     * @param context 查询上下文
     */
    static void markL1Hit(ModelResultContext context) {
        if (context != null) {
            QueryCacheConfig config = getOrCreateConfig(context);
            config.setL1CacheHit(true);
        }
    }

    /**
     * 标记 L2 缓存命中
     *
     * @param context 查询上下文
     */
    static void markL2Hit(ModelResultContext context) {
        if (context != null) {
            QueryCacheConfig config = getOrCreateConfig(context);
            config.setL2CacheHit(true);
        }
    }
}
