package com.foggyframework.dataset.model.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 查询缓存配置属性
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Data
@ConfigurationProperties(prefix = "foggy.query-cache")
public class QueryCacheProperties {

    /**
     * 是否启用查询缓存
     */
    private boolean enabled = true;

    /**
     * 缓存类型
     * <ul>
     *   <li>redis - 使用 Redis 分布式缓存</li>
     *   <li>caffeine - 使用 Caffeine 本地缓存</li>
     *   <li>none - 禁用缓存</li>
     * </ul>
     */
    private String type = "redis";

    /**
     * 默认 TTL（生存时间）
     */
    private Duration defaultTtl = Duration.ofMinutes(5);

    /**
     * 按模型配置的 TTL
     * <p>
     * 例如:
     * <pre>
     * foggy.query-cache.model-ttl.FactOrders=10m
     * foggy.query-cache.model-ttl.DimCustomer=1h
     * </pre>
     * </p>
     */
    private Map<String, Duration> modelTtl = new HashMap<>();

    /**
     * 最大缓存结果条数
     * <p>
     * 超过此数量的查询结果不会被缓存。
     * 设置为 0 表示不限制。
     * </p>
     */
    private int maxResultSize = 10000;

    /**
     * 排除的模型列表
     * <p>
     * 这些模型的查询永远不会被缓存。
     * 适用于实时性要求高的模型。
     * </p>
     */
    private Set<String> excludeModels = new HashSet<>();

    /**
     * 是否缓存空结果
     */
    private boolean cacheEmptyResult = true;

    /**
     * 缓存键前缀
     */
    private String keyPrefix = "qc:";

    /**
     * Redis 配置（仅当 type=redis 时有效）
     */
    private RedisConfig redis = new RedisConfig();

    /**
     * Caffeine 配置（仅当 type=caffeine 时有效）
     */
    private CaffeineConfig caffeine = new CaffeineConfig();

    /**
     * Redis 特定配置
     */
    @Data
    public static class RedisConfig {
        /**
         * 缓存击穿保护：是否使用分布式锁
         */
        private boolean useLock = false;

        /**
         * 分布式锁等待超时
         */
        private Duration lockTimeout = Duration.ofSeconds(5);

        /**
         * 是否在 TTL 上添加随机偏移（防止缓存雪崩）
         */
        private boolean ttlJitter = true;

        /**
         * TTL 随机偏移最大值（秒）
         */
        private int ttlJitterMax = 60;
    }

    /**
     * Caffeine 特定配置
     */
    @Data
    public static class CaffeineConfig {
        /**
         * 最大缓存条目数
         */
        private int maximumSize = 1000;

        /**
         * 初始容量
         */
        private int initialCapacity = 100;

        /**
         * 是否记录统计信息
         */
        private boolean recordStats = true;
    }

    /**
     * 获取指定模型的 TTL
     *
     * @param modelName 模型名称
     * @return TTL，如果未配置则返回默认 TTL
     */
    public Duration getTtlForModel(String modelName) {
        Duration modelSpecificTtl = modelTtl.get(modelName);
        return modelSpecificTtl != null ? modelSpecificTtl : defaultTtl;
    }

    /**
     * 判断模型是否被排除
     *
     * @param modelName 模型名称
     * @return true 表示不缓存此模型
     */
    public boolean isExcluded(String modelName) {
        return excludeModels.contains(modelName);
    }
}
