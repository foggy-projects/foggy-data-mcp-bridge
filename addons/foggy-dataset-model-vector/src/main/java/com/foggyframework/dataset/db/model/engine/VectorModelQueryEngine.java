package com.foggyframework.dataset.db.model.engine;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.query.request.*;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.impl.vector.EmbeddingService;
import com.foggyframework.dataset.db.model.impl.vector.MilvusClientPool;
import com.foggyframework.dataset.db.model.impl.vector.VectorDbConfig;
import com.foggyframework.dataset.db.model.impl.vector.VectorQueryModel;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.QueryEngine;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 向量模型查询引擎 v2.0
 * <p>
 * 处理向量相似度搜索请求，支持 Milvus 向量数据库。
 * <p>
 * 支持的操作符：
 * <ul>
 *   <li>similar - 向量相似度搜索（支持 groupBy、radius 等高级参数）</li>
 *   <li>hybrid - 混合搜索（向量 + 关键词加权融合）</li>
 * </ul>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@Data
public class VectorModelQueryEngine implements QueryEngine {

    private final VectorQueryModel queryModel;
    private final EmbeddingService embeddingService;

    private JdbcQuery jdbcQuery;

    /**
     * 向量搜索参数
     */
    private VectorSearchParams vectorSearchParams;

    /**
     * 混合搜索参数
     */
    private HybridSearchParams hybridSearchParams;

    /**
     * 普通过滤条件（非向量搜索）
     */
    private List<FilterCondition> filterConditions = new ArrayList<>();

    /**
     * 选择的输出字段
     */
    private List<String> outputFields = new ArrayList<>();

    /**
     * Milvus 客户端连接池
     */
    private static MilvusClientPool clientPool;
    private static final Object poolLock = new Object();

    /**
     * 总记录数
     */
    private int totalCount = 0;

    public VectorModelQueryEngine(VectorQueryModel queryModel, EmbeddingService embeddingService) {
        this.queryModel = queryModel;
        this.embeddingService = embeddingService;
    }

    /**
     * 分析查询请求
     */
    public void analysisQueryRequest(SystemBundlesContext systemBundlesContext, DbQueryRequestDef queryRequest) {
        RX.notNull(queryRequest, "查询请求不得为空");

        this.jdbcQuery = new JdbcQuery();
        jdbcQuery.setQueryRequest(queryRequest);
        jdbcQuery.from(queryModel.getQueryObject());

        // 1. 处理选择的列
        List<DbColumn> selectColumns;
        if (queryRequest.getColumns() == null || queryRequest.getColumns().isEmpty()) {
            selectColumns = queryModel.getSelectColumns(true);
        } else {
            selectColumns = new ArrayList<>(queryRequest.getColumns().size());
            for (String columnName : queryRequest.getColumns()) {
                selectColumns.add(queryModel.findJdbcColumnForSelectByName(columnName, true).getSelectColumn());
            }
        }
        jdbcQuery.select(selectColumns);

        // 转换为输出字段名
        for (DbColumn column : selectColumns) {
            outputFields.add(column.getSqlColumnName());
        }

        // 添加 _score 字段（相似度得分）
        if (!outputFields.contains("_score")) {
            outputFields.add("_score");
        }

        // 2. 处理切片条件，区分向量搜索和普通过滤
        if (queryRequest.getSlice() != null) {
            for (SliceRequestDef sliceDef : queryRequest.getSlice()) {
                processSlice(sliceDef);
            }
        }

        // 验证：必须有向量搜索条件
        if (vectorSearchParams == null && hybridSearchParams == null) {
            log.warn("No vector search condition found, will return empty result");
        }
    }

    /**
     * 处理切片条件
     */
    private void processSlice(SliceRequestDef sliceDef) {
        String op = sliceDef.getOp();

        if ("similar".equalsIgnoreCase(op)) {
            // 向量相似度搜索
            processVectorSearchSlice(sliceDef);
        } else if ("hybrid".equalsIgnoreCase(op)) {
            // 混合搜索
            processHybridSearchSlice(sliceDef);
        } else {
            // 普通过滤条件
            processFilterSlice(sliceDef);
        }
    }

    /**
     * 处理向量搜索条件
     */
    @SuppressWarnings("unchecked")
    private void processVectorSearchSlice(SliceRequestDef sliceDef) {
        if (vectorSearchParams != null || hybridSearchParams != null) {
            throw RX.throwB("Only one vector search condition is allowed");
        }

        String field = sliceDef.getField();
        Object value = sliceDef.getValue();

        vectorSearchParams = new VectorSearchParams();
        vectorSearchParams.setField(field);

        if (value instanceof Map) {
            Map<String, Object> valueMap = (Map<String, Object>) value;

            // 处理文本或直接向量
            if (valueMap.containsKey("text")) {
                String text = (String) valueMap.get("text");
                vectorSearchParams.setText(text);
                // 将文本转换为向量
                if (embeddingService != null) {
                    List<Float> vector = embeddingService.embed(text);
                    vectorSearchParams.setVector(vector);
                } else {
                    throw RX.throwB("EmbeddingService is required for text-based vector search");
                }
            } else if (valueMap.containsKey("vector")) {
                Object vectorObj = valueMap.get("vector");
                if (vectorObj instanceof List) {
                    vectorSearchParams.setVector(convertToFloatList((List<?>) vectorObj));
                }
            }

            // topK 参数
            if (valueMap.containsKey("topK")) {
                vectorSearchParams.setTopK(((Number) valueMap.get("topK")).intValue());
            }

            // minScore 参数
            if (valueMap.containsKey("minScore")) {
                vectorSearchParams.setMinScore(((Number) valueMap.get("minScore")).floatValue());
            }

            // groupBy 参数
            if (valueMap.containsKey("groupBy")) {
                vectorSearchParams.setGroupBy((String) valueMap.get("groupBy"));
            }

            // groupSize 参数
            if (valueMap.containsKey("groupSize")) {
                vectorSearchParams.setGroupSize(((Number) valueMap.get("groupSize")).intValue());
            }

            // radius 参数（范围搜索）
            if (valueMap.containsKey("radius")) {
                vectorSearchParams.setRadius(((Number) valueMap.get("radius")).floatValue());
            }

            // rangeFilter 参数
            if (valueMap.containsKey("rangeFilter")) {
                vectorSearchParams.setRangeFilter(((Number) valueMap.get("rangeFilter")).floatValue());
            }

        } else if (value instanceof String) {
            // 简化形式：直接传文本
            String text = (String) value;
            vectorSearchParams.setText(text);
            if (embeddingService != null) {
                List<Float> vector = embeddingService.embed(text);
                vectorSearchParams.setVector(vector);
            } else {
                throw RX.throwB("EmbeddingService is required for text-based vector search");
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Vector search params: field={}, topK={}, minScore={}, groupBy={}, radius={}, text={}",
                    vectorSearchParams.getField(),
                    vectorSearchParams.getTopK(),
                    vectorSearchParams.getMinScore(),
                    vectorSearchParams.getGroupBy(),
                    vectorSearchParams.getRadius(),
                    vectorSearchParams.getText() != null ?
                            vectorSearchParams.getText().substring(0, Math.min(50, vectorSearchParams.getText().length())) + "..." : null);
        }
    }

    /**
     * 处理混合搜索条件
     */
    @SuppressWarnings("unchecked")
    private void processHybridSearchSlice(SliceRequestDef sliceDef) {
        if (vectorSearchParams != null || hybridSearchParams != null) {
            throw RX.throwB("Only one vector search condition is allowed");
        }

        String field = sliceDef.getField();
        Object value = sliceDef.getValue();

        if (!(value instanceof Map)) {
            throw RX.throwB("Hybrid search requires a map value with 'text', 'keyword', and optional weights");
        }

        Map<String, Object> valueMap = (Map<String, Object>) value;

        hybridSearchParams = new HybridSearchParams();
        hybridSearchParams.setField(field);

        // text 参数（用于向量搜索）
        if (valueMap.containsKey("text")) {
            String text = (String) valueMap.get("text");
            hybridSearchParams.setText(text);
            if (embeddingService != null) {
                List<Float> vector = embeddingService.embed(text);
                hybridSearchParams.setVector(vector);
            } else {
                throw RX.throwB("EmbeddingService is required for hybrid search");
            }
        } else {
            throw RX.throwB("Hybrid search requires 'text' parameter");
        }

        // keyword 参数（用于关键词过滤）
        if (valueMap.containsKey("keyword")) {
            hybridSearchParams.setKeyword((String) valueMap.get("keyword"));
        }

        // topK 参数
        if (valueMap.containsKey("topK")) {
            hybridSearchParams.setTopK(((Number) valueMap.get("topK")).intValue());
        }

        // vectorWeight 参数
        if (valueMap.containsKey("vectorWeight")) {
            hybridSearchParams.setVectorWeight(((Number) valueMap.get("vectorWeight")).floatValue());
        }

        // keywordWeight 参数
        if (valueMap.containsKey("keywordWeight")) {
            hybridSearchParams.setKeywordWeight(((Number) valueMap.get("keywordWeight")).floatValue());
        }

        if (log.isDebugEnabled()) {
            log.debug("Hybrid search params: field={}, topK={}, vectorWeight={}, keywordWeight={}, text={}, keyword={}",
                    hybridSearchParams.getField(),
                    hybridSearchParams.getTopK(),
                    hybridSearchParams.getVectorWeight(),
                    hybridSearchParams.getKeywordWeight(),
                    hybridSearchParams.getText() != null ?
                            hybridSearchParams.getText().substring(0, Math.min(50, hybridSearchParams.getText().length())) + "..." : null,
                    hybridSearchParams.getKeyword());
        }
    }

    /**
     * 处理普通过滤条件
     */
    private void processFilterSlice(SliceRequestDef sliceDef) {
        FilterCondition condition = new FilterCondition();
        condition.setField(sliceDef.getField());
        condition.setOp(sliceDef.getOp());
        condition.setValue(sliceDef.getValue());
        filterConditions.add(condition);
    }

    /**
     * 执行向量搜索
     */
    public List<Map<String, Object>> executeSearch(int offset, int limit) {
        // 根据搜索类型选择执行方法
        if (hybridSearchParams != null) {
            return executeHybridSearch(offset, limit);
        } else if (vectorSearchParams != null && vectorSearchParams.getVector() != null) {
            return executeSimilarSearch(offset, limit);
        } else {
            log.warn("No vector search params, returning empty result");
            return new ArrayList<>();
        }
    }

    /**
     * 执行相似度搜索
     */
    private List<Map<String, Object>> executeSimilarSearch(int offset, int limit) {
        try {
            return getClientPool().execute(client -> {
                // 构建搜索请求
                int topK = Math.min(vectorSearchParams.getTopK(), offset + limit);

                SearchReq.SearchReqBuilder searchBuilder = SearchReq.builder()
                        .collectionName(queryModel.getCollectionName())
                        .data(Collections.singletonList(new FloatVec(vectorSearchParams.getVector())))
                        .topK(topK)
                        .outputFields(outputFields);

                // 添加过滤条件
                String filterExpr = buildFilterExpression();
                if (StringUtils.isNotEmpty(filterExpr)) {
                    searchBuilder.filter(filterExpr);
                }

                // 添加 groupBy 参数
                if (StringUtils.isNotEmpty(vectorSearchParams.getGroupBy())) {
                    searchBuilder.groupByFieldName(vectorSearchParams.getGroupBy());
                    // Note: groupSize is not available in Milvus SDK 2.4.4
                }

                // 添加范围搜索参数
                if (vectorSearchParams.getRadius() != null) {
                    Map<String, Object> searchParams = new HashMap<>();
                    searchParams.put("radius", vectorSearchParams.getRadius());
                    if (vectorSearchParams.getRangeFilter() != null) {
                        searchParams.put("range_filter", vectorSearchParams.getRangeFilter());
                    }
                    searchBuilder.searchParams(searchParams);
                }

                SearchReq searchReq = searchBuilder.build();

                // 执行搜索
                SearchResp searchResp = client.search(searchReq);

                // 处理结果
                return processSearchResults(searchResp, offset, limit);
            });

        } catch (Exception e) {
            log.error("Failed to execute vector search", e);
            throw new RuntimeException("Vector search failed: " + e.getMessage(), e);
        }
    }

    /**
     * 执行混合搜索
     * <p>
     * 注意：Milvus SDK 2.4.4 的 HybridSearch API 有限制，
     * 当前实现采用简化方案：先执行向量搜索，再在应用层进行关键词过滤。
     */
    private List<Map<String, Object>> executeHybridSearch(int offset, int limit) {
        try {
            return getClientPool().execute(client -> {
                int topK = Math.min(hybridSearchParams.getTopK(), offset + limit);

                // 构建过滤条件（包含关键词过滤）
                String filterExpr = buildFilterExpression();
                String keywordFilter = buildKeywordFilter(hybridSearchParams.getKeyword());

                String combinedFilter;
                if (StringUtils.isNotEmpty(filterExpr) && StringUtils.isNotEmpty(keywordFilter)) {
                    combinedFilter = filterExpr + " and " + keywordFilter;
                } else if (StringUtils.isNotEmpty(keywordFilter)) {
                    combinedFilter = keywordFilter;
                } else {
                    combinedFilter = filterExpr;
                }

                // 使用普通搜索 + 过滤实现混合搜索
                SearchReq.SearchReqBuilder searchBuilder = SearchReq.builder()
                        .collectionName(queryModel.getCollectionName())
                        .data(Collections.singletonList(new FloatVec(hybridSearchParams.getVector())))
                        .topK(topK)
                        .outputFields(outputFields);

                if (StringUtils.isNotEmpty(combinedFilter)) {
                    searchBuilder.filter(combinedFilter);
                }

                // 执行搜索
                SearchResp searchResp = client.search(searchBuilder.build());

                // 处理结果
                return processSearchResults(searchResp, offset, limit);
            });

        } catch (Exception e) {
            log.error("Failed to execute hybrid search", e);
            throw new RuntimeException("Hybrid search failed: " + e.getMessage(), e);
        }
    }

    /**
     * 处理搜索结果
     */
    private List<Map<String, Object>> processSearchResults(SearchResp searchResp, int offset, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();

        if (searchResults != null && !searchResults.isEmpty()) {
            List<SearchResp.SearchResult> firstQueryResults = searchResults.get(0);
            totalCount = firstQueryResults.size();

            // 应用分页
            int start = Math.min(offset, firstQueryResults.size());
            int end = Math.min(offset + limit, firstQueryResults.size());

            Float minScore = vectorSearchParams != null ? vectorSearchParams.getMinScore() : null;

            for (int i = start; i < end; i++) {
                SearchResp.SearchResult result = firstQueryResults.get(i);
                float score = result.getScore();

                // 应用 minScore 过滤
                if (minScore != null && score < minScore) {
                    continue;
                }

                Map<String, Object> row = new HashMap<>(result.getEntity());
                row.put("_score", score);
                results.add(row);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Vector search returned {} results", results.size());
        }

        return results;
    }

    /**
     * 构建关键词过滤表达式
     * <p>
     * 在所有文本字段中搜索关键词
     */
    private String buildKeywordFilter(String keyword) {
        if (StringUtils.isEmpty(keyword)) {
            return "";
        }

        // 简单实现：在 content 和 title 字段中搜索（可根据实际情况调整）
        // Milvus 使用 like 操作符进行模糊匹配
        List<String> conditions = new ArrayList<>();

        // 尝试在常见文本字段中搜索
        String[] textFields = {"content", "title", "text", "description"};
        for (String field : textFields) {
            if (outputFields.contains(field)) {
                conditions.add(field + " like \"%" + keyword + "%\"");
            }
        }

        if (conditions.isEmpty()) {
            return "";
        }

        return "(" + String.join(" or ", conditions) + ")";
    }

    /**
     * 构建 Milvus 过滤表达式
     */
    private String buildFilterExpression() {
        if (filterConditions.isEmpty()) {
            return "";
        }

        List<String> expressions = new ArrayList<>();
        for (FilterCondition condition : filterConditions) {
            String expr = buildSingleFilterExpression(condition);
            if (expr != null) {
                expressions.add(expr);
            }
        }

        return String.join(" and ", expressions);
    }

    /**
     * 构建单个过滤表达式
     */
    private String buildSingleFilterExpression(FilterCondition condition) {
        String field = condition.getField();
        String op = condition.getOp();
        Object value = condition.getValue();

        switch (op.toLowerCase()) {
            case "=":
            case "==":
                return formatFilterValue(field, "==", value);
            case "!=":
            case "<>":
                return formatFilterValue(field, "!=", value);
            case ">":
                return formatFilterValue(field, ">", value);
            case ">=":
                return formatFilterValue(field, ">=", value);
            case "<":
                return formatFilterValue(field, "<", value);
            case "<=":
                return formatFilterValue(field, "<=", value);
            case "in":
                if (value instanceof List) {
                    return field + " in " + formatListValue((List<?>) value);
                }
                break;
            case "not in":
                if (value instanceof List) {
                    return field + " not in " + formatListValue((List<?>) value);
                }
                break;
            case "like":
                return field + " like \"%" + value + "%\"";
            default:
                log.warn("Unsupported filter operator: {}", op);
        }
        return null;
    }

    private String formatFilterValue(String field, String op, Object value) {
        if (value instanceof String) {
            return field + " " + op + " \"" + value + "\"";
        }
        return field + " " + op + " " + value;
    }

    private String formatListValue(List<?> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            Object v = values.get(i);
            if (v instanceof String) {
                sb.append("\"").append(v).append("\"");
            } else {
                sb.append(v);
            }
            if (i < values.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 获取或创建连接池
     */
    private MilvusClientPool getClientPool() {
        if (clientPool == null) {
            synchronized (poolLock) {
                if (clientPool == null) {
                    VectorDbConfig config = queryModel.getVectorDbConfig();
                    clientPool = new MilvusClientPool(config);
                    log.info("Created Milvus client pool: {}", clientPool.getPoolStatus());
                }
            }
        }
        return clientPool;
    }

    /**
     * 转换为 Float 列表
     */
    private List<Float> convertToFloatList(List<?> list) {
        List<Float> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Number) {
                result.add(((Number) item).floatValue());
            }
        }
        return result;
    }

    /**
     * 获取总记录数
     */
    public int getTotalCount() {
        return totalCount;
    }

    /**
     * 获取查询模型（实现 QueryEngine 接口）
     */
    @Override
    public QueryModel getJdbcQueryModel() {
        return queryModel;
    }

    /**
     * 关闭资源
     */
    public void close() {
        // 连接池是共享的，不在这里关闭
    }

    /**
     * 关闭连接池（应用关闭时调用）
     */
    public static void closePool() {
        synchronized (poolLock) {
            if (clientPool != null) {
                clientPool.close();
                clientPool = null;
            }
        }
    }

    /**
     * 向量搜索参数
     */
    @Data
    public static class VectorSearchParams {
        private String field;
        private String text;
        private List<Float> vector;
        private int topK = 10;
        private Float minScore;
        // v2.0 新增参数
        private String groupBy;
        private int groupSize = 0;
        private Float radius;
        private Float rangeFilter;
    }

    /**
     * 混合搜索参数
     */
    @Data
    public static class HybridSearchParams {
        private String field;
        private String text;
        private String keyword;
        private List<Float> vector;
        private int topK = 10;
        private float vectorWeight = 0.7f;
        private float keywordWeight = 0.3f;
    }

    /**
     * 过滤条件
     */
    @Data
    public static class FilterCondition {
        private String field;
        private String op;
        private Object value;
    }
}
