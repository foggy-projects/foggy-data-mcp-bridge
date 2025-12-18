package com.foggyframework.dataset.jdbc.model.service.impl;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.jdbc.model.def.query.request.JdbcQueryRequestDef;
import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQueryResult;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.DataSetResultFilterManager;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.jdbc.model.service.QueryFacade;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryModelLoader;
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
    private JdbcQueryModelLoader jdbcQueryModelLoader;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    @Resource
    private DataSetResultFilterManager dataSetResultFilterManager;

    @Override
    public PagingResultImpl queryModelData(PagingRequest<JdbcQueryRequestDef> form) {
        return queryModelData(form, ModelResultContext.QueryType.NORMAL);
    }

    @Override
    public PagingResultImpl queryModelData(PagingRequest<JdbcQueryRequestDef> form,
                                           ModelResultContext.QueryType queryType) {
        // 创建上下文
        ModelResultContext context = new ModelResultContext(form, null);
        context.setQueryType(queryType);

        // 执行完整查询流程
        JdbcQueryResult result = doQuery(context);

        return result.getPagingResult();
    }

    @Override
    public JdbcQueryResult queryModelResult(PagingRequest<JdbcQueryRequestDef> form) {
        // 创建上下文
        ModelResultContext context = new ModelResultContext(form, null);
        context.setQueryType(ModelResultContext.QueryType.NORMAL);

        return doQuery(context);
    }

    @Override
    public JdbcQueryResult queryModelResult(ModelResultContext context) {
        return doQuery(context);
    }

    /**
     * 执行查询的核心流程
     * <p>
     * 生命周期：beforeQuery -> query -> process
     * </p>
     */
    private JdbcQueryResult doQuery(ModelResultContext context) {
        PagingRequest<JdbcQueryRequestDef> form = context.getRequest();
        JdbcQueryRequestDef queryRequest = form.getParam();

        // 1. 获取查询模型
        String queryModelName = queryRequest.getQueryModel();
        JdbcQueryModel jdbcQueryModel = jdbcQueryModelLoader.getJdbcQueryModel(queryModelName);

        // 2. beforeQuery: 执行预处理 Step（AutoGroupBy、InlineExpression、Authorization 等）
        dataSetResultFilterManager.beforeQuery(context);

        if (log.isDebugEnabled()) {
            log.debug("QueryFacade.beforeQuery completed, queryType={}, model={}",
                    context.getQueryType(), queryModelName);
        }

        // 3. 执行查询（使用带 context 的方法，以便复用预处理结果）
        JdbcQueryResult jdbcQueryResult = jdbcQueryModel.query(systemBundlesContext, context);

        // 4. 设置查询结果到上下文
        context.setPagingResult(jdbcQueryResult.getPagingResult());
        if (jdbcQueryResult.getQueryEngine() != null) {
            context.setJdbcQuery(jdbcQueryResult.getQueryEngine().getJdbcQuery());
            context.setJdbcQueryModel(jdbcQueryResult.getQueryEngine().getJdbcQueryModel());
        }

        // 5. process: 执行结果处理 Step
        dataSetResultFilterManager.process(context);

        // 6. 更新结果（process 可能修改了 pagingResult）
        PagingResultImpl processedResult = context.getPagingResult();

        return JdbcQueryResult.of(processedResult, jdbcQueryResult.getQueryEngine());
    }
}
