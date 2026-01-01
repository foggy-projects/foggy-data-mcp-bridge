package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.domain.CachedQueryContext.ColumnSchema;
import com.foggyframework.dataviewer.repository.CachedQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 查询缓存服务
 * <p>
 * 负责缓存查询参数并生成唯一的查询ID
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryCacheService {

    private final CachedQueryRepository repository;
    private final DataViewerProperties properties;

    /**
     * 缓存查询并生成唯一ID
     *
     * @param request       查询请求
     * @param authorization 授权信息
     * @return 缓存的查询上下文
     */
    public CachedQueryContext cacheQuery(OpenInViewerRequest request, String authorization) {
        String queryId = generateSecureId();

        CachedQueryContext ctx = CachedQueryContext.builder()
                .queryId(queryId)
                .model(request.getModel())
                .columns(request.getColumns())
                .slice(request.getSlice())
                .groupBy(request.getGroupBy())
                .orderBy(request.getOrderBy())
                .title(request.getTitle())
                .authorization(authorization)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(properties.getCache().getTtlMinutes(), ChronoUnit.MINUTES))
                .build();

        // TODO: Fetch schema from model metadata
        ctx.setSchema(buildDefaultSchema(request.getColumns()));

        log.info("Cached query with ID: {} for model: {}", queryId, request.getModel());
        return repository.save(ctx);
    }

    /**
     * 获取缓存的查询（如果未过期）
     *
     * @param queryId 查询ID
     * @return 查询上下文
     */
    public Optional<CachedQueryContext> getQuery(String queryId) {
        return repository.findByQueryIdAndExpiresAtAfter(queryId, Instant.now());
    }

    /**
     * 更新预估行数
     *
     * @param queryId          查询ID
     * @param estimatedRowCount 预估行数
     */
    public void updateEstimatedRowCount(String queryId, Long estimatedRowCount) {
        getQuery(queryId).ifPresent(ctx -> {
            ctx.setEstimatedRowCount(estimatedRowCount);
            repository.save(ctx);
        });
    }

    /**
     * 生成安全的查询ID
     */
    private String generateSecureId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 构建默认的列元数据
     */
    private List<ColumnSchema> buildDefaultSchema(List<String> columns) {
        if (columns == null) {
            return List.of();
        }
        return columns.stream()
                .map(col -> ColumnSchema.builder()
                        .name(col)
                        .title(col)
                        .type("TEXT")
                        .filterable(true)
                        .aggregatable(false)
                        .build())
                .toList();
    }

    /**
     * 查询请求DTO
     */
    @lombok.Data
    public static class OpenInViewerRequest {
        private String model;
        private List<String> columns;
        private List<Map<String, Object>> slice;
        private List<Map<String, Object>> groupBy;
        private List<Map<String, Object>> orderBy;
        private String title;
    }
}
