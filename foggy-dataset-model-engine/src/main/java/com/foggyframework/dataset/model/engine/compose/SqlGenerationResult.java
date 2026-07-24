package com.foggyframework.dataset.model.engine.compose;

import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL 生成结果 -- 仅截取 SQL 不执行
 *
 * <p>用于 CTE/子查询组合场景：将 QM 生成的完整 SQL（含 TM JOIN + 权限 slice）
 * 视为黑盒"视图"，供 {@link CteComposer} 拼接。</p>
 *
 * <p><b>CTE Wrapping support (9.2.0+):</b> When the engine uses two-stage CTE wrapping
 * for window calculated fields, the result carries structured {@link CteStage} objects
 * so that {@code ComposePlanner} can flatten them as sibling CTEs instead of nesting
 * {@code WITH} clauses (which is illegal on MSSQL/MySQL 5.7 inline subqueries).
 * When no CTE wrapping is needed, {@code cteStages} is empty and {@code sql} contains
 * the complete single-pass SQL (backward compatible).</p>
 *
 * @author Foggy Framework
 * @since 8.2.0
 */
@Getter
public class SqlGenerationResult {

    /**
     * A single CTE stage produced by the engine's two-stage wrapping pipeline.
     *
     * <p>Example: for a window CF query, the engine produces:
     * <ul>
     *   <li>Stage 1: {@code CteStage("stage1", "SELECT dims, aggs FROM table WHERE ... GROUP BY ...", params)}</li>
     * </ul>
     * and the main {@link #sql} field holds the outer SELECT that references {@code stage1}.</p>
     *
     * @param alias  CTE alias (e.g., "stage1")
     * @param sql    SQL body (without the {@code WITH alias AS (...)} wrapper)
     * @param params bind parameters for this stage
     * @since 9.2.0
     */
    public record CteStage(String alias, String sql, List<Object> params) {}

    /**
     * 生成的完整 SQL（不含分页）
     *
     * <p>When {@link #hasCteStages()} is true, this field contains ONLY the outer SELECT
     * (without the {@code WITH stage1 AS (...)} prefix). The CTE prefix must be assembled
     * by the caller using the {@link #cteStages} list.</p>
     *
     * <p>When {@link #hasCteStages()} is false, this field contains the complete SQL
     * (legacy single-pass or self-assembled CTE for direct execution).</p>
     */
    private final String sql;

    /**
     * SQL 绑定参数（按占位符顺序）
     *
     * <p>When {@link #hasCteStages()} is true, this list contains ONLY the params for the
     * outer SELECT. Stage-specific params are in {@link CteStage#params()}.
     * Full param ordering: stage1.params + stage2.params + ... + this.params.</p>
     */
    private final List<Object> params;

    /**
     * 查询引擎实例（保留以便后续提取列信息等元数据）
     */
    private final JdbcModelQueryEngine queryEngine;

    /**
     * Optional CTE stages produced by the engine's two-stage wrapping pipeline.
     * Empty when single-pass SQL is used (no CTE wrapping needed).
     *
     * @since 9.2.0
     */
    private final List<CteStage> cteStages;

    /**
     * SQL generation diagnostics captured from {@code ModelResultContext.extData}.
     *
     * <p>Consumers such as Compose can inspect {@code queryStagePlan} without
     * reparsing SQL shape or coupling to engine internals.</p>
     *
     * @since 9.3.0
     */
    private final Map<String, Object> diagnostics;

    /**
     * Legacy constructor — single-pass SQL (no CTE stages).
     */
    public SqlGenerationResult(String sql, List<Object> params, JdbcModelQueryEngine queryEngine) {
        this(sql, params, queryEngine, Collections.emptyList());
    }

    /**
     * Full constructor with optional CTE stages.
     *
     * @param sql         outer SELECT SQL (or complete SQL when no CTE stages)
     * @param params      params for the outer SELECT (or complete params when no CTE stages)
     * @param queryEngine engine instance
     * @param cteStages   prerequisite CTE stages (empty for single-pass)
     * @since 9.2.0
     */
    public SqlGenerationResult(String sql, List<Object> params, JdbcModelQueryEngine queryEngine,
                               List<CteStage> cteStages) {
        this(sql, params, queryEngine, cteStages, Collections.emptyMap());
    }

    /**
     * Full constructor with optional CTE stages and diagnostics.
     *
     * @param sql         outer SELECT SQL (or complete SQL when no CTE stages)
     * @param params      params for the outer SELECT (or complete params when no CTE stages)
     * @param queryEngine engine instance
     * @param cteStages   prerequisite CTE stages (empty for single-pass)
     * @param diagnostics immutable SQL generation diagnostics snapshot
     * @since 9.3.0
     */
    public SqlGenerationResult(String sql, List<Object> params, JdbcModelQueryEngine queryEngine,
                               List<CteStage> cteStages, Map<String, Object> diagnostics) {
        this.sql = sql;
        this.params = params;
        this.queryEngine = queryEngine;
        this.cteStages = cteStages != null ? cteStages : Collections.emptyList();
        this.diagnostics = diagnostics != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(diagnostics))
                : Collections.emptyMap();
    }

    /**
     * Whether this result contains structured CTE stages that need to be
     * flattened by the caller (e.g., ComposePlanner).
     *
     * @return true if the engine used CTE wrapping and the stages are available
     * @since 9.2.0
     */
    public boolean hasCteStages() {
        return cteStages != null && !cteStages.isEmpty();
    }

    /**
     * Assemble the complete SQL by prepending the CTE stages.
     * Useful for direct execution (not via ComposePlanner).
     *
     * @return complete SQL with {@code WITH stage1 AS (...)} prefix if CTE stages exist,
     *         or the plain {@link #sql} otherwise
     * @since 9.2.0
     */
    public String getAssembledSql() {
        if (!hasCteStages()) {
            return sql;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("WITH ");
        for (int i = 0; i < cteStages.size(); i++) {
            if (i > 0) sb.append(",\n");
            CteStage stage = cteStages.get(i);
            sb.append(stage.alias()).append(" AS (\n").append(stage.sql()).append("\n)");
        }
        sb.append("\n").append(sql);
        return sb.toString();
    }

    /**
     * Assemble bind parameters in the same order as {@link #getAssembledSql()} placeholders.
     *
     * @return stage params followed by outer SELECT params
     * @since 9.2.0
     */
    public List<Object> getAssembledParams() {
        if (!hasCteStages()) {
            return params != null ? params : List.of();
        }
        List<Object> assembled = new ArrayList<>();
        for (CteStage stage : cteStages) {
            if (stage.params() != null) {
                assembled.addAll(stage.params());
            }
        }
        if (params != null) {
            assembled.addAll(params);
        }
        return assembled;
    }
}
