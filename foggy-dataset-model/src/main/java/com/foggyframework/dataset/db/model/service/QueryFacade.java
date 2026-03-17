package com.foggyframework.dataset.db.model.service;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.PagingResultImpl;

/**
 * 查询门面接口
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
public interface QueryFacade {

    /**
     * 执行查询（简化版）
     * <p>
     * 内部完成完整的查询生命周期：beforeQuery -> query -> process
     * </p>
     *
     * @param form 查询请求
     * @return 查询结果
     */
    PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form);

    /**
     * 执行查询(带命名空间)
     *
     * @param form      查询请求
     * @param namespace 命名空间（空字符串或null表示默认命名空间）
     * @return 查询结果
     */
    PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form, String namespace);

    /**
     * 执行查询（带认证和命名空间）
     *
     * @param form          查询请求
     * @param authorization 认证令牌
     * @param namespace     命名空间（空字符串或null表示默认命名空间）
     * @return 查询结果
     */
    PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form, String authorization, String namespace);

    /**
     * 执行查询（带查询类型）
     *
     * @param form      查询请求
     * @param queryType 查询类型（NORMAL、SEMANTIC）
     * @return 查询结果
     */
    PagingResultImpl queryModelData(PagingRequest<DbQueryRequestDef> form,
                                    ModelResultContext.QueryType queryType);

    /**
     * 执行查询（完整版，返回 JdbcQueryResult）
     * <p>
     * 内部完成完整的查询生命周期，返回包含查询引擎信息的结果。
     * </p>
     *
     * @param form 查询请求
     * @return 查询结果（包含查询引擎信息）
     */
    DbQueryResult queryModelResult(PagingRequest<DbQueryRequestDef> form);

    /**
     * 执行查询（完整版，带上下文）
     * <p>
     * 允许调用方提供预配置的 ModelResultContext，
     * 用于 SemanticQueryService 等需要设置 SecurityContext 的场景。
     * </p>
     *
     * @param context 预配置的上下文（必须已设置 request）
     * @return 查询结果（包含查询引擎信息）
     */
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
    SqlGenerationResult buildSqlOnly(ModelResultContext context);
}
