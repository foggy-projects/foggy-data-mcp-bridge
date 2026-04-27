package com.foggyframework.dataset.db.model.engine.compose;

import java.util.ArrayList;
import java.util.List;

/**
 * CTE 组合器 -- 纯字符串拼接层
 *
 * <p>将多个 QM 生成的 SQL（黑盒"视图"）拼接为 CTE 或子查询形式，
 * 不侵入查询引擎内部。</p>
 *
 * <h3>CTE 模式（PostgreSQL、MySQL 8+、SQL Server、SQLite 3.35+）</h3>
 * <pre>{@code
 * WITH cte_0 AS (sql₁), cte_1 AS (sql₂)
 * SELECT cte_0.col1, cte_1.col2
 * FROM cte_0
 * LEFT JOIN cte_1 ON cte_0.key = cte_1.key
 * }</pre>
 *
 * <h3>子查询模式（MySQL 5.7 回退）</h3>
 * <pre>{@code
 * SELECT t0.col1, t1.col2
 * FROM (sql₁) AS t0
 * LEFT JOIN (sql₂) AS t1 ON t0.key = t1.key
 * }</pre>
 *
 * <h3>Cross-repo API drift vs. Python (8.2.0.beta · MINOR DRIFT, intentional)</h3>
 *
 * <p>Python 的 {@code foggy.dataset_model.engine.compose.cte_composer.CteComposer.compose}
 * 顶层签名带一个 {@code select_columns} 参数，可以在 compose 层"重写"外层
 * {@code SELECT} 列；Java 这边没有这个顶层参数。功能上 Java 用两个旁路把它分担掉：</p>
 *
 * <ol>
 *   <li>{@link CteUnit#getSelectColumns()} — 每个 unit 自带一份外层 SELECT 列，
 *       {@link #buildMultiCte} / {@link #buildMultiSubquery} 按 unit 逐一展开
 *       （{@code unit.alias.col1, unit.alias.col2, ...}）。这覆盖了"按 unit 选列"
 *       场景，但<b>无法</b>表达 Python 那种"先 join 再统一裁列"的能力。</li>
 *   <li>{@link com.foggyframework.dataset.db.model.engine.compose.compilation.ComposePlanner}
 *       的 M6 编译路径在 {@code JoinPlan} 场景<b>不走本类</b>，而是自己手工组装
 *       2-branch CTE/subquery（参见 {@code SQL assembly helpers} 段注释）。
 *       原因是 Java 的 {@link JoinSpec} 仅支持<b>单列等值 JOIN</b>
 *       （{@code leftKey = rightKey}），Python 的 {@code JoinSpec} 携带 raw
 *       {@code on_condition}，能力更强。M6 为了不动这套 v1.3 / 8.2 的共享基础设施，
 *       直接在 planner 里自带 SELECT / JOIN 组装。</li>
 * </ol>
 *
 * <p>因此本类的真实使用面只有：单 base 单元件包装、以及"单列等值 join + 按 unit 选列"
 * 的简单 compose 用例。<b>派生 / 多列条件 / 复杂 ON 子句都走 ComposePlanner，不走这里。</b>
 * 后续若要把 API 完全对齐 Python（暴露顶层 {@code selectColumns} + raw {@code onCondition}），
 * 需要同步升级 {@link CteUnit} 和 {@link JoinSpec} 的契约，并重写 ComposePlanner 的 M6 join 路径。</p>
 *
 * @author Foggy Framework
 * @since 8.2.0
 */
public class CteComposer {

    private CteComposer() {
    }

    /**
     * 组合两个 CTE 单元
     *
     * @param left     左侧 CTE 单元
     * @param right    右侧 CTE 单元
     * @param joinSpec JOIN 规格
     * @param useCte   是否使用 CTE 语法（false 时回退为子查询）
     * @return 组合后的 SQL 和参数
     */
    public static ComposedSql compose(CteUnit left, CteUnit right, JoinSpec joinSpec, boolean useCte) {
        List<Object> allParams = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        if (useCte) {
            buildCte(sb, allParams, left, right, joinSpec);
        } else {
            buildSubquery(sb, allParams, left, right, joinSpec);
        }

        return new ComposedSql(sb.toString(), allParams);
    }

    /**
     * 组合多个 CTE 单元（链式 JOIN）。
     *
     * <p><b>API drift vs. Python（参见类级 Javadoc）</b>：本签名缺少 Python 顶层
     * {@code select_columns} 参数。"外层选列"在这里分布到每个 {@link CteUnit#getSelectColumns()}
     * 里，由 {@link #buildMultiCte} / {@link #buildMultiSubquery} 按 unit 逐一展开。
     * 想"先 join 再统一裁列"的调用方必须走
     * {@link com.foggyframework.dataset.db.model.engine.compose.compilation.ComposePlanner}
     * 的 M6 SQL assembly 路径，本方法不会满足那种诉求。</p>
     *
     * @param units     CTE 单元列表
     * @param joinSpecs JOIN 规格列表（长度 = units.size() - 1，仅支持单列等值 JOIN）
     * @param useCte    是否使用 CTE 语法
     * @return 组合后的 SQL 和参数
     */
    public static ComposedSql compose(List<CteUnit> units, List<JoinSpec> joinSpecs, boolean useCte) {
        return compose(units, joinSpecs, useCte, null);
    }

    /**
     * 组合多个 CTE 单元（链式 JOIN），并允许调用方显式覆盖外层 SELECT 投影列。
     *
     * <p>{@code selectColumns} 非空时优先使用它渲染外层 SELECT；
     * 为空时保持历史行为，按各 {@link CteUnit#getSelectColumns()} 渲染，
     * 缺省则回退到 {@code alias.*}。</p>
     *
     * @param units         CTE 单元列表
     * @param joinSpecs     JOIN 规格列表（长度 = units.size() - 1）
     * @param useCte        是否使用 CTE 语法
     * @param selectColumns 外层 SELECT 的显式投影列（可选）
     * @return 组合后的 SQL 和参数
     */
    public static ComposedSql compose(List<CteUnit> units, List<JoinSpec> joinSpecs,
                                      boolean useCte, List<String> selectColumns) {
        if (units.size() < 2) {
            throw new IllegalArgumentException("At least 2 CTE units required for composition");
        }
        if (joinSpecs.size() != units.size() - 1) {
            throw new IllegalArgumentException("joinSpecs.size() must equal units.size() - 1");
        }

        List<Object> allParams = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        if (useCte) {
            buildMultiCte(sb, allParams, units, joinSpecs, selectColumns);
        } else {
            buildMultiSubquery(sb, allParams, units, joinSpecs, selectColumns);
        }

        return new ComposedSql(sb.toString(), allParams);
    }

    // ---- CTE 模式 ----

    private static void buildCte(StringBuilder sb, List<Object> allParams,
                                  CteUnit left, CteUnit right, JoinSpec joinSpec) {
        // WITH clause
        sb.append("WITH ").append(left.getAlias()).append(" AS (").append(left.getSql()).append(")");
        addParams(allParams, left.getParams());

        sb.append(", ").append(right.getAlias()).append(" AS (").append(right.getSql()).append(") ");
        addParams(allParams, right.getParams());

        // SELECT clause
        appendSelectClause(sb, left, right, joinSpec);

        // FROM + JOIN
        sb.append(" FROM ").append(left.getAlias());
        appendJoinClause(sb, left.getAlias(), right.getAlias(), joinSpec);
    }

    private static void buildMultiCte(StringBuilder sb, List<Object> allParams,
                                       List<CteUnit> units, List<JoinSpec> joinSpecs,
                                       List<String> selectColumns) {
        // WITH clause
        sb.append("WITH ");
        for (int i = 0; i < units.size(); i++) {
            if (i > 0) sb.append(", ");
            CteUnit unit = units.get(i);
            sb.append(unit.getAlias()).append(" AS (").append(unit.getSql()).append(")");
            addParams(allParams, unit.getParams());
        }
        sb.append(" ");

        appendMultiSelectClause(sb, units, selectColumns);

        // FROM + chain JOINs
        sb.append(" FROM ").append(units.get(0).getAlias());
        for (int i = 0; i < joinSpecs.size(); i++) {
            JoinSpec spec = joinSpecs.get(i);
            String leftAlias = units.get(i).getAlias();
            String rightAlias = units.get(i + 1).getAlias();
            appendJoinClause(sb, leftAlias, rightAlias, spec);
        }
    }

    // ---- 子查询模式（MySQL 5.7 回退）----

    private static void buildSubquery(StringBuilder sb, List<Object> allParams,
                                       CteUnit left, CteUnit right, JoinSpec joinSpec) {
        // SELECT clause
        appendSelectClause(sb, left, right, joinSpec);

        // FROM (subquery) AS alias
        sb.append(" FROM (").append(left.getSql()).append(") AS ").append(left.getAlias());
        addParams(allParams, left.getParams());

        // JOIN (subquery) AS alias ON ...
        sb.append(" ").append(joinSpec.getJoinType()).append(" JOIN ");
        sb.append("(").append(right.getSql()).append(") AS ").append(right.getAlias());
        addParams(allParams, right.getParams());

        sb.append(" ON ").append(left.getAlias()).append(".").append(joinSpec.getLeftKey());
        sb.append(" = ").append(right.getAlias()).append(".").append(joinSpec.getRightKey());
    }

    private static void buildMultiSubquery(StringBuilder sb, List<Object> allParams,
                                            List<CteUnit> units, List<JoinSpec> joinSpecs,
                                            List<String> selectColumns) {
        // SELECT clause
        appendMultiSelectClause(sb, units, selectColumns);

        // FROM first subquery
        CteUnit firstUnit = units.get(0);
        sb.append(" FROM (").append(firstUnit.getSql()).append(") AS ").append(firstUnit.getAlias());
        addParams(allParams, firstUnit.getParams());

        // Chain JOINs
        for (int i = 0; i < joinSpecs.size(); i++) {
            JoinSpec spec = joinSpecs.get(i);
            CteUnit rightUnit = units.get(i + 1);
            String leftAlias = units.get(i).getAlias();

            sb.append(" ").append(spec.getJoinType()).append(" JOIN ");
            sb.append("(").append(rightUnit.getSql()).append(") AS ").append(rightUnit.getAlias());
            addParams(allParams, rightUnit.getParams());

            sb.append(" ON ").append(leftAlias).append(".").append(spec.getLeftKey());
            sb.append(" = ").append(rightUnit.getAlias()).append(".").append(spec.getRightKey());
        }
    }

    // ---- Helper methods ----

    private static void appendSelectClause(StringBuilder sb, CteUnit left, CteUnit right, JoinSpec joinSpec) {
        sb.append("SELECT ");
        boolean first = true;

        if (joinSpec.getLeftColumns() != null) {
            for (String col : joinSpec.getLeftColumns()) {
                if (!first) sb.append(", ");
                sb.append(left.getAlias()).append(".").append(col);
                first = false;
            }
        } else {
            sb.append(left.getAlias()).append(".*");
            first = false;
        }

        if (joinSpec.getRightColumns() != null) {
            for (String col : joinSpec.getRightColumns()) {
                if (!first) sb.append(", ");
                sb.append(right.getAlias()).append(".").append(col);
                first = false;
            }
        } else {
            if (!first) sb.append(", ");
            sb.append(right.getAlias()).append(".*");
        }
    }

    private static void appendMultiSelectClause(StringBuilder sb, List<CteUnit> units, List<String> selectColumns) {
        sb.append("SELECT ");
        if (selectColumns != null && !selectColumns.isEmpty()) {
            for (int i = 0; i < selectColumns.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(selectColumns.get(i));
            }
            return;
        }

        boolean first = true;
        for (CteUnit unit : units) {
            if (unit.getSelectColumns() != null) {
                for (String col : unit.getSelectColumns()) {
                    if (!first) sb.append(", ");
                    sb.append(unit.getAlias()).append(".").append(col);
                    first = false;
                }
            } else {
                if (!first) sb.append(", ");
                sb.append(unit.getAlias()).append(".*");
                first = false;
            }
        }
    }

    private static void appendJoinClause(StringBuilder sb, String leftAlias, String rightAlias, JoinSpec joinSpec) {
        sb.append(" ").append(joinSpec.getJoinType()).append(" JOIN ").append(rightAlias);
        sb.append(" ON ").append(leftAlias).append(".").append(joinSpec.getLeftKey());
        sb.append(" = ").append(rightAlias).append(".").append(joinSpec.getRightKey());
    }

    private static void addParams(List<Object> target, List<Object> source) {
        if (source != null) {
            target.addAll(source);
        }
    }
}
