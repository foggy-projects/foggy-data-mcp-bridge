package com.foggyframework.dataset.db.model.cache.provider;

import com.foggyframework.dataset.db.model.cache.config.QueryCacheProperties;
import com.foggyframework.dataset.db.model.cache.fingerprint.QueryFingerprintBuilder;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.spi.QueryCacheProvider;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis 双层查询缓存提供者
 * <p>
 * 支持两层缓存：
 * <ul>
 *   <li><b>L1 缓存（Token 级别）</b>：基于授权令牌 + 请求指纹，可跳过 SQL 构建</li>
 *   <li><b>L2 缓存（SQL 级别）</b>：基于最终 SQL/Pipeline + 参数，精确匹配</li>
 * </ul>
 * </p>
 * <p>
 * 使用 Redis 作为分布式缓存存储。
 * 支持按模型配置 TTL、结果大小限制、缓存排除等特性。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
public class RedisQueryCacheProvider implements QueryCacheProvider {

    private static final String L1_PREFIX = "l1:";
    private static final String L2_PREFIX = "l2:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final QueryCacheKeyBuilder cacheKeyBuilder;
    private final QueryCacheProperties properties;

    // L1 缓存统计
    private final AtomicLong l1HitCount = new AtomicLong(0);
    private final AtomicLong l1MissCount = new AtomicLong(0);

    // L2 缓存统计
    private final AtomicLong l2HitCount = new AtomicLong(0);
    private final AtomicLong l2MissCount = new AtomicLong(0);

    // 跳过统计
    private final AtomicLong skipCount = new AtomicLong(0);

    public RedisQueryCacheProvider(
            RedisTemplate<String, Object> redisTemplate,
            QueryFingerprintBuilder fingerprintBuilder,
            QueryCacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.cacheKeyBuilder = new QueryCacheKeyBuilder(fingerprintBuilder, properties);
    }

    // ==================== L1 缓存：Token 级别 ====================

    @Override
    public PagingResultImpl checkL1Cache(ModelResultContext context, String authorization) {
        if (!properties.isEnabled()) {
            return null;
        }

        String modelName = cacheKeyBuilder.contextModelName(context);
        if (modelName == null) {
            l1MissCount.incrementAndGet();
            return null;
        }

        // 检查是否排除
        if (properties.isExcluded(modelName)) {
            skipCount.incrementAndGet();
            return null;
        }

        // 构建 L1 缓存键：authorization + 请求指纹
        String l1Key = cacheKeyBuilder.buildL1CacheKey(context, authorization);
        if (l1Key == null) {
            l1MissCount.incrementAndGet();
            return null;
        }

        try {
            Object cached = redisTemplate.opsForValue().get(l1Key);
            if (cached instanceof PagingResultImpl result) {
                l1HitCount.incrementAndGet();
                if (log.isDebugEnabled()) {
                    log.debug("L1 cache HIT: key={}, model={}", l1Key, modelName);
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("L1 cache read failed: key={}, error={}", l1Key, e.getMessage());
        }

        l1MissCount.incrementAndGet();
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
            log.debug("Skip L1 caching empty result for model={}", modelName);
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

        // 计算 TTL
        Duration ttl = calculateTtl(modelName);

        try {
            redisTemplate.opsForValue().set(l1Key, result, ttl);
            if (log.isDebugEnabled()) {
                log.debug("L1 cache WRITE: key={}, size={}, ttl={}", l1Key, resultSize, ttl);
            }
        } catch (Exception e) {
            log.warn("L1 cache write failed: key={}, error={}", l1Key, e.getMessage());
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
            skipCount.incrementAndGet();
            return null;
        }

        String l2Key = cacheKeyBuilder.buildL2CacheKey(modelName, sql, params, context);
        if (l2Key == null) {
            l2MissCount.incrementAndGet();
            return null;
        }

        try {
            Object cached = redisTemplate.opsForValue().get(l2Key);
            if (cached instanceof PagingResultImpl result) {
                l2HitCount.incrementAndGet();
                if (log.isDebugEnabled()) {
                    log.debug("L2 cache HIT: key={}, model={}", l2Key, modelName);
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("L2 cache read failed: key={}, error={}", l2Key, e.getMessage());
        }

        l2MissCount.incrementAndGet();
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
            log.debug("Skip L2 caching empty result for model={}", modelName);
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

        // 计算 TTL
        Duration ttl = calculateTtl(modelName);

        try {
            redisTemplate.opsForValue().set(l2Key, result, ttl);
            if (log.isDebugEnabled()) {
                log.debug("L2 cache WRITE: key={}, size={}, ttl={}", l2Key, resultSize, ttl);
            }
        } catch (Exception e) {
            log.warn("L2 cache write failed: key={}, error={}", l2Key, e.getMessage());
        }
    }

    // ==================== 缓存管理 ====================

    @Override
    public void evict(String modelName) {
        // 清除 L1 和 L2 缓存
        String l1Pattern = properties.getKeyPrefix() + L1_PREFIX + modelName + ":*";
        String l2Pattern = properties.getKeyPrefix() + L2_PREFIX + modelName + ":*";

        int total = 0;
        try {
            Set<String> l1Keys = redisTemplate.keys(l1Pattern);
            if (l1Keys != null && !l1Keys.isEmpty()) {
                redisTemplate.delete(l1Keys);
                total += l1Keys.size();
            }

            Set<String> l2Keys = redisTemplate.keys(l2Pattern);
            if (l2Keys != null && !l2Keys.isEmpty()) {
                redisTemplate.delete(l2Keys);
                total += l2Keys.size();
            }

            log.info("Evicted {} cache entries (L1+L2) for model: {}", total, modelName);
        } catch (Exception e) {
            log.error("Failed to evict cache for model {}: {}", modelName, e.getMessage());
        }
    }

    @Override
    public void evictAll() {
        String pattern = properties.getKeyPrefix() + "*";
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Evicted all {} query cache entries (L1+L2)", keys.size());
            }
        } catch (Exception e) {
            log.error("Failed to evict all cache: {}", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getStats() {
        long l1Hits = l1HitCount.get();
        long l1Misses = l1MissCount.get();
        long l2Hits = l2HitCount.get();
        long l2Misses = l2MissCount.get();
        long skips = skipCount.get();

        long totalHits = l1Hits + l2Hits;
        long totalRequests = l1Hits + l1Misses + l2Hits + l2Misses;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("type", "redis");
        stats.put("enabled", properties.isEnabled());
        stats.put("l1Hits", l1Hits);
        stats.put("l1Misses", l1Misses);
        stats.put("l2Hits", l2Hits);
        stats.put("l2Misses", l2Misses);
        stats.put("skips", skips);
        stats.put("hitRate", totalRequests > 0
                ? String.format("%.2f%%", (double) totalHits / totalRequests * 100)
                : "N/A");
        stats.put("defaultTtl", properties.getDefaultTtl().toString());
        return stats;
    }

    @Override
    public int getOrder() {
        return 100; // 较高优先级
    }

    // ==================== 私有方法 ====================

    /**
     * 计算 TTL，支持随机偏移
     *
     * @param modelName 模型名称
     * @return TTL
     */
    private Duration calculateTtl(String modelName) {
        Duration baseTtl = properties.getTtlForModel(modelName);

        // 添加随机偏移（防止缓存雪崩）
        if (properties.getRedis().isTtlJitter()) {
            int jitterSeconds = ThreadLocalRandom.current().nextInt(0, properties.getRedis().getTtlJitterMax() + 1);
            return baseTtl.plusSeconds(jitterSeconds);
        }

        return baseTtl;
    }
}
