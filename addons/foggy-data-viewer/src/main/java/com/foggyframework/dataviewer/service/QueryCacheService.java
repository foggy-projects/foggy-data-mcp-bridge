package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.repository.CachedQueryRepository;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 查询缓存服务
 * <p>
 * 负责缓存查询参数并生成唯一的查询ID
 */
@Slf4j
public class QueryCacheService {

    private final QueryContextStore store;
    private final DataViewerProperties properties;

    public QueryCacheService(QueryContextStore store, DataViewerProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    /** Keeps existing embedders using the Mongo repository constructor working. */
    public QueryCacheService(CachedQueryRepository repository, DataViewerProperties properties) {
        this(new MongoQueryContextStore(repository), properties);
    }

    /**
     * 缓存查询并生成唯一ID
     *
     * @param request       查询请求
     * @param authorization Legacy compatibility parameter. Authorization is
     *                      deliberately never stored in the cached context.
     * @return 缓存的查询上下文
     */
    public CachedQueryContext cacheQuery(OpenInViewerRequest request, String authorization) {
        String queryId = generateSecureId();

        CachedQueryContext ctx = CachedQueryContext.builder()
                .queryId(queryId)
                .model(request.getModel())
                .columns(request.getColumns())
                .slice(request.getSlice())
                .having(request.getHaving())
                .groupBy(request.getGroupBy())
                .orderBy(request.getOrderBy())
                .calculatedFields(request.getCalculatedFields())
                .extData(request.getExtData())
                .title(request.getTitle())
                .namespace(request.getNamespace())
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(properties.getCache().getTtlMinutes(), ChronoUnit.MINUTES))
                .build();

        // 构建 TableConfig
        ctx.setTableConfig(buildTableConfig(request));

        log.info("Cached query with ID: {} for model: {}", queryId, request.getModel());
        return store.save(ctx);
    }

    /**
     * 获取缓存的查询（如果未过期）
     *
     * @param queryId 查询ID
     * @return 查询上下文
     */
    public Optional<CachedQueryContext> getQuery(String queryId) {
        return getQuery(queryId, Instant.now());
    }

    public Optional<CachedQueryContext> getQuery(String queryId, Instant now) {
        return store.findActive(queryId, now);
    }

    /** Extend a still-live query when its one-time viewer link is redeemed. */
    public boolean extendExpiry(String queryId, String model, String namespace, Instant now, Instant expiresAt) {
        return store.extendExpiry(queryId, model, namespace, now, expiresAt);
    }

    /**
     * 更新预估行数
     *
     * @param queryId          查询ID
     * @param estimatedRowCount 预估行数
     */
    public void updateEstimatedRowCount(String queryId, Long estimatedRowCount) {
        store.updateEstimatedRowCount(queryId, estimatedRowCount, Instant.now());
    }

    /**
     * 生成安全的查询ID
     */
    private String generateSecureId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 构建 TableConfig
     */
    private CachedQueryContext.TableConfig buildTableConfig(OpenInViewerRequest request) {
        return CachedQueryContext.TableConfig.builder()
                .qmModel(request.getModel())
                .visibleColumns(request.getColumns() != null ? request.getColumns() : List.of())
                .customizations(request.getColumnCustomizations() != null
                        ? request.getColumnCustomizations()
                        : List.of())
                .build();
    }

    /**
     * 查询请求DTO
     * <p>
     * 使用类型安全的请求定义类，复用 foggy-dataset-model 中的结构
     */
    @lombok.Data
    public static class OpenInViewerRequest {
        private String model;
        private List<String> columns;
        private List<SliceRequestDef> slice;
        private List<SliceRequestDef> having;
        private List<GroupRequestDef> groupBy;
        private List<OrderRequestDef> orderBy;
        private List<CalculatedFieldDef> calculatedFields;
        private Object extData;
        private String title;
        private String namespace;

        /**
         * 可选的列定制配置
         */
        private List<CachedQueryContext.ColumnCustomization> columnCustomizations;
    }
}
