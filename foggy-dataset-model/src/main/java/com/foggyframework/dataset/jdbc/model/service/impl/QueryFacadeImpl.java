package com.foggyframework.dataset.jdbc.model.service.impl;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.jdbc.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.jdbc.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.DataSetResultFilterManager;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.jdbc.model.service.QueryFacade;
import com.foggyframework.dataset.jdbc.model.spi.QueryModelLoader;
import com.foggyframework.dataset.jdbc.model.spi.QueryModel;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * 查询门面实现
 * <p>
 * 统一封装查询生命周期：beforeQuery -> query -> process
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
     * 生命周期：beforeQuery -> query -> process
     * </p>
     */
    private DbQueryResult doQuery(ModelResultContext context) {
        PagingRequest<DbQueryRequestDef> form = context.getRequest();
        DbQueryRequestDef queryRequest = form.getParam();

        // 1. 获取查询模型
        String queryModelName = queryRequest.getQueryModel();
        QueryModel jdbcQueryModel = queryModelLoader.getJdbcQueryModel(queryModelName);

        // 1.1 提前设置 jdbcQueryModel，供 beforeQuery Step 使用（如 AutoGroupByStep 需要查询列定义）
        context.setJdbcQueryModel(jdbcQueryModel);

        // 2. beforeQuery: 执行预处理 Step（AutoGroupBy、InlineExpression、Authorization 等）
        dataSetResultFilterManager.beforeQuery(context);

        if (log.isDebugEnabled()) {
            log.debug("QueryFacade.beforeQuery completed, queryType={}, model={}",
                    context.getQueryType(), queryModelName);
        }

        // 3. 执行查询（使用带 context 的方法，以便复用预处理结果）
        DbQueryResult dbQueryResult = jdbcQueryModel.query(systemBundlesContext, context);

        // 4. 设置查询结果到上下文
        context.setPagingResult(dbQueryResult.getPagingResult());
        if (dbQueryResult.getQueryEngine() != null) {
            context.setJdbcQuery(dbQueryResult.getQueryEngine().getJdbcQuery());
            context.setJdbcQueryModel(dbQueryResult.getQueryEngine().getJdbcQueryModel());
        }

        // 5. process: 执行结果处理 Step
        dataSetResultFilterManager.process(context);

        // 6. 更新结果（process 可能修改了 pagingResult）
        PagingResultImpl processedResult = context.getPagingResult();

        return DbQueryResult.of(processedResult, dbQueryResult.getQueryEngine());
    }
}
