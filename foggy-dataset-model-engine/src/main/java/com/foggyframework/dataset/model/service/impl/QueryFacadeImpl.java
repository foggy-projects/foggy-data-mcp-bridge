package com.foggyframework.dataset.model.service.impl;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.plugins.result_set_filter.DataSetResultFilterManager;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import com.foggyframework.dataset.model.service.internal.QueryFacadeDtoMapper;
import com.foggyframework.dataset.model.spi.NamespaceContext;
import com.foggyframework.dataset.model.spi.NamespaceScope;
import com.foggyframework.dataset.model.spi.QueryEngine;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.model.api.QueryFacadeRequest;
import com.foggyframework.dataset.model.api.QueryFacadeResult;
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
 *   <li><b>L1 缓存</b>：由 {@link com.foggyframework.dataset.model.plugins.result_set_filter.L1CacheStep} 处理</li>
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
public class QueryFacadeImpl implements AdvancedQueryFacade {

    @Resource
    private QueryModelLoader queryModelLoader;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    @Resource
    private DataSetResultFilterManager dataSetResultFilterManager;

    @Override
    public QueryFacadeResult query(QueryFacadeRequest request) {
        PagingRequest<DbQueryRequestDef> legacyRequest = QueryFacadeDtoMapper.toLegacyRequest(request);
        PagingResultImpl result = queryModelData(
                legacyRequest,
                request.getAuthorization(),
                request.getNamespace(),
                ModelResultContext.QueryType.NORMAL,
                request.isNamespaceProvided()
        );
        return QueryFacadeDtoMapper.toResult(result);
    }

    @Override
    public PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form) {
        return queryModelData(form, null, null, ModelResultContext.QueryType.NORMAL, false);
    }

    @Override
    public PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form, String namespace) {
        return queryModelData(form, null, namespace, ModelResultContext.QueryType.NORMAL, true);
    }

    @Override
    public PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form, String authorization, String namespace) {
        return queryModelData(form, authorization, namespace, ModelResultContext.QueryType.NORMAL, true);
    }

    @Override
    public PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form,
                                           ModelResultContext.QueryType queryType) {
        return queryModelData(form, null, null, queryType, false);
    }

    /**
     * 执行查询（内部统一方法）
     *
     * @param form          查询请求
     * @param authorization 认证令牌
     * @param namespace     命名空间
     * @param queryType     查询类型
     * @return 查询结果
     */
    private PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form,
                                            String authorization,
                                            String namespace,
                                            ModelResultContext.QueryType queryType,
                                            boolean namespaceProvided) {
        // 创建上下文
        ModelResultContext context = new ModelResultContext(form, null);
        context.setQueryType(queryType);
        context.setNamespace(namespace);

        // 设置认证信息到安全上下文
        if (authorization != null) {
            ModelResultContext.SecurityContext securityContext = new ModelResultContext.SecurityContext();
            securityContext.setAuthorization(authorization);
            context.setSecurityContext(securityContext);
        }

        // 执行完整查询流程
        DbQueryResult result = doQuery(context, namespaceProvided);

        return result.getPagingResult();
    }

    @Override
    public DbQueryResult queryModelResult(PagingRequest<DbQueryRequestDef> form) {
        // 创建上下文
        ModelResultContext context = new ModelResultContext(form, null);
        context.setQueryType(ModelResultContext.QueryType.NORMAL);

        return doQuery(context, false);
    }

    @Override
    public DbQueryResult queryModelResult(ModelResultContext context) {
        return doQuery(context, true);
    }

    /**
     * 执行查询的核心流程
     * <p>
     * 生命周期：beforeQuery -> [skipQuery check] -> query (with L2 cache) -> process
     * </p>
     */
    private DbQueryResult doQuery(ModelResultContext context, boolean namespaceProvided) {
        try (NamespaceScope ignored = openNamespaceScope(context, namespaceProvided)) {
            // 0. 固定本次查询实际使用的 canonical namespace，供 loader/filter/cache 共用
            String effectiveNamespace = context.getNamespace();

            PagingRequest<DbQueryRequestDef> form = context.getRequest();
            DbQueryRequestDef queryRequest = form.getParam();
            context.mergeRequestExtData(queryRequest.getExtData());

            // 1. 获取查询模型（带命名空间）
            String queryModelName = queryRequest.getQueryModel();
            QueryModel jdbcQueryModel = resolveAndPin(context, queryModelName, effectiveNamespace);

            // 1.1 提前设置 jdbcQueryModel，供 beforeQuery Step 使用
            context.setQueryModel(jdbcQueryModel);

            // 2. beforeQuery: 执行预处理 Step（L1Cache、Authorization、AutoGroupBy、InlineExpression 等）
            dataSetResultFilterManager.beforeQuery(context);

            if (log.isDebugEnabled()) {
                log.debug("AdvancedQueryFacade.beforeQuery completed, queryType={}, model={}, skipQuery={}, namespace={}",
                        context.getQueryType(), queryModelName, context.isSkipQuery(), context.getNamespace());
            }

            // 3. 检查是否跳过查询（如 L1 缓存命中，或 TimeWindow 拦截）
            if (context.isSkipQuery()) {
                if (context.getQueryResult() == null) {
                    context.setPagingResult(new com.foggyframework.dataset.model.PagingResultImpl());
                    context.setQueryResult(DbQueryResult.of(context.getPagingResult(), null));
                }
                // 执行 process Step（缓存结果也需要经过结果处理）
                dataSetResultFilterManager.process(context);
                return context.getQueryResult();
            }

            // 4. 执行查询（内部包含 L2 缓存逻辑）
            DbQueryResult dbQueryResult = jdbcQueryModel.query(
                    executionBundlesContext(context), context);
            QueryEngine queryEngine = dbQueryResult.getQueryEngine();

            // Query execution must remain attached to the exact model instance
            // resolved from the pinned catalog snapshot. Accepting an engine
            // created for another model would combine that model with the
            // original catalog and datasource binding identities.
            applyPinnedQueryEngine(context, jdbcQueryModel, queryEngine);

            // 5. 设置查询结果到上下文
            context.setPagingResult(dbQueryResult.getPagingResult());
            context.setQueryResult(dbQueryResult);

            // 6. process: 执行结果处理 Step（包含 L1 缓存写入）
            dataSetResultFilterManager.process(context);

            // 7. 更新结果（process 可能修改了 pagingResult）
            PagingResultImpl processedResult = context.getPagingResult();

            return DbQueryResult.of(processedResult, queryEngine);
        }
    }

    private void applyPinnedQueryEngine(
            ModelResultContext context,
            QueryModel pinnedModel,
            QueryEngine queryEngine
    ) {
        // The model may update the shared context while executing. Restore the
        // pinned reference before validating or publishing any returned state.
        context.setQueryModel(pinnedModel);
        if (queryEngine == null) {
            return;
        }
        if (queryEngine.getJdbcQueryModel() != pinnedModel) {
            throw new IllegalStateException(
                    "QUERY_ENGINE_MODEL_MISMATCH: query engine did not retain the pinned query model"
            );
        }
        context.setQuery(queryEngine.getJdbcQuery());
    }

    @Override
    public SqlGenerationResult buildSqlOnly(ModelResultContext context) {
        try (NamespaceScope ignored = openNamespaceScope(context, true)) {
            String effectiveNamespace = context.getNamespace();

            PagingRequest<DbQueryRequestDef> form = context.getRequest();
            DbQueryRequestDef queryRequest = form.getParam();
            context.mergeRequestExtData(queryRequest.getExtData());

            // 1. 获取查询模型
            String queryModelName = queryRequest.getQueryModel();
            QueryModel queryModel = resolveAndPin(context, queryModelName, effectiveNamespace);

            // 2. beforeQuery: 执行预处理（权限注入、AutoGroupBy 等）
            dataSetResultFilterManager.beforeQuery(context);

            // TimeWindowInterceptor may replace the query with a Compose plan.
            // The caller owns final plan compilation because it has the semantic service instance.
            if (context.isSkipQuery()
                    && context.getExtData() != null
                    && (context.getExtData().containsKey("timeWindowPlan")
                    || context.getExtData().containsKey("comparativePlan"))) {
                return new SqlGenerationResult("", java.util.List.of(), null);
            }

            // 3. 仅生成 SQL，不执行
            if (queryModel instanceof JdbcQueryModelImpl jdbcImpl) {
                return jdbcImpl.generateSql(executionBundlesContext(context), context);
            }

            throw new UnsupportedOperationException(
                    "buildSqlOnly only supports JDBC query models, got: " + queryModel.getClass().getName());
        }
    }

    @Override
    public com.foggyframework.dataset.model.plugins.query_execution.ManagedSqlRelation prepareManagedRelation(
            ModelResultContext context,
            com.foggyframework.dataset.model.plugins.query_execution.ManagedRelationOptions options) {
        try (NamespaceScope ignored = openNamespaceScope(context, true)) {
            String effectiveNamespace = context.getNamespace();

            PagingRequest<DbQueryRequestDef> form = context.getRequest();
            DbQueryRequestDef queryRequest = form.getParam();
            context.mergeRequestExtData(queryRequest.getExtData());
            String queryModelName = queryRequest.getQueryModel();
            QueryModel queryModel = resolveAndPin(context, queryModelName, effectiveNamespace);

            // beforeQuery: Authorization, AutoGroupBy, InlineExpression, systemSlice, etc.
            dataSetResultFilterManager.beforeQuery(context);

            if (context.isSkipQuery()) {
                throw new com.foggyframework.dataset.model.engine.pivot.sql.PivotPushdownUnsupportedException(
                        "prepareManagedRelation does not support skipQuery (e.g. TimeWindow intercept). Fallback to memory path.");
            }

            if (queryModel instanceof JdbcQueryModelImpl jdbcImpl) {
                return jdbcImpl.prepareManagedRelation(
                        executionBundlesContext(context), context, options);
            }

            throw new com.foggyframework.dataset.model.engine.pivot.sql.PivotPushdownUnsupportedException(
                    "prepareManagedRelation only supports JDBC query models, got: " + queryModel.getClass().getName());
        }
    }

    private QueryModel resolveAndPin(
            ModelResultContext context,
            String queryModelName,
            String namespace
    ) {
        if (context.getExecutionBundlesContext() != null) {
            if (context.getCatalogIdentity() == null
                    || context.getQueryModel() == null
                    || !queryModelName.equals(context.getCanonicalModelName())) {
                throw new IllegalStateException(
                        "CANDIDATE_MODEL_RESOLUTION_REQUIRED: request-local execution "
                                + "requires one matching catalog resolution");
            }
            return context.getQueryModel();
        }
        CatalogResolution<QueryModel> resolution =
                queryModelLoader.resolveJdbcQueryModel(queryModelName, namespace);
        // Compatibility for external/legacy QueryModelLoader implementations
        // and existing mocks. Such a result is deliberately untracked and must
        // therefore fail closed in cache consumers.
        if (resolution == null) {
            QueryModel model = queryModelLoader.getJdbcQueryModel(queryModelName, namespace);
            context.pinUntrackedQueryModel(model);
            return model;
        }
        context.pinCatalogResolution(resolution, namespace);
        return resolution.model();
    }

    private SystemBundlesContext executionBundlesContext(ModelResultContext context) {
        return context != null && context.getExecutionBundlesContext() != null
                ? context.getExecutionBundlesContext()
                : systemBundlesContext;
    }

    @Override
    public DbQueryResult executeManagedRelation(
            com.foggyframework.dataset.model.plugins.query_execution.ManagedSqlRelation relation,
            String finalSql, java.util.List<Object> finalParams) {
        com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionContext executionContext =
                relation.getExecutionContext();
        ModelResultContext resultContext = executionContext == null
                ? null
                : executionContext.getModelResultContext();
        try (NamespaceScope ignored = openNamespaceScope(resultContext, resultContext != null)) {
            com.foggyframework.dataset.model.spi.QueryModel qm =
                    relation.getQueryEngine().getJdbcQueryModel();
            if (qm instanceof JdbcQueryModelImpl jdbcImpl) {
                return jdbcImpl.executeManagedRelation(executionContext, finalSql, finalParams);
            }

            throw new UnsupportedOperationException(
                    "executeManagedRelation only supports JDBC query models.");
        }
    }

    /**
     * 参数是否存在由入口重载决定，不能用 namespace 值是否为空来推断：显式 null 是
     * default，而没有 namespace 参数才继承 outer scope。解析后把同一个 canonical
     * 值写回一次性执行上下文，避免 loader/filter/cache 使用不同 namespace。
     */
    private NamespaceScope openNamespaceScope(ModelResultContext context, boolean namespaceProvided) {
        NamespaceScope scope = namespaceProvided
                ? NamespaceContext.open(context.getNamespace())
                : NamespaceContext.openInherited();
        try {
            if (context != null) {
                context.setNamespace(NamespaceContext.getNamespace());
            }
            return scope;
        } catch (RuntimeException | Error failure) {
            scope.close();
            throw failure;
        }
    }

}
