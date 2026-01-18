package com.foggyframework.dataset.db.model.service.impl;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.DataSetResultFilterManager;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.db.model.spi.NoOpQueryCacheProvider;
import com.foggyframework.dataset.db.model.spi.QueryCacheProvider;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 查询门面实现
 * <p>
 * 统一封装查询生命周期：beforeQuery -> [L1 cache] -> query (with L2 cache) -> process
 * </p>
 * <p>
 * 双层缓存支持：
 * <ul>
 *   <li><b>L1 缓存</b>：Token 级别，在 beforeQuery 后检查，可跳过 SQL 构建</li>
 *   <li><b>L2 缓存</b>：SQL 级别，在 JdbcQueryModelImpl 内部处理</li>
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

    /**
     * 查询缓存提供者（可选）
     * <p>
     * 默认使用 {@link NoOpQueryCacheProvider}，不执行任何缓存操作。
     * 引入 foggy-dataset-model-cache 模块后会自动注入具体实现。
     * </p>
     */
    private QueryCacheProvider queryCacheProvider = NoOpQueryCacheProvider.INSTANCE;

    @Autowired(required = false)
    public void setQueryCacheProvider(QueryCacheProvider queryCacheProvider) {
        if (queryCacheProvider != null) {
            this.queryCacheProvider = queryCacheProvider;
            log.info("QueryCacheProvider initialized: {}", queryCacheProvider.getClass().getSimpleName());
        }
    }

    /**
     * 获取当前的缓存提供者
     * <p>
     * 供 QueryModel 实现类获取，用于 L2 缓存。
     * </p>
     */
    public QueryCacheProvider getQueryCacheProvider() {
        return queryCacheProvider;
    }

    @Override
    public PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form) {
        return queryModelData(form, ModelResultContext.QueryType.NORMAL);
    }

    @Override
    public PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form,
                                           ModelResultContext.QueryType queryType) {
        // 创建上下文
        ModelResultContext context = new ModelResultContext(form, null);
        context.setQueryType(queryType);

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
     * 生命周期：beforeQuery -> [L1 cache check] -> query (with L2 cache) -> [L1 cache write] -> process
     * </p>
     */
    private DbQueryResult doQuery(ModelResultContext context) {
        PagingRequest<DbQueryRequestDef> form = context.getRequest();
        DbQueryRequestDef queryRequest = form.getParam();

        // 1. 获取查询模型
        String queryModelName = queryRequest.getQueryModel();
        QueryModel jdbcQueryModel = queryModelLoader.getJdbcQueryModel(queryModelName);

        // 1.1 提前设置 jdbcQueryModel，供 beforeQuery Step 使用
        context.setJdbcQueryModel(jdbcQueryModel);

        // 1.2 初始化缓存配置并注入缓存提供者
        if (context.getCacheConfig() == null) {
            context.setCacheConfig(ModelResultContext.QueryCacheConfig.defaultConfig());
        }
        context.getCacheConfig().setProvider(queryCacheProvider);

        // 2. beforeQuery: 执行预处理 Step（AutoGroupBy、InlineExpression、Authorization 等）
        dataSetResultFilterManager.beforeQuery(context);

        if (log.isDebugEnabled()) {
            log.debug("QueryFacade.beforeQuery completed, queryType={}, model={}",
                    context.getQueryType(), queryModelName);
        }

        // 3. 【L1 缓存检查】Token 级别（需主动开启）
        String authorization = context.getAuthorization();
        boolean l1Enabled = QueryCacheProvider.isL1Enabled(context);

        if (l1Enabled && authorization != null) {
            PagingResultImpl cached = queryCacheProvider.checkL1Cache(context, authorization);
            if (cached != null) {
                if (log.isDebugEnabled()) {
                    log.debug("L1 cache HIT for model={}", queryModelName);
                }
                QueryCacheProvider.markL1Hit(context);
                context.setPagingResult(cached);

                // 执行 process Step（缓存结果也需要经过结果处理）
                dataSetResultFilterManager.process(context);
                return DbQueryResult.of(context.getPagingResult(), null);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("L1 cache MISS for model={}", queryModelName);
                }
            }
        }

        // 4. 执行查询（内部包含 L2 缓存逻辑）
        DbQueryResult dbQueryResult = jdbcQueryModel.query(systemBundlesContext, context);

        // 5. 设置查询结果到上下文
        context.setPagingResult(dbQueryResult.getPagingResult());
        if (dbQueryResult.getQueryEngine() != null) {
            context.setJdbcQuery(dbQueryResult.getQueryEngine().getJdbcQuery());
            context.setJdbcQueryModel(dbQueryResult.getQueryEngine().getJdbcQueryModel());
        }

        // 6. 【L1 缓存写入】（仅当 L1 启用且未命中时）
        if (l1Enabled && authorization != null) {
            boolean l1Hit = context.getCacheConfig() != null && context.getCacheConfig().isL1CacheHit();
            if (!l1Hit && dbQueryResult.getPagingResult() != null) {
                queryCacheProvider.writeL1Cache(context, authorization, dbQueryResult.getPagingResult());
                if (log.isDebugEnabled()) {
                    log.debug("L1 cache WRITE for model={}", queryModelName);
                }
            }
        }

        // 7. process: 执行结果处理 Step
        dataSetResultFilterManager.process(context);

        // 8. 更新结果（process 可能修改了 pagingResult）
        PagingResultImpl processedResult = context.getPagingResult();

        return DbQueryResult.of(processedResult, dbQueryResult.getQueryEngine());
    }
}
