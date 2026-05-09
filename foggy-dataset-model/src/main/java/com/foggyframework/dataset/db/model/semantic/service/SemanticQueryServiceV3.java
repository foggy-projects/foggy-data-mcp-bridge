package com.foggyframework.dataset.db.model.semantic.service;

import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * V3版本语义查询服务接口
 *
 * <p>与V2的核心区别：字段名直接使用，无需判断和拼接后缀</p>
 *
 * <p>由于元数据已经将维度展开为独立的 $id 和 $caption 字段，
 * AI 可以直接从元数据中选择字段名使用，无需额外的后缀处理逻辑。</p>
 *
 * <p>V3 简化了以下场景：</p>
 * <ul>
 *   <li>columns: 直接使用 salesDate$id 或 salesDate$caption</li>
 *   <li>slice: 直接使用字段名作为过滤条件</li>
 *   <li>orderBy: 直接使用字段名排序</li>
 *   <li>groupBy: 直接使用字段名分组</li>
 * </ul>
 */
public interface SemanticQueryServiceV3 {

    /**
     * 执行语义查询（V3版本）
     *
     * @param model   模型名称
     * @param request 语义查询请求（字段名已包含后缀，无需归一化处理）
     * @param mode    查询模式: execute(执行) | validate(验证)
     * @param context 请求上下文（命名空间 + 安全信息），不可为 null，可用 {@link SemanticRequestContext#empty()}
     * @return 查询响应
     */
    SemanticQueryResponse queryModel(String model, SemanticQueryRequest request, String mode,
                                     SemanticRequestContext context);

    /**
     * 验证查询请求（V3版本）
     *
     * @param model   模型名称
     * @param request 原始请求
     * @param context 请求上下文（命名空间 + 安全信息），不可为 null，可用 {@link SemanticRequestContext#empty()}
     * @return 验证响应
     */
    SemanticQueryResponse validateQuery(String model, SemanticQueryRequest request,
                                        SemanticRequestContext context);

    /**
     * 仅生成 SQL，不执行查询
     *
     * <p>用于 CTE/子查询组合场景：走完语义层转换 + beforeQuery pipeline（权限注入等），
     * 截取 QM 生成的完整 SQL，不执行。</p>
     *
     * @param model   模型名称
     * @param request 语义查询请求
     * @param context 请求上下文
     * @return SQL 生成结果
     */
    SqlGenerationResult generateSql(String model, SemanticQueryRequest request,
                                    SemanticRequestContext context);

    /**
     * Resolve a semantic QM field to the physical SQL expression used inside
     * base-model SQL, for compose-side expression injection.
     *
     * <p>Implementations that cannot expose model metadata may return empty;
     * compose tests/fakes then fall back to the field name. Production
     * implementations should fail closed by returning empty when the field is
     * unknown.</p>
     */
    default Optional<String> resolveFieldSqlExpression(String model, String field, String namespace) {
        return Optional.empty();
    }

    /**
     * Execute raw SQL compiled by M6 {@link com.foggyframework.dataset.db.model.engine.compose.compilation.ComposeSqlCompiler}
     * and return rows.
     *
     * <p>Used by M7 {@code PlanExecution.executePlan} as the raw-SQL primitive
     * behind {@code QueryPlan.execute()}. Does NOT re-run governance —
     * governance already applied during {@link #generateSql} inside
     * {@code ComposeSqlCompiler}.</p>
     *
     * @param sql         compiled SQL (positional ? placeholders)
     * @param params      positional bind parameters
     * @param routeModel  reserved for multi-datasource routing (M8+);
     *                    ignored in M7 single-datasource deployment.
     *                    Always null-safe.
     * @return list of rows (Map&lt;column_name, value&gt;)
     * @throws RuntimeException with message prefix "executeSql failed:"
     *         when executor not configured / SQL syntax error / DB connection error
     */
    List<Map<String, Object>> executeSql(String sql, List<Object> params, String routeModel);
}
