package com.foggyframework.dataset.db.model.cache.provider;

import com.foggyframework.dataset.db.model.cache.config.QueryCacheProperties;
import com.foggyframework.dataset.db.model.cache.fingerprint.QueryFingerprintBuilder;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.spi.QueryCacheProvider;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine 双层本地缓存提供者
 * <p>
 * 支持两层缓存：
 * <ul>
 *   <li><b>L1 缓存（Token 级别）</b>：基于授权令牌 + 请求指纹，可跳过 SQL 构建</li>
 *   <li><b>L2 缓存（SQL 级别）</b>：基于最终 SQL/Pipeline + 参数，精确匹配</li>
 * </ul>
 * </p>
 * <p>
 * 使用 Caffeine 作为本地缓存存储查询结果。
 * 适用于单机部署或对延迟敏感的场景。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
public class CaffeineQueryCacheProvider implements QueryCacheProvider {

    private static final String L1_PREFIX = "l1:";
    private static final String L2_PREFIX = "l2:";

    private final Cache<String, PagingResultImpl> l1Cache;
    private final Cache<String, PagingResultImpl> l2Cache;
    private final QueryCacheKeyBuilder cacheKeyBuilder;
    private final QueryCacheProperties properties;

    public CaffeineQueryCacheProvider(
            QueryFingerprintBuilder fingerprintBuilder,
            QueryCacheProperties properties) {
        this.properties = properties;
        this.cacheKeyBuilder = new QueryCacheKeyBuilder(fingerprintBuilder, properties);

        // 构建 L1 Caffeine 缓存
        Caffeine<Object, Object> l1Builder = Caffeine.newBuilder()
                .maximumSize(properties.getCaffeine().getMaximumSize() / 2) // L1 和 L2 各一半
                .initialCapacity(properties.getCaffeine().getInitialCapacity() / 2)
                .expireAfterWrite(properties.getDefaultTtl().toMillis(), TimeUnit.MILLISECONDS);

        // 构建 L2 Caffeine 缓存
        Caffeine<Object, Object> l2Builder = Caffeine.newBuilder()
                .maximumSize(properties.getCaffeine().getMaximumSize() / 2)
                .initialCapacity(properties.getCaffeine().getInitialCapacity() / 2)
                .expireAfterWrite(properties.getDefaultTtl().toMillis(), TimeUnit.MILLISECONDS);

        if (properties.getCaffeine().isRecordStats()) {
            l1Builder.recordStats();
            l2Builder.recordStats();
        }

        this.l1Cache = l1Builder.build();
        this.l2Cache = l2Builder.build();

        log.info("Caffeine dual-layer query cache initialized: maxSize={}, ttl={}",
                properties.getCaffeine().getMaximumSize(),
                properties.getDefaultTtl());
    }

    // ==================== L1 缓存：Token 级别 ====================

    @Override
    public PagingResultImpl checkL1Cache(ModelResultContext context, String authorization) {
        if (!properties.isEnabled()) {
            return null;
        }

        String modelName = cacheKeyBuilder.contextModelName(context);
        if (modelName == null) {
            return null;
        }

        // 检查是否排除
        if (properties.isExcluded(modelName)) {
            return null;
        }

        // 构建 L1 缓存键
        String l1Key = cacheKeyBuilder.buildL1CacheKey(context, authorization);
        if (l1Key == null) {
            return null;
        }

        // 查询缓存
        PagingResultImpl cached = l1Cache.getIfPresent(l1Key);
        if (cached != null) {
            if (log.isDebugEnabled()) {
                log.debug("L1 cache HIT: key={}, model={}", l1Key, modelName);
            }
            return cached;
        }

        if (log.isDebugEnabled()) {
            log.debug("L1 cache MISS: key={}, model={}", l1Key, modelName);
        }
        return null;
    }

    @Override
    public void writeL1Cache(ModelResultContext context, String authorization, PagingResultImpl result) {
        if (!properties.isEnabled() || result == null) {
            return;
        }

        String modelName = cacheKeyBuilder.contextModelName(context);
        if (modelName == null) {
            return;
        }

        // 检查是否排除
        if (properties.isExcluded(modelName)) {
            return;
        }

        // 检查空结果
        if (!properties.isCacheEmptyResult() &&
            (result.getItems() == null || result.getItems().isEmpty())) {
            return;
        }

        // 检查结果大小限制
        int resultSize = result.getItems() != null ? result.getItems().size() : 0;
        if (properties.getMaxResultSize() > 0 && resultSize > properties.getMaxResultSize()) {
            log.debug("Skip L1 caching large result: model={}, size={}", modelName, resultSize);
            return;
        }

        // 构建 L1 缓存键
        String l1Key = cacheKeyBuilder.buildL1CacheKey(context, authorization);
        if (l1Key == null) {
            return;
        }

        l1Cache.put(l1Key, result);

        if (log.isDebugEnabled()) {
            log.debug("L1 cache WRITE: key={}, size={}", l1Key, resultSize);
        }
    }

    // ==================== L2 缓存：SQL 级别 ====================

    @Override
    public PagingResultImpl checkL2Cache(String modelName, String sql, List<?> params, ModelResultContext context) {
        if (!properties.isEnabled()) {
            return null;
        }

        // 检查是否排除
        if (properties.isExcluded(modelName)) {
            return null;
        }

        // 构建 L2 缓存键
        String l2Key = cacheKeyBuilder.buildL2CacheKey(modelName, sql, params, context);
        if (l2Key == null) {
            return null;
        }

        // 查询缓存
        PagingResultImpl cached = l2Cache.getIfPresent(l2Key);
        if (cached != null) {
            if (log.isDebugEnabled()) {
                log.debug("L2 cache HIT: key={}, model={}", l2Key, modelName);
            }
            return cached;
        }

        if (log.isDebugEnabled()) {
            log.debug("L2 cache MISS: key={}, model={}", l2Key, modelName);
        }
        return null;
    }

    @Override
    public void writeL2Cache(String modelName, String sql, List<?> params, PagingResultImpl result, ModelResultContext context) {
        if (!properties.isEnabled() || result == null) {
            return;
        }

        // 检查是否排除
        if (properties.isExcluded(modelName)) {
            return;
        }

        // 检查空结果
        if (!properties.isCacheEmptyResult() &&
            (result.getItems() == null || result.getItems().isEmpty())) {
            return;
        }

        // 检查结果大小限制
        int resultSize = result.getItems() != null ? result.getItems().size() : 0;
        if (properties.getMaxResultSize() > 0 && resultSize > properties.getMaxResultSize()) {
            log.debug("Skip L2 caching large result: model={}, size={}", modelName, resultSize);
            return;
        }

        // 构建 L2 缓存键
        String l2Key = cacheKeyBuilder.buildL2CacheKey(modelName, sql, params, context);
        if (l2Key == null) {
            return;
        }

        l2Cache.put(l2Key, result);

        if (log.isDebugEnabled()) {
            log.debug("L2 cache WRITE: key={}, size={}", l2Key, resultSize);
        }
    }

    // ==================== 缓存管理 ====================

    @Override
    public void evict(String modelName) {
        String l1Prefix = properties.getKeyPrefix() + L1_PREFIX + modelName + ":";
        String l2Prefix = properties.getKeyPrefix() + L2_PREFIX + modelName + ":";

        long l1Evicted = l1Cache.asMap().keySet().stream()
                .filter(key -> key.startsWith(l1Prefix))
                .peek(l1Cache::invalidate)
                .count();

        long l2Evicted = l2Cache.asMap().keySet().stream()
                .filter(key -> key.startsWith(l2Prefix))
                .peek(l2Cache::invalidate)
                .count();

        log.info("Evicted {} cache entries (L1={}, L2={}) for model: {}",
                l1Evicted + l2Evicted, l1Evicted, l2Evicted, modelName);
    }

    @Override
    public void evictAll() {
        long l1Size = l1Cache.estimatedSize();
        long l2Size = l2Cache.estimatedSize();

        l1Cache.invalidateAll();
        l2Cache.invalidateAll();

        log.info("Evicted all {} query cache entries (L1={}, L2={})", l1Size + l2Size, l1Size, l2Size);
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("type", "caffeine");
        stats.put("enabled", properties.isEnabled());
        stats.put("l1EstimatedSize", l1Cache.estimatedSize());
        stats.put("l2EstimatedSize", l2Cache.estimatedSize());
        stats.put("maximumSize", properties.getCaffeine().getMaximumSize());
        stats.put("defaultTtl", properties.getDefaultTtl().toString());

        if (properties.getCaffeine().isRecordStats()) {
            CacheStats l1Stats = l1Cache.stats();
            CacheStats l2Stats = l2Cache.stats();

            stats.put("l1Hits", l1Stats.hitCount());
            stats.put("l1Misses", l1Stats.missCount());
            stats.put("l2Hits", l2Stats.hitCount());
            stats.put("l2Misses", l2Stats.missCount());

            long totalHits = l1Stats.hitCount() + l2Stats.hitCount();
            long totalRequests = l1Stats.hitCount() + l1Stats.missCount() + l2Stats.hitCount() + l2Stats.missCount();
            stats.put("hitRate", totalRequests > 0
                    ? String.format("%.2f%%", (double) totalHits / totalRequests * 100)
                    : "N/A");
            stats.put("l1Evictions", l1Stats.evictionCount());
            stats.put("l2Evictions", l2Stats.evictionCount());
        }

        return stats;
    }

    @Override
    public int getOrder() {
        return 100;
    }

}
