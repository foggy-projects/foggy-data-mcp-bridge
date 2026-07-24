package com.foggyframework.dataset.model.plugins.query_execution;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * 受管关系代数
 * <p>
 * 在 PREPARE_MANAGED_RELATION 阶段后返回。
 * 包含经过权限校验、预聚合重写后的基础 SQL、参数、方言和引擎引用，
 * 供外层 (如 Pivot Planner) 进行包装。
 *
 * <p>外层 Planner 必须先校验 capability metadata（{@link #isWrappable()},
 * {@link #isPermissionValidated()}）再生成外层 SQL。
 * 任何关键 capability 不满足时必须 fail-closed。</p>
 */
@Getter
public class ManagedSqlRelation {
    private final String sql;
    private final List<Object> params;
    private final FDialect dialect;
    private final JdbcModelQueryEngine queryEngine;
    private final QueryExecutionContext executionContext;

    // ========== Capability Metadata ==========

    /**
     * 当前 SQL 是否可以被安全包裹在外层 CTE 中
     * <p>如果为 false，Planner 必须 fail-closed，不生成外层 SQL。</p>
     */
    private final boolean wrappable;

    /**
     * 物理列权限是否已校验通过
     * <p>如果为 false，外层不得直接执行包装后的 SQL。</p>
     */
    private final boolean permissionValidated;

    /**
     * 预聚合 SQL rewrite 是否已应用
     * <p>如果为 true，base SQL 已经是预聚合改写后的 SQL。</p>
     */
    private final boolean preAggApplied;

    /**
     * 度量元数据列表
     * <p>由 prepare 阶段填充，含 AdditiveKind 和聚合函数名。
     * Planner 消费此列表决定 domain CTE 聚合策略。</p>
     */
    private final List<ManagedMetricMetadata> metrics;

    /**
     * 完整构造函数（含 capability metadata）
     */
    public ManagedSqlRelation(String sql, List<Object> params, FDialect dialect,
                              JdbcModelQueryEngine queryEngine, QueryExecutionContext executionContext,
                              boolean wrappable, boolean permissionValidated, boolean preAggApplied,
                              List<ManagedMetricMetadata> metrics) {
        this.sql = sql;
        this.params = params;
        this.dialect = dialect;
        this.queryEngine = queryEngine;
        this.executionContext = executionContext;
        this.wrappable = wrappable;
        this.permissionValidated = permissionValidated;
        this.preAggApplied = preAggApplied;
        this.metrics = metrics != null ? metrics : Collections.emptyList();
    }

    /**
     * 向后兼容构造函数（不含 capability metadata，默认不可包裹）
     */
    public ManagedSqlRelation(String sql, List<Object> params, FDialect dialect,
                              JdbcModelQueryEngine queryEngine, QueryExecutionContext executionContext) {
        this(sql, params, dialect, queryEngine, executionContext,
             false, false, false, Collections.emptyList());
    }
}
