package com.foggyframework.dataset.db.model.service.impl;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.DataSetResultFilterManager;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.db.model.spi.NamespaceContext;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 查询门面实现
 * <p>
 * 统一封装查询生命周期：beforeQuery -> [skipQuery check] -> query (with L2 cache) -> process
 * </p>
 * <p>
 * 双层缓存支持：
 * <ul>
 *   <li><b>L1 缓存</b>：由 {@link com.foggyframework.dataset.db.model.plugins.result_set_filter.L1CacheStep} 处理</li>
 *   <li><b>L2 缓存</b>：在 JdbcQueryModelImpl 内部处理</li>
 * </ul>
 * 引入 foggy-dataset-model-cache 模块后自动启用。
 * </p>
 *
 * @author foggy-framework
 * @since 8.0.0
 */
@Slf4j
@Service
public class QueryFacadeImpl implements QueryFacade {

    @Resource
    private QueryModelLoader queryModelLoader;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    @Resource
    private DataSetResultFilterManager dataSetResultFilterManager;

    @Override
    public PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form) {
        return queryModelData(form, null, ModelResultContext.QueryType.NORMAL);
    }

    @Override
    public PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form, String namespace) {
        return queryModelData(form, namespace, ModelResultContext.QueryType.NORMAL);
    }

    @Override
    public PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form,
                                           ModelResultContext.QueryType queryType) {
        return queryModelData(form, null, queryType);
    }

    /**
     * 执行查询（内部统一方法）
     *
     * @param form      查询请求
     * @param namespace 命名空间
     * @param queryType 查询类型
     * @return 查询结果
     */
    private PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form,
                                            String namespace,
                                            ModelResultContext.QueryType queryType) {
        // 创建上下文
        ModelResultContext context = new ModelResultContext(form, null);
        context.setQueryType(queryType);
        context.setNamespace(namespace);

        // 执行完整查询流程
        DbQueryResult result = doQuery(context);

        return result.getPagingResult();
    }

    @Override
    public DbQueryResult queryModelResult(PagingRequest<DbQueryRequestDef> form) {
        // 创建上下文
        ModelResultContext context = new ModelResultContext(form, null);
        context.setQueryType(ModelResultContext.QueryType.NORMAL);

        return doQuery(context);
    }

    @Override
    public DbQueryResult queryModelResult(ModelResultContext context) {
        return doQuery(context);
    }

    /**
     * 执行查询的核心流程
     * <p>
     * 生命周期：beforeQuery -> [skipQuery check] -> query (with L2 cache) -> process
     * </p>
     */
    private DbQueryResult doQuery(ModelResultContext context) {
        try {
            // 0. 设置namespace到ThreadLocal（供模型加载使用）
            if (context.getNamespace() != null) {
                NamespaceContext.setNamespace(context.getNamespace());
            }

            PagingRequest<DbQueryRequestDef> form = context.getRequest();
            DbQueryRequestDef queryRequest = form.getParam();

            // 1. 获取查询模型
            String queryModelName = queryRequest.getQueryModel();
            QueryModel jdbcQueryModel = queryModelLoader.getJdbcQueryModel(queryModelName);

            // 1.1 提前设置 jdbcQueryModel，供 beforeQuery Step 使用
            context.setQueryModel(jdbcQueryModel);

            // 2. beforeQuery: 执行预处理 Step（L1Cache、Authorization、AutoGroupBy、InlineExpression 等）
            dataSetResultFilterManager.beforeQuery(context);

            if (log.isDebugEnabled()) {
                log.debug("QueryFacade.beforeQuery completed, queryType={}, model={}, skipQuery={}, namespace={}",
                        context.getQueryType(), queryModelName, context.isSkipQuery(), context.getNamespace());
            }

            // 3. 检查是否跳过查询（如 L1 缓存命中）
            if (context.isSkipQuery() && context.getQueryResult() != null) {
                // 执行 process Step（缓存结果也需要经过结果处理）
                dataSetResultFilterManager.process(context);
                return context.getQueryResult();
            }

            // 4. 执行查询（内部包含 L2 缓存逻辑）
            DbQueryResult dbQueryResult = jdbcQueryModel.query(systemBundlesContext, context);

            // 5. 设置查询结果到上下文
            context.setPagingResult(dbQueryResult.getPagingResult());
            context.setQueryResult(dbQueryResult);
            if (dbQueryResult.getQueryEngine() != null) {
                context.setQuery(dbQueryResult.getQueryEngine().getJdbcQuery());
                context.setQueryModel(dbQueryResult.getQueryEngine().getJdbcQueryModel());
            }

            // 6. process: 执行结果处理 Step（包含 L1 缓存写入）
            dataSetResultFilterManager.process(context);

            // 7. 更新结果（process 可能修改了 pagingResult）
            PagingResultImpl processedResult = context.getPagingResult();

            return DbQueryResult.of(processedResult, dbQueryResult.getQueryEngine());
        } finally {
            // 8. 清理namespace ThreadLocal
            NamespaceContext.clear();
        }
    }
}
