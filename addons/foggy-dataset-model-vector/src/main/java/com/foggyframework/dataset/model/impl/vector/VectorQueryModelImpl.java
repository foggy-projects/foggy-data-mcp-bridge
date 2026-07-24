package com.foggyframework.dataset.model.impl.vector;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.engine.VectorModelQueryEngine;
import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.spi.CalculatedFieldProcessor;
import com.foggyframework.dataset.model.spi.TableModel;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * 向量查询模型实现
 * <p>
 * 专注于向量相似度检索，不支持聚合等复杂特性。
 */
@Getter
@Setter
@Slf4j
public class VectorQueryModelImpl extends QueryModelSupport implements VectorQueryModel {

    /**
     * 向量数据库配置
     */
    private VectorDbConfig vectorDbConfig;

    /**
     * 向量字段名称
     */
    private String vectorFieldName;

    /**
     * 集合名称
     */
    private String collectionName;

    /**
     * 向量字段元数据（从 Milvus 自动发现）
     */
    private VectorFieldMetadata vectorFieldMetadata;

    /**
     * Embedding 服务
     */
    private EmbeddingService embeddingService;

    public VectorQueryModelImpl(List<TableModel> tableModelList, Fsscript fsscript, VectorDbConfig vectorDbConfig) {
        super(tableModelList, fsscript);
        this.vectorDbConfig = vectorDbConfig;
    }

    @Override
    public CalculatedFieldProcessor getCalculatedFieldProcessor() {
        // 向量模型暂不支持计算字段
        return null;
    }

    @Override
    public DbQueryResult query(SystemBundlesContext systemBundlesContext, PagingRequest<DbQueryRequestDef> form) {
        ModelResultContext context = new ModelResultContext(form, null);
        return query(systemBundlesContext, context);
    }

    @Override
    public DbQueryResult query(SystemBundlesContext systemBundlesContext, ModelResultContext context) {
        return queryVector(systemBundlesContext, context.getRequest());
    }

    /**
     * 执行向量查询
     */
    public DbQueryResult queryVector(SystemBundlesContext systemBundlesContext, PagingRequest<DbQueryRequestDef> form) {
        DbQueryRequestDef queryRequest = form.getParam();

        // 初始化 embedding 服务
        if (embeddingService == null && vectorDbConfig.getEmbedding() != null) {
            embeddingService = new EmbeddingService(vectorDbConfig.getEmbedding());
        }

        VectorModelQueryEngine queryEngine = new VectorModelQueryEngine(this, embeddingService);

        // 分析查询请求
        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        // 执行向量搜索
        List<Map<String, Object>> results = queryEngine.executeSearch(form.getStart(), form.getLimit());

        // 构建返回结果
        int total = 0;
        Map<String, Object> totalData = null;

        if (queryRequest.isReturnTotal()) {
            total = queryEngine.getTotalCount();
            totalData = Map.of("total", total);
        }

        return DbQueryResult.of(
                PagingResultImpl.of(results, form.getStart(), form.getLimit(), totalData, total),
                queryEngine
        );
    }
}
