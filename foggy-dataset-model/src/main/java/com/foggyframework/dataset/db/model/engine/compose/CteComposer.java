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
     * 组合多个 CTE 单元（链式 JOIN）
     *
     * @param units     CTE 单元列表
     * @param joinSpecs JOIN 规格列表（长度 = units.size() - 1）
     * @param useCte    是否使用 CTE 语法
     * @return 组合后的 SQL 和参数
     */
    public static ComposedSql compose(List<CteUnit> units, List<JoinSpec> joinSpecs, boolean useCte) {
        if (units.size() < 2) {
            throw new IllegalArgumentException("At least 2 CTE units required for composition");
        }
        if (joinSpecs.size() != units.size() - 1) {
            throw new IllegalArgumentException("joinSpecs.size() must equal units.size() - 1");
        }

        List<Object> allParams = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        if (useCte) {
            buildMultiCte(sb, allParams, units, joinSpecs);
        } else {
            buildMultiSubquery(sb, allParams, units, joinSpecs);
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
                                       List<CteUnit> units, List<JoinSpec> joinSpecs) {
        // WITH clause
        sb.append("WITH ");
        for (int i = 0; i < units.size(); i++) {
            if (i > 0) sb.append(", ");
            CteUnit unit = units.get(i);
            sb.append(unit.getAlias()).append(" AS (").append(unit.getSql()).append(")");
            addParams(allParams, unit.getParams());
        }
        sb.append(" ");

        // SELECT clause -- select all columns from all units
        sb.append("SELECT ");
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
                                            List<CteUnit> units, List<JoinSpec> joinSpecs) {
        // SELECT clause
        sb.append("SELECT ");
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
