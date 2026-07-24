package com.foggyframework.dataset.model.engine.pivot.rollup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Rollup 辅助查询结果缓存
 *
 * <p>存储辅助聚合查询的结果，供 cache-aware SubtotalInjector 使用。</p>
 *
 * <p>Cache key = grainKey + 结构化坐标字符串化。</p>
 */
public class RollupCache {

    private static final Logger logger = LoggerFactory.getLogger(RollupCache.class);

    /**
     * 缓存结构: grainKey → coordKey → metricName → value
     */
    private final Map<String, Map<String, Map<String, Object>>> cache = new LinkedHashMap<>();

    /**
     * 写入缓存
     *
     * @param grainKey     grain 标识
     * @param coordinates  结构化坐标（有序）
     * @param metricValues metric → value 映射
     */
    public void put(String grainKey, List<RollupCoordinate> coordinates, Map<String, Object> metricValues) {
        String coordKey = buildCoordKey(coordinates);
        cache.computeIfAbsent(grainKey, k -> new LinkedHashMap<>())
                .put(coordKey, metricValues);
    }

    /**
     * 读取缓存中某个 metric 的值
     *
     * @param grainKey    grain 标识
     * @param coordinates 结构化坐标
     * @param metricName  度量名
     * @return 度量值
     * @throws IllegalStateException 如果 cache miss
     */
    public Object get(String grainKey, List<RollupCoordinate> coordinates, String metricName) {
        String coordKey = buildCoordKey(coordinates);
        Map<String, Map<String, Object>> grainCache = cache.get(grainKey);
        if (grainCache == null) {
            throw new IllegalStateException(
                    "[Pivot] Rollup cache miss: grainKey=" + grainKey + ", coordKey=" + coordKey);
        }
        Map<String, Object> metricValues = grainCache.get(coordKey);
        if (metricValues == null) {
            throw new IllegalStateException(
                    "[Pivot] Rollup cache miss: grainKey=" + grainKey + ", coordKey=" + coordKey);
        }
        return metricValues.get(metricName);
    }

    /**
     * 尝试读取（不抛异常）
     */
    public Object getOrNull(String grainKey, List<RollupCoordinate> coordinates, String metricName) {
        String coordKey = buildCoordKey(coordinates);
        Map<String, Map<String, Object>> grainCache = cache.get(grainKey);
        if (grainCache == null) return null;
        Map<String, Object> metricValues = grainCache.get(coordKey);
        if (metricValues == null) return null;
        return metricValues.get(metricName);
    }

    /**
     * 是否为空
     */
    public boolean isEmpty() {
        return cache.isEmpty();
    }

    /**
     * 总条目数
     */
    public int size() {
        return cache.values().stream().mapToInt(Map::size).sum();
    }

    /**
     * 从 grain key 构建坐标索引 key
     */
    private String buildCoordKey(List<RollupCoordinate> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (RollupCoordinate coord : coordinates) {
            if (sb.length() > 0) sb.append('\u001F');
            sb.append(coord.field()).append('=').append(coord.displayValue());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "RollupCache{grains=" + cache.size() + ", entries=" + size() + "}";
    }
}
