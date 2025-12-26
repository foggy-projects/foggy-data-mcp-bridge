package com.foggyframework.dataset.jdbc.model.service;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.jdbc.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.jdbc.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.ModelResultContext;
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
}
