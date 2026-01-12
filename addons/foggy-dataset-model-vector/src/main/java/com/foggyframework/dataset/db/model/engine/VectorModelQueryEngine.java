package com.foggyframework.dataset.db.model.engine;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.query.request.*;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.impl.vector.EmbeddingService;
import com.foggyframework.dataset.db.model.impl.vector.VectorDbConfig;
import com.foggyframework.dataset.db.model.impl.vector.VectorQueryModel;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.QueryEngine;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 向量模型查询引擎
 * <p>
 * 处理向量相似度搜索请求，支持 Milvus 向量数据库。
 * 支持 slice 中的 similar 操作符进行向量相似度检索。
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
     * 普通过滤条件（非向量搜索）
     */
    private List<FilterCondition> filterConditions = new ArrayList<>();

    /**
     * 选择的输出字段
     */
    private List<String> outputFields = new ArrayList<>();

    /**
     * Milvus 客户端（延迟初始化）
     */
    private MilvusClientV2 milvusClient;

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
        if (vectorSearchParams == null) {
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
        if (vectorSearchParams != null) {
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
            log.debug("Vector search params: field={}, topK={}, minScore={}, text={}",
                    vectorSearchParams.getField(),
                    vectorSearchParams.getTopK(),
                    vectorSearchParams.getMinScore(),
                    vectorSearchParams.getText() != null ?
                            vectorSearchParams.getText().substring(0, Math.min(50, vectorSearchParams.getText().length())) + "..." : null);
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
        if (vectorSearchParams == null || vectorSearchParams.getVector() == null) {
            log.warn("No vector search params, returning empty result");
            return new ArrayList<>();
        }

        try {
            // 初始化 Milvus 客户端
            initMilvusClient();

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

            SearchReq searchReq = searchBuilder.build();

            // 执行搜索
            SearchResp searchResp = milvusClient.search(searchReq);

            // 处理结果
            List<Map<String, Object>> results = new ArrayList<>();
            List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();

            if (searchResults != null && !searchResults.isEmpty()) {
                List<SearchResp.SearchResult> firstQueryResults = searchResults.get(0);
                totalCount = firstQueryResults.size();

                // 应用分页
                int start = Math.min(offset, firstQueryResults.size());
                int end = Math.min(offset + limit, firstQueryResults.size());

                for (int i = start; i < end; i++) {
                    SearchResp.SearchResult result = firstQueryResults.get(i);
                    float score = result.getScore();

                    // 应用 minScore 过滤
                    if (vectorSearchParams.getMinScore() != null && score < vectorSearchParams.getMinScore()) {
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

        } catch (Exception e) {
            log.error("Failed to execute vector search", e);
            throw new RuntimeException("Vector search failed: " + e.getMessage(), e);
        }
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
     * 初始化 Milvus 客户端
     */
    private void initMilvusClient() {
        if (milvusClient != null) {
            return;
        }

        VectorDbConfig config = queryModel.getVectorDbConfig();
        String uri = String.format("http://%s:%d", config.getHost(), config.getPort());

        ConnectConfig.ConnectConfigBuilder connectBuilder = ConnectConfig.builder()
                .uri(uri)
                .connectTimeoutMs(config.getConnectTimeoutMs());

        if (StringUtils.isNotEmpty(config.getDatabase())) {
            connectBuilder.dbName(config.getDatabase());
        }
        if (StringUtils.isNotEmpty(config.getUsername())) {
            // Milvus v2 SDK 使用 token 格式: username:password
            connectBuilder.token(config.getUsername() + ":" + config.getPassword());
        }

        milvusClient = new MilvusClientV2(connectBuilder.build());

        if (log.isDebugEnabled()) {
            log.debug("Milvus client initialized: {}", uri);
        }
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
        if (milvusClient != null) {
            milvusClient.close();
            milvusClient = null;
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
