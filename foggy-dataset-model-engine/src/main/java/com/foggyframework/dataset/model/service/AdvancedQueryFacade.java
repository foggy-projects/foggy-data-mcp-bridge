package com.foggyframework.dataset.model.service;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.port.AdvancedQueryExecutionPort;
import com.foggyframework.dataset.model.plugins.query_execution.ManagedRelationOptions;
import com.foggyframework.dataset.model.plugins.query_execution.ManagedSqlRelation;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.PagingResultImpl;

/**
 * Engine-internal advanced query port.
 * <p>
 * 统一封装查询生命周期：beforeQuery -> query -> process
 * 供 Controller 层、SemanticQueryService 等统一调用。
 * </p>
 *
 * <h3>职责</h3>
 * <ul>
 *     <li>创建 ModelResultContext</li>
 *     <li>执行 beforeQuery Step（AutoGroupBy、InlineExpression、Authorization 等）</li>
 *     <li>执行查询</li>
 *     <li>执行 process Step（结果处理）</li>
 * </ul>
 *
 * @author foggy-framework
 * @since 8.0.0
 */
public interface AdvancedQueryFacade extends com.foggyframework.dataset.model.api.QueryFacade,
        AdvancedQueryExecutionPort {

    /**
     * 执行查询（简化版）
     * <p>
     * 内部完成完整的查询生命周期：beforeQuery -> query -> process
     * 未显式提供 namespace：嵌套调用继承当前 scope，root 调用使用默认 namespace。
     * </p>
     *
     * @param form 查询请求
     * @return 查询结果
     */
    @Deprecated(since = "9.3.5", forRemoval = false)
    PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form);

    /**
     * 执行查询(带命名空间)
     *
     * @param form      查询请求
     * @param namespace 显式命名空间（空字符串或null表示默认命名空间）
     * @return 查询结果
     */
    @Deprecated(since = "9.3.5", forRemoval = false)
    PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form, String namespace);

    /**
     * 执行查询（带认证和命名空间）
     *
     * @param form          查询请求
     * @param authorization 认证令牌
     * @param namespace     显式命名空间（空字符串或null表示默认命名空间）
     * @return 查询结果
     */
    @Deprecated(since = "9.3.5", forRemoval = false)
    PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form, String authorization, String namespace);

    /**
     * 执行查询（带查询类型）
     * <p>未显式提供 namespace：嵌套调用继承当前 scope，root 调用使用默认 namespace。</p>
     *
     * @param form      查询请求
     * @param queryType 查询类型（NORMAL、SEMANTIC）
     * @return 查询结果
     */
    @Deprecated(since = "9.3.5", forRemoval = false)
    PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form,
                                    ModelResultContext.QueryType queryType);

    /**
     * 执行查询（完整版，返回 JdbcQueryResult）
     * <p>
     * 内部完成完整的查询生命周期，返回包含查询引擎信息的结果。
     * 未显式提供 namespace：嵌套调用继承当前 scope，root 调用使用默认 namespace。
     * </p>
     *
     * @param form 查询请求
     * @return 查询结果（包含查询引擎信息）
     */
    @Deprecated(since = "9.3.5", forRemoval = false)
    DbQueryResult queryModelResult(PagingRequest<DbQueryRequestDef> form);

    /**
     * 执行查询（完整版，带上下文）
     * <p>
     * 允许调用方提供预配置的 ModelResultContext，
     * 用于 SemanticQueryService 等需要设置 SecurityContext 的场景。
     * context 中 null/blank namespace 按显式默认 namespace 处理。
     * </p>
     *
     * @param context 预配置的上下文（必须已设置 request）
     * @return 查询结果（包含查询引擎信息）
     */
    @Override
    @Deprecated(since = "9.3.5", forRemoval = false)
    DbQueryResult queryModelResult(ModelResultContext context);

    /**
     * 仅生成 SQL，不执行查询
     *
     * <p>用于 CTE/子查询组合场景：走完 beforeQuery pipeline（权限注入、AutoGroupBy 等），
     * 然后调用 {@code JdbcQueryModelImpl.generateSql()} 截取 SQL + 参数，不实际执行。</p>
     *
     * @param context 预配置的上下文（必须已设置 request）
     * @return SQL 生成结果（含 SQL 字符串、绑定参数）
     */
    @Override
    @Deprecated(since = "9.3.5", forRemoval = false)
    SqlGenerationResult buildSqlOnly(ModelResultContext context);

    /**
     * 准备受管 SQL Relation（Pivot SQL Pushdown 专用）
     *
     * <p>完整生命周期：loadModel → beforeQuery → analysisQueryRequest → selected beforeExecute steps
     * → return ManagedSqlRelation（含 capability metadata）。</p>
     *
     * <p>不执行数据库查询。返回的 ManagedSqlRelation 可被 PivotAxisDomainSqlPlanner
     * 安全包装为外层 CTE SQL。</p>
     *
     * @param context 预配置的上下文（必须已设置 request、namespace、securityContext）
     * @param options 准备选项
     * @return 受管 SQL Relation
     */
    @Override
    @Deprecated(since = "9.3.5", forRemoval = false)
    ManagedSqlRelation prepareManagedRelation(ModelResultContext context, ManagedRelationOptions options);

    /**
     * 执行受管 SQL Relation（外层包装后的最终 SQL）
     *
     * @param relation 之前 prepare 返回的 ManagedSqlRelation
     * @param finalSql 外层包装后的最终 SQL
     * @param finalParams 外层包装后的最终参数
     * @return 查询结果
     */
    @Override
    @Deprecated(since = "9.3.5", forRemoval = false)
    DbQueryResult executeManagedRelation(ManagedSqlRelation relation, String finalSql, java.util.List<Object> finalParams);
}
