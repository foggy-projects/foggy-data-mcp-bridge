package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.domain.CachedQueryContext.ColumnSchema;
import com.foggyframework.dataviewer.domain.CachedQueryContext.DictItem;
import com.foggyframework.dataviewer.repository.CachedQueryRepository;
import com.foggyframework.dataset.db.model.def.dict.DbDictDef;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.spi.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

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
    private final QueryModelLoader queryModelLoader;

    @Autowired(required = false)
    private DbModelDictService dictService;

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
                .calculatedFields(request.getCalculatedFields())
                .title(request.getTitle())
                .authorization(authorization)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(properties.getCache().getTtlMinutes(), ChronoUnit.MINUTES))
                .build();

        // 从 QueryModel 获取真实的 schema
        ctx.setSchema(buildSchemaFromModel(request.getModel(), request.getColumns()));

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
     * 从 QueryModel 构建列元数据
     */
    private List<ColumnSchema> buildSchemaFromModel(String modelName, List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            return List.of();
        }

        try {
            QueryModel queryModel = queryModelLoader.getJdbcQueryModel(modelName);
            if (queryModel == null) {
                log.warn("QueryModel not found: {}, using default schema", modelName);
                return buildDefaultSchema(columns);
            }

            List<ColumnSchema> schemas = new ArrayList<>();
            for (String columnName : columns) {
                ColumnSchema schema = buildColumnSchema(queryModel, columnName);
                schemas.add(schema);
            }
            return schemas;
        } catch (Exception e) {
            log.warn("Failed to load QueryModel: {}, using default schema. Error: {}",
                    modelName, e.getMessage());
            return buildDefaultSchema(columns);
        }
    }

    /**
     * 构建单个列的 schema
     */
    private ColumnSchema buildColumnSchema(QueryModel queryModel, String columnName) {
        try {
            DbQueryColumn queryColumn = queryModel.findJdbcQueryColumnByName(columnName, false);
            if (queryColumn != null) {
                DbColumn selectColumn = queryColumn.getSelectColumn();
                DbColumnType colType = queryColumn.getType();
                String typeName = colType != null ? colType.name() : "TEXT";

                ColumnSchema.ColumnSchemaBuilder builder = ColumnSchema.builder()
                        .name(columnName)
                        .title(queryColumn.getCaption() != null ? queryColumn.getCaption() : columnName)
                        .type(typeName)
                        .filterable(true)
                        .aggregatable(false);

                // 推断过滤器类型
                String filterType = inferFilterType(selectColumn, colType);
                builder.filterType(filterType);

                // 是否为度量
                builder.measure(selectColumn != null && selectColumn.isMeasure());

                // 处理字典类型
                if ("dict".equals(filterType)) {
                    populateDictMetadata(builder, selectColumn);
                }

                // 处理维度类型
                if ("dimension".equals(filterType)) {
                    populateDimensionMetadata(builder, selectColumn, columnName);
                }

                // 处理日期格式
                if ("date".equals(filterType) || "datetime".equals(filterType)) {
                    populateDateMetadata(builder, selectColumn, colType);
                }

                // 提取 extData.ui 配置
                populateUiConfig(builder, selectColumn);

                return builder.build();
            }
        } catch (Exception e) {
            log.debug("Column {} not found in QueryModel, using default", columnName);
        }

        // 默认 schema
        return ColumnSchema.builder()
                .name(columnName)
                .title(columnName)
                .type("TEXT")
                .filterType("text")
                .filterable(true)
                .aggregatable(false)
                .build();
    }

    /**
     * 推断过滤器类型
     */
    private String inferFilterType(DbColumn selectColumn, DbColumnType colType) {
        // 1. 检查是否有自定义 filterType
        if (selectColumn != null) {
            Object extData = selectColumn.getExtData();
            if (extData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> extMap = (Map<String, Object>) extData;
                Object uiConfig = extMap.get("ui");
                if (uiConfig instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> ui = (Map<String, Object>) uiConfig;
                    String customType = (String) ui.get("filterType");
                    if ("custom".equals(customType)) {
                        return "custom";
                    }
                }
            }
        }

        // 2. 检查是否为维度列
        if (selectColumn != null && selectColumn.isDimension()) {
            return "dimension";
        }

        // 3. 检查是否为字典属性
        if (selectColumn instanceof DbProperty) {
            DbProperty prop = (DbProperty) selectColumn;
            if (prop.isDict()) {
                return "dict";
            }
        }

        // 4. 根据 DbColumnType 推断
        if (colType == null) return "text";

        switch (colType) {
            case DAY:
                return "date";
            case DATETIME:
                return "datetime";
            case NUMBER:
            case MONEY:
            case INTEGER:
            case BIGINT:
                return "number";
            case BOOL:
                return "bool";
            case DICT:
                return "dict";
            default:
                return "text";
        }
    }

    /**
     * 填充字典元数据
     */
    private void populateDictMetadata(ColumnSchema.ColumnSchemaBuilder builder, DbColumn selectColumn) {
        if (!(selectColumn instanceof DbProperty)) {
            return;
        }

        DbProperty prop = (DbProperty) selectColumn;
        String dictRef = prop.getDictRef();

        if (dictRef != null && !dictRef.isEmpty() && dictService != null) {
            builder.dictId(dictRef);
            DbDictDef dictDef = dictService.getDictById(dictRef);
            if (dictDef != null && dictDef.getItems() != null) {
                List<DictItem> items = dictDef.getItems().stream()
                        .map(item -> DictItem.builder()
                                .value(item.getValue())
                                .label(item.getLabel())
                                .build())
                        .toList();
                builder.dictItems(items);
            }
        }
    }

    /**
     * 填充维度元数据
     */
    private void populateDimensionMetadata(ColumnSchema.ColumnSchemaBuilder builder, DbColumn selectColumn, String columnName) {
        // 从列名中提取维度引用
        // 例如: product$caption -> product, store$id -> store
        if (columnName.contains("$")) {
            String dimRef = columnName.substring(0, columnName.indexOf("$"));
            builder.dimensionRef(dimRef);
        } else if (selectColumn != null && selectColumn.isDimension()) {
            builder.dimensionRef(selectColumn.getName());
        }
    }

    /**
     * 填充日期元数据
     */
    private void populateDateMetadata(ColumnSchema.ColumnSchemaBuilder builder, DbColumn selectColumn, DbColumnType colType) {
        String format = null;

        // 尝试从属性获取格式
        if (selectColumn instanceof DbProperty) {
            DbProperty prop = (DbProperty) selectColumn;
            format = prop.getFormat();
        }

        // 默认格式
        if (format == null || format.isEmpty()) {
            format = colType == DbColumnType.DATETIME ? "yyyy-MM-dd HH:mm:ss" : "yyyy-MM-dd";
        }

        builder.format(format);
    }

    /**
     * 提取 extData.ui 配置
     */
    @SuppressWarnings("unchecked")
    private void populateUiConfig(ColumnSchema.ColumnSchemaBuilder builder, DbColumn selectColumn) {
        if (selectColumn == null) {
            return;
        }

        Object extData = selectColumn.getExtData();
        if (extData instanceof Map) {
            Map<String, Object> extMap = (Map<String, Object>) extData;
            Object uiConfig = extMap.get("ui");
            if (uiConfig instanceof Map) {
                builder.uiConfig((Map<String, Object>) uiConfig);
            }
        }
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
                        .filterType("text")
                        .filterable(true)
                        .aggregatable(false)
                        .build())
                .toList();
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
        private List<GroupRequestDef> groupBy;
        private List<OrderRequestDef> orderBy;
        private List<CalculatedFieldDef> calculatedFields;
        private String title;
    }
}
