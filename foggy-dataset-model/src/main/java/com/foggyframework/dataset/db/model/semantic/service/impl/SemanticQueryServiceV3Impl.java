package com.foggyframework.dataset.db.model.semantic.service.impl;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.JsonUtils;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.service.DimensionMemberLoader;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.db.model.spi.DbQueryColumn;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * V3版本语义查询服务实现
 *
 * <p>核心简化：字段名直接使用，无需判断和拼接后缀</p>
 *
 * <p>与V2的区别：</p>
 * <ul>
 *   <li>不再需要将 $caption 归一化为 $id（因为 $caption 已经是独立字段）</li>
 *   <li>不再需要自动补全 $id/$caption 后缀</li>
 *   <li>所有字段直接透传给底层服务</li>
 * </ul>
 *
 * <p>V3 仍然保留的功能：</p>
 * <ul>
 *   <li>slice 中 $caption 字段的值转换（caption值 -> id值）</li>
 *   <li>columns 和 groupBy 的对齐校验</li>
 * </ul>
 */
@Service
public class SemanticQueryServiceV3Impl implements SemanticQueryServiceV3 {

    private static final Logger logger = LoggerFactory.getLogger(SemanticQueryServiceV3Impl.class);

    @Resource
    private QueryFacade queryFacade;

    @Resource
    private QueryModelLoader queryModelLoader;

    @Resource
    private DimensionMemberLoader dimensionMemberLoader;

    @Override
    public SemanticQueryResponse queryModel(String model, SemanticQueryRequest request, String mode) {
        return queryModel(model, request, mode, null);
    }

    @Override
    public SemanticQueryResponse queryModel(String model, SemanticQueryRequest request, String mode,
                                            ModelResultContext.SecurityContext securityContext) {
        if ("validate".equals(mode)) {
            return validateQuery(model, request);
        }

        if (request.getColumns() == null || request.getColumns().isEmpty()) {
            throw RX.throwB("请指定查询字段");
        }

        long startTime = System.currentTimeMillis();

        // 1. 创建上下文
        QueryContextV3 context = new QueryContextV3();
        context.model = model;
        context.originalRequest = request;

        // 2. 构建初始JDBC请求
        PagingRequest<DbQueryRequestDef> jdbcRequest = buildJdbcRequest(model, request, context);

        // 3. 处理 slice 中的 $caption 值转换（如果需要）
        // 注意：这里在 beforeQuery 之前处理，因为需要先转换好 slice
        if (request.getSlice() != null) {
            List<SliceRequestDef> processedSlice = processSliceValues(model, request.getSlice(), request, context);
            jdbcRequest.getParam().setSlice(processedSlice);
        }

        // 4. 创建ModelResultContext，标记为语义查询，设置SecurityContext
        ModelResultContext resultContext = new ModelResultContext();
        resultContext.setRequest(jdbcRequest);
        resultContext.setQueryType(ModelResultContext.QueryType.SEMANTIC);
        resultContext.setSecurityContext(securityContext);

        // 5. 使用 QueryFacade 执行完整查询生命周期（beforeQuery -> query -> process）
        DbQueryResult dbQueryResult = queryFacade.queryModelResult(resultContext);
        PagingResultImpl queryResult = resultContext.getPagingResult();
        context.extData = resultContext.getExtData();

        // 6. 构建响应
        SemanticQueryResponse response = buildResponse(
                jdbcRequest.getParam(),
                queryResult,
                context,
                dbQueryResult.getQueryEngine().getJdbcQueryModel()
        );

        // 7. 添加调试信息
        if (logger.isDebugEnabled()) {
            addDebugInfo(response, context, startTime);
        }

        return response;
    }

    @Override
    public SemanticQueryResponse validateQuery(String model, SemanticQueryRequest request) {
        SemanticQueryResponse response = new SemanticQueryResponse();

        // V3 的验证主要检查字段是否存在
        QueryModel queryModel = queryModelLoader.getJdbcQueryModel(model);
        if (queryModel == null) {
            throw RX.throwB("模型不存在: " + model);
        }

        List<String> warnings = new ArrayList<>();

        // 检查 columns 中的字段
        if (request.getColumns() != null) {
            for (String col : request.getColumns()) {
                DbQueryColumn queryColumn = queryModel.findJdbcQueryColumnByName(col, false);
                if (queryColumn == null) {
                    warnings.add("字段不存在: " + col);
                }
            }
        }
        if (request.getSlice() != null) {
            for (SemanticQueryRequest.SliceItem slice : request.getSlice()) {
                if (StringUtils.isEmpty(slice.getField())) {
                    throw RX.throwB(JsonUtils.toJson(slice) + "中的name字段不能为空");
                }
            }
        }

        // 检查 groupBy 和 columns 的对齐
        if (request.getGroupBy() != null && request.getColumns() != null) {
            Set<String> columnSet = new HashSet<>(request.getColumns());
            for (SemanticQueryRequest.GroupByItem item : request.getGroupBy()) {
                // 非度量字段（没有聚合类型）必须在 columns 中
                if ((item.getAgg() == null || item.getAgg().isEmpty())
                        && !columnSet.contains(item.getField())) {
                    warnings.add("groupBy 字段 " + item.getField() + " 必须出现在 columns 中");
                }
            }
        }

        response.setWarnings(warnings.isEmpty() ? null : warnings);
        return response;
    }

    /**
     * 构建JDBC查询请求（V3版本：直接透传字段名）
     */
    private PagingRequest<DbQueryRequestDef> buildJdbcRequest(String model, SemanticQueryRequest request, QueryContextV3 context) {
        DbQueryRequestDef queryDef = new DbQueryRequestDef();
        queryDef.setQueryModel(model);
        queryDef.setReturnTotal(request.getReturnTotal());
        queryDef.setStrictColumns(true);

        // 获取模型定义用于字段校验
        QueryModel queryModel = queryModelLoader.getJdbcQueryModel(model);

        // 复制 columns 和 groupBy 以便修改
        List<String> columns = new ArrayList<>(request.getColumns());
        List<SemanticQueryRequest.GroupByItem> groupByItems = request.getGroupBy() != null
                ? new ArrayList<>(request.getGroupBy())
                : null;

        // 自动对齐 columns 和 groupBy 中的维度字段
        if (groupByItems != null && !groupByItems.isEmpty()) {
            alignColumnsAndGroupBy(columns, groupByItems, queryModel, context);

            // 校验 groupBy 中的非度量字段必须在 columns 中
            validateGroupByFieldsInColumns(groupByItems, columns);
        }

        queryDef.setColumns(columns);

        // 转换过滤条件（V3：字段名直接使用）
        if (request.getSlice() != null) {
            List<SliceRequestDef> jdbcSlice = request.getSlice().stream()
                    .map(this::convertToJdbcSlice)
                    .collect(Collectors.toList());
            queryDef.setSlice(jdbcSlice);
        }

        // 转换分组（V3：字段名直接使用）
        if (groupByItems != null) {
            List<GroupRequestDef> jdbcGroupBy = groupByItems.stream()
                    .map(item -> {
                        GroupRequestDef group = new GroupRequestDef();
                        group.setField(item.getField());
                        group.setAgg(item.getAgg());
                        return group;
                    })
                    .collect(Collectors.toList());
            queryDef.setGroupBy(jdbcGroupBy);
        }

        // 转换排序（V3：对维度字段自动补充后缀）
        if (request.getOrderBy() != null) {
            List<OrderRequestDef> jdbcOrderBy = request.getOrderBy().stream()
                    .map(item -> {
                        OrderRequestDef order = new OrderRequestDef();
                        String field = normalizeOrderByField(item.getField(), queryModel, context);
                        order.setField(field);
                        order.setOrder(item.getDir());
                        return order;
                    })
                    .collect(Collectors.toList());
            queryDef.setOrderBy(jdbcOrderBy);
        }

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryDef);

        if (request.getStart() != null) {
            pagingRequest.setStart(request.getStart());
        }
        if (request.getLimit() != null) {
            pagingRequest.setPageSize(request.getLimit());
        }

        return pagingRequest;
    }

    /**
     * 处理 slice 中的值转换
     *
     * <p>V3 仍然需要处理的场景：</p>
     * <ul>
     *   <li>当 slice 使用 $caption 字段且传入的是 caption 值时，需要转换为 id 值</li>
     *   <li>例如：slice 使用 customer$caption = "张三"，需要转为对应的 customer_id</li>
     * </ul>
     */
    private List<SliceRequestDef> processSliceValues(String model, List<SemanticQueryRequest.SliceItem> slice,
                                                     SemanticQueryRequest request, QueryContextV3 context) {
        List<SliceRequestDef> processed = new ArrayList<>();

        for (SemanticQueryRequest.SliceItem item : slice) {
            SliceRequestDef sliceDef = new SliceRequestDef();

            // 检查是否是 $caption 字段
            if (item.getField().endsWith("$caption")) {
                // 需要将 caption 值转换为查询 caption 字段的条件
                // 但如果用户明确使用 $caption 字段，说明他想直接查询 caption 列
                // V3 版本：直接使用字段名，不做值转换
                sliceDef.setField(item.getField());
                sliceDef.setOp(item.getOp());
                sliceDef.setValue(item.getValue());
            } else {
                // 其他字段直接透传
                sliceDef.setField(item.getField());
                sliceDef.setOp(item.getOp());
                sliceDef.setValue(item.getValue());
            }

            processed.add(sliceDef);
        }

        return processed;
    }

    private SliceRequestDef convertToJdbcSlice(SemanticQueryRequest.SliceItem item) {
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField(item.getField());
        slice.setOp(item.getOp());
        slice.setValue(item.getValue());
        return slice;
    }

    /**
     * 构建响应
     */
    private SemanticQueryResponse buildResponse(DbQueryRequestDef request, PagingResultImpl queryResult,
                                                QueryContextV3 context, QueryModel queryModel) {
        SemanticQueryResponse response = new SemanticQueryResponse();

        // 转换数据项
        int returnedCount = 0;
        if (queryResult.getItems() != null) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Object row : queryResult.getItems()) {
                if (row instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> item = (Map<String, Object>) row;
                    items.add(item);
                } else {
                    Map<String, Object> item = new HashMap<>();
                    item.put("data", row);
                    items.add(item);
                }
            }
            response.setItems(items);
            returnedCount = items.size();
        }

        // 获取实际使用的分页参数
        int actualStart = queryResult.getStart();
        int actualLimit = queryResult.getLimit();
        Long totalCount = queryResult.getTotal() > 0 ? queryResult.getTotal() : null;

        // 判断是否有更多数据
        boolean hasMore = false;
        if (totalCount != null) {
            // 有总数时，精确判断
            hasMore = (actualStart + returnedCount) < totalCount;
        } else {
            // 无总数时，根据返回条数判断（返回条数等于 limit 说明可能有更多）
            hasMore = returnedCount > 0 && returnedCount >= actualLimit;
        }

        // 构建分页信息
        SemanticQueryResponse.PaginationInfo pagination = new SemanticQueryResponse.PaginationInfo();
        pagination.setStart(actualStart);
        pagination.setLimit(actualLimit);
        pagination.setReturned(returnedCount);
        pagination.setTotalCount(totalCount);
        pagination.setHasMore(hasMore);
        pagination.setRangeDescription(buildRangeDescription(actualStart, returnedCount, totalCount, hasMore));
        response.setPagination(pagination);

        // 设置分页信息（保留原有字段以保持兼容性）
        response.setTotal(queryResult.getTotal());
        response.setHasNext(hasMore);
        response.setTotalData(queryResult.getTotalData());

        // 设置警告信息
        response.setWarnings(context.warnings.isEmpty() ? null : context.warnings);

        // 构建 Schema 信息（包含 summary）
        response.setSchema(buildSchemaInfo(queryModel, request, queryResult));

        return response;
    }

    /**
     * 构建数据范围描述（人类可读）
     */
    private String buildRangeDescription(int start, int returned, Long totalCount, boolean hasMore) {
        if (returned == 0) {
            return "无数据";
        }

        int from = start + 1;
        int to = start + returned;

        StringBuilder sb = new StringBuilder();
        sb.append("显示第 ").append(from).append("-").append(to).append(" 条");

        if (totalCount != null && totalCount > 0) {
            sb.append("，共 ").append(totalCount).append(" 条");
        } else if (hasMore) {
            sb.append("，还有更多数据");
        }

        return sb.toString();
    }

    /**
     * 构建结果集 Schema 信息（含 Markdown summary）
     */
    private SemanticQueryResponse.SchemaInfo buildSchemaInfo(QueryModel queryModel,
                                                             DbQueryRequestDef request,
                                                             PagingResultImpl queryResult) {
        SemanticQueryResponse.SchemaInfo schemaInfo = new SemanticQueryResponse.SchemaInfo();
        List<SemanticQueryResponse.SchemaInfo.ColumnDef> columnDefs = new ArrayList<>();

        List<String> columns = request.getColumns();
        if (columns != null && queryModel != null) {
            for (String columnName : columns) {
                DbQueryColumn queryColumn = queryModel.findJdbcQueryColumnByName(columnName, false);

                SemanticQueryResponse.SchemaInfo.ColumnDef columnDef =
                        new SemanticQueryResponse.SchemaInfo.ColumnDef();
                columnDef.setName(columnName);

                if (queryColumn != null) {
                    columnDef.setTitle(queryColumn.getCaption());
                    columnDef.setDataType(queryColumn.getType());
                }

                columnDefs.add(columnDef);
            }
        }

        schemaInfo.setColumns(columnDefs);

        // 生成 Markdown summary
        schemaInfo.setSummary(buildSchemaSummary(columnDefs, request, queryResult));

        return schemaInfo;
    }

    /**
     * 生成 Markdown 格式的结果摘要（仅聚合查询时生成）
     */
    private String buildSchemaSummary(List<SemanticQueryResponse.SchemaInfo.ColumnDef> columnDefs,
                                      DbQueryRequestDef request,
                                      PagingResultImpl queryResult) {
        List<GroupRequestDef> groupBy = request.getGroupBy();

        // 无分组时不生成 summary
        if (groupBy == null || groupBy.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        // 提取分组维度和度量指标
        List<String> dimensions = new ArrayList<>();
        List<String> measures = new ArrayList<>();

        Set<String> groupByNames = new HashSet<>();
        for (GroupRequestDef g : groupBy) {
            if (g.getAgg() == null || g.getAgg().isEmpty()) {
                // 无聚合类型 = 分组维度
                groupByNames.add(g.getField());
            }
        }

        for (SemanticQueryResponse.SchemaInfo.ColumnDef col : columnDefs) {
            if (groupByNames.contains(col.getName())) {
                dimensions.add(col.getTitle() != null ? col.getTitle() : col.getName());
            } else {
                // 找到对应的聚合类型
                String aggType = findAggregationType(col.getName(), groupBy);
                if (aggType != null) {
                    measures.add(col.getTitle() != null
                            ? col.getTitle() + "(" + translateAggType(aggType) + ")"
                            : col.getName());
                } else {
                    measures.add(col.getTitle() != null ? col.getTitle() : col.getName());
                }
            }
        }

        // 构建摘要
        if (!dimensions.isEmpty() && !measures.isEmpty()) {
            sb.append("按 ").append(String.join("、", dimensions)).append(" 分组");
            sb.append("，统计 ").append(String.join("、", measures));
        } else if (!dimensions.isEmpty()) {
            sb.append("按 ").append(String.join("、", dimensions)).append(" 分组");
        }

        // 添加数据量信息
        if (queryResult != null && queryResult.getItems() != null) {
            int rowCount = queryResult.getItems().size();
            sb.append("，返回 ").append(rowCount).append(" 条数据");
            if (queryResult.getTotal() > rowCount) {
                sb.append("（共 ").append(queryResult.getTotal()).append(" 条）");
            }
        }

        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 查找列的聚合类型
     */
    private String findAggregationType(String columnName, List<GroupRequestDef> groupBy) {
        if (groupBy == null) return null;
        for (GroupRequestDef g : groupBy) {
            if (columnName.equals(g.getField()) && g.getAgg() != null && !g.getAgg().isEmpty()) {
                return g.getAgg();
            }
        }
        return null;
    }

    /**
     * 翻译聚合类型为中文
     */
    private String translateAggType(String aggType) {
        if (aggType == null) return "";
        return switch (aggType.toUpperCase()) {
            case "SUM" -> "求和";
            case "AVG" -> "平均";
            case "COUNT" -> "计数";
            case "MAX" -> "最大";
            case "MIN" -> "最小";
            default -> aggType;
        };
    }

    /**
     * 添加调试信息
     */
    private void addDebugInfo(SemanticQueryResponse response, QueryContextV3 context, long startTime) {
        SemanticQueryResponse.DebugInfo debugInfo = new SemanticQueryResponse.DebugInfo();
        debugInfo.setDurationMs(System.currentTimeMillis() - startTime);
        response.setDebug(debugInfo);
    }

    /**
     * 自动对齐 columns 和 groupBy 中的维度字段
     *
     * <p>当 columns 中有维度的 $caption 但 groupBy 中只有 $id（或反之），
     * 自动补全缺失的字段并添加警告信息。</p>
     *
     * @param columns      列列表（会被修改）
     * @param groupByItems 分组项列表（会被修改）
     * @param queryModel   查询模型
     * @param context      查询上下文（用于记录警告）
     */
    private void alignColumnsAndGroupBy(List<String> columns, List<SemanticQueryRequest.GroupByItem> groupByItems,
                                        QueryModel queryModel, QueryContextV3 context) {
        // 收集 columns 中的维度字段（按基础名分组）
        Map<String, Set<String>> columnDimensions = new HashMap<>();
        for (String col : columns) {
            if (col.contains("$")) {
                String baseName = col.substring(0, col.lastIndexOf('$'));
                String suffix = col.substring(col.lastIndexOf('$') + 1);
                columnDimensions.computeIfAbsent(baseName, k -> new HashSet<>()).add(suffix);
            }
        }

        // 收集 groupBy 中的非度量维度字段（按基础名分组）
        Map<String, Set<String>> groupByDimensions = new HashMap<>();
        for (SemanticQueryRequest.GroupByItem item : groupByItems) {
            // 跳过度量字段（有聚合类型的）
            if (item.getAgg() != null && !item.getAgg().isEmpty()) {
                continue;
            }
            String field = item.getField();
            if (field.contains("$")) {
                String baseName = field.substring(0, field.lastIndexOf('$'));
                String suffix = field.substring(field.lastIndexOf('$') + 1);
                groupByDimensions.computeIfAbsent(baseName, k -> new HashSet<>()).add(suffix);
            }
        }

        // 找出需要对齐的维度字段
        Set<String> allDimensionBases = new HashSet<>();
        allDimensionBases.addAll(columnDimensions.keySet());
        allDimensionBases.addAll(groupByDimensions.keySet());

        Set<String> columnsSet = new HashSet<>(columns);
        Set<String> groupByFieldSet = groupByItems.stream()
                .map(SemanticQueryRequest.GroupByItem::getField)
                .collect(Collectors.toSet());

        for (String baseName : allDimensionBases) {
            Set<String> colSuffixes = columnDimensions.getOrDefault(baseName, Collections.emptySet());
            Set<String> grpSuffixes = groupByDimensions.getOrDefault(baseName, Collections.emptySet());

            // columns 有 caption，检查是否需要补充 id 到 columns
            if (colSuffixes.contains("caption") && grpSuffixes.contains("id") && !colSuffixes.contains("id")) {
                String fieldToAdd = baseName + "$id";
                if (!columnsSet.contains(fieldToAdd) && queryModel.findJdbcQueryColumnByName(fieldToAdd, false) != null) {
                    columns.add(fieldToAdd);
                    columnsSet.add(fieldToAdd);
                    context.warnings.add("columns 自动补充字段 " + fieldToAdd + "（与 groupBy 对齐）");
                }
            }

            // columns 有 id，检查是否需要补充 caption 到 columns
            if (colSuffixes.contains("id") && grpSuffixes.contains("caption") && !colSuffixes.contains("caption")) {
                String fieldToAdd = baseName + "$caption";
                if (!columnsSet.contains(fieldToAdd) && queryModel.findJdbcQueryColumnByName(fieldToAdd, false) != null) {
                    columns.add(fieldToAdd);
                    columnsSet.add(fieldToAdd);
                    context.warnings.add("columns 自动补充字段 " + fieldToAdd + "（与 groupBy 对齐）");
                }
            }

            // groupBy 有 id，检查是否需要补充 caption 到 groupBy
            if (grpSuffixes.contains("id") && colSuffixes.contains("caption") && !grpSuffixes.contains("caption")) {
                String fieldToAdd = baseName + "$caption";
                if (!groupByFieldSet.contains(fieldToAdd) && queryModel.findJdbcQueryColumnByName(fieldToAdd, false) != null) {
                    SemanticQueryRequest.GroupByItem newItem = new SemanticQueryRequest.GroupByItem();
                    newItem.setField(fieldToAdd);
                    groupByItems.add(newItem);
                    groupByFieldSet.add(fieldToAdd);
                    context.warnings.add("groupBy 自动补充字段 " + fieldToAdd + "（与 columns 对齐）");
                }
            }

            // groupBy 有 caption，检查是否需要补充 id 到 groupBy
            if (grpSuffixes.contains("caption") && colSuffixes.contains("id") && !grpSuffixes.contains("id")) {
                String fieldToAdd = baseName + "$id";
                if (!groupByFieldSet.contains(fieldToAdd) && queryModel.findJdbcQueryColumnByName(fieldToAdd, false) != null) {
                    SemanticQueryRequest.GroupByItem newItem = new SemanticQueryRequest.GroupByItem();
                    newItem.setField(fieldToAdd);
                    groupByItems.add(newItem);
                    groupByFieldSet.add(fieldToAdd);
                    context.warnings.add("groupBy 自动补充字段 " + fieldToAdd + "（与 columns 对齐）");
                }
            }
        }
    }

    /**
     * 校验 groupBy 中的非度量字段必须在 columns 中
     *
     * @param groupByItems 分组项列表
     * @param columns      列列表
     */
    private void validateGroupByFieldsInColumns(List<SemanticQueryRequest.GroupByItem> groupByItems, List<String> columns) {
        Set<String> columnsSet = new HashSet<>(columns);

        // 收集 columns 中的维度基础名
        Set<String> columnBases = new HashSet<>();
        for (String col : columns) {
            if (col.contains("$")) {
                columnBases.add(col.substring(0, col.lastIndexOf('$')));
            }
        }

        for (SemanticQueryRequest.GroupByItem item : groupByItems) {
            // 跳过度量字段（有聚合类型的）
            if (item.getAgg() != null && !item.getAgg().isEmpty()) {
                continue;
            }

            String field = item.getField();

            // 检查字段是否在 columns 中
            if (!columnsSet.contains(field)) {
                // 检查是否是维度字段的变体（例如 columns 中有 product$caption，groupBy 中有 product$id）
                if (field.contains("$")) {
                    String baseName = field.substring(0, field.lastIndexOf('$'));
                    if (!columnBases.contains(baseName)) {
                        throw RX.throwB("groupBy 字段 " + field + " 必须出现在 columns 中（或 columns 中有其对应的维度字段）");
                    }
                } else {
                    // 非维度字段必须完全匹配
                    throw RX.throwB("groupBy 字段 " + field + " 必须出现在 columns 中");
                }
            }
        }
    }

    /**
     * 规范化 orderBy 字段名
     *
     * <p>对于没有后缀的维度字段，自动补充 $id 后缀</p>
     *
     * @param field      原始字段名
     * @param queryModel 查询模型
     * @param context    查询上下文（用于记录警告）
     * @return 规范化后的字段名
     */
    private String normalizeOrderByField(String field, QueryModel queryModel, QueryContextV3 context) {
        // 如果字段已有后缀，直接返回
        if (field.contains("$")) {
            return field;
        }

        // 检查是否是度量字段（直接存在于模型中）
        DbQueryColumn directColumn = queryModel.findJdbcQueryColumnByName(field, false);
        if (directColumn != null) {
            // 是度量字段，直接返回
            return field;
        }

        // 尝试补充 $id 后缀
        String fieldWithId = field + "$id";
        DbQueryColumn columnWithId = queryModel.findJdbcQueryColumnByName(fieldWithId, false);
        if (columnWithId != null) {
            context.warnings.add("orderBy 字段 " + field + " 自动补充为 " + fieldWithId);
            return fieldWithId;
        }

        // 尝试补充 $caption 后缀
        String fieldWithCaption = field + "$caption";
        DbQueryColumn columnWithCaption = queryModel.findJdbcQueryColumnByName(fieldWithCaption, false);
        if (columnWithCaption != null) {
            context.warnings.add("orderBy 字段 " + field + " 自动补充为 " + fieldWithCaption);
            return fieldWithCaption;
        }

        // 都找不到，返回原字段名，让底层报错
        return field;
    }

    /**
     * V3查询上下文
     */
    private static class QueryContextV3 {
        String model;
        SemanticQueryRequest originalRequest;
        Map<String, Object> extData = new HashMap<>();
        List<String> warnings = new ArrayList<>();
    }
}
