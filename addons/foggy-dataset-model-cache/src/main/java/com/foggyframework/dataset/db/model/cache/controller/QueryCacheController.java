package com.foggyframework.dataset.db.model.cache.controller;

import com.foggyframework.dataset.db.model.cache.config.QueryCacheProperties;
import com.foggyframework.dataset.db.model.spi.QueryCacheProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 查询缓存管理 REST API
 * <p>
 * 提供缓存统计、清除等管理功能。
 * </p>
 * <p>
 * 端点：
 * <ul>
 *   <li>GET /api/query-cache/stats - 获取缓存统计</li>
 *   <li>DELETE /api/query-cache/evict/{modelName} - 清除指定模型的缓存</li>
 *   <li>DELETE /api/query-cache/evict-all - 清除所有缓存</li>
 * </ul>
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
@RestController
@RequestMapping("/api/query-cache")
@RequiredArgsConstructor
public class QueryCacheController {

    private final QueryCacheProvider queryCacheProvider;
    private final QueryCacheProperties properties;

    /**
     * 获取缓存统计信息
     *
     * @return 统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> response = new LinkedHashMap<>();

        // 配置信息
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", properties.isEnabled());
        config.put("type", properties.getType());
        config.put("defaultTtl", properties.getDefaultTtl().toString());
        config.put("maxResultSize", properties.getMaxResultSize());
        config.put("cacheEmptyResult", properties.isCacheEmptyResult());
        config.put("excludeModels", properties.getExcludeModels());
        response.put("config", config);

        // 运行时统计
        Map<String, Object> stats = queryCacheProvider.getStats();
        response.put("stats", stats);

        return ResponseEntity.ok(response);
    }

    /**
     * 清除指定模型的缓存
     *
     * @param modelName 模型名称
     * @return 操作结果
     */
    @DeleteMapping("/evict/{modelName}")
    public ResponseEntity<Map<String, Object>> evict(@PathVariable String modelName) {
        log.info("Evicting cache for model: {}", modelName);

        queryCacheProvider.evict(modelName);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("model", modelName);
        response.put("message", "Cache evicted for model: " + modelName);

        return ResponseEntity.ok(response);
    }

    /**
     * 清除所有缓存
     *
     * @return 操作结果
     */
    @DeleteMapping("/evict-all")
    public ResponseEntity<Map<String, Object>> evictAll() {
        log.info("Evicting all query cache");

        queryCacheProvider.evictAll();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "All query cache evicted");

        return ResponseEntity.ok(response);
    }

    /**
     * 批量清除指定模型的缓存
     *
     * @param request 包含模型名称列表的请求
     * @return 操作结果
     */
    @PostMapping("/evict-batch")
    public ResponseEntity<Map<String, Object>> evictBatch(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        java.util.List<String> models = (java.util.List<String>) request.get("models");

        if (models == null || models.isEmpty()) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("message", "No models specified");
            return ResponseEntity.badRequest().body(response);
        }

        log.info("Evicting cache for models: {}", models);

        for (String model : models) {
            queryCacheProvider.evict(model);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("models", models);
        response.put("message", "Cache evicted for " + models.size() + " models");

        return ResponseEntity.ok(response);
    }
}
