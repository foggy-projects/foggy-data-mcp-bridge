package com.foggyframework.dataset.db.model.engine.expression;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.WindowOrderDef;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.model.spi.support.CalculatedDbColumn;
import com.foggyframework.fsscript.parser.spi.Exp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 计算字段处理器
 * <p>
 * 用于 JDBC/SQL 类型的 QueryModel，将计算字段表达式编译为 SQL 片段。
 * 共享逻辑（拓扑排序、表达式编译/求值）委托给 {@link CalculatedFieldService}，
 * 本类仅负责窗口函数包装等 SQL 特有能力。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
@Slf4j
public class SqlCalculatedFieldProcessor implements CalculatedFieldProcessor {

    private static final Pattern AGGREGATE_FUNCTION_CALL = Pattern.compile(
            "\\b(sum|avg|count|countd|count_distinct|min|max|stddev_pop|stddev_samp|var_pop|var_samp)\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    private final JdbcQueryModel queryModel;
    private final FDialect dialect;
    private SqlExpContext context;
    private CalculateQueryContext calculateQueryContext;
    private boolean groupedQuery;

    public SqlCalculatedFieldProcessor(JdbcQueryModel queryModel, FDialect dialect) {
        this.queryModel = queryModel;
        this.dialect = dialect;
    }

    @Override
    public List<CalculatedDbColumn> processCalculatedFields(
            List<CalculatedFieldDef> calculatedFields,
            ApplicationContext appCtx) {
        if (calculatedFields == null || calculatedFields.isEmpty()) {
            return new ArrayList<>();
        }

        // 创建 SQL 表达式上下文
        this.context = new SqlExpContext(queryModel, dialect, appCtx);
        this.context.setCalculateQueryContext(calculateQueryContext);

        // 按依赖关系排序（委托 CalculatedFieldService）
        List<CalculatedFieldDef> sortedFields = CalculatedFieldService.sortByDependencies(calculatedFields);

        List<CalculatedDbColumn> result = new ArrayList<>(sortedFields.size());

        for (CalculatedFieldDef fieldDef : sortedFields) {
            CalculatedDbColumn column = doProcessCalculatedField(fieldDef, appCtx);
            result.add(column);
        }

        return result;
    }

    @Override
    public CalculatedDbColumn processCalculatedField(
            CalculatedFieldDef fieldDef,
            ApplicationContext appCtx) {
        // 如果上下文不存在，创建一个新的
        if (this.context == null) {
            this.context = new SqlExpContext(queryModel, dialect, appCtx);
            this.context.setCalculateQueryContext(calculateQueryContext);
        }
        return doProcessCalculatedField(fieldDef, appCtx);
    }

    /**
     * 处理单个计算字段的内部实现
     * <p>
     * 相比 {@link CalculatedFieldService#processCalculatedField}，额外支持窗口函数（PARTITION BY / ORDER BY / frame）。
     * </p>
     */
    private CalculatedDbColumn doProcessCalculatedField(
            CalculatedFieldDef fieldDef,
            ApplicationContext appCtx) {
        // 验证必填字段
        RX.hasText(fieldDef.getName(), "计算字段名称不能为空");
        RX.hasText(fieldDef.getExpression(), "计算字段表达式不能为空: " + fieldDef.getName());

        // 检查名称是否已存在
        if (context.hasColumn(fieldDef.getName())) {
            throw RX.throwAUserTip("计算字段名称已存在: " + fieldDef.getName());
        }

        try {
            // 1. 获取或编译表达式 AST（委托 CalculatedFieldService）
            Exp compiledExp = fieldDef.getCompiledExp();
            if (compiledExp == null) {
                compiledExp = CalculatedFieldService.compileExpression(fieldDef.getExpression());
                fieldDef.setCompiledExp(compiledExp);
            } else if (log.isDebugEnabled()) {
                log.debug("Reusing pre-compiled AST for field: {}", fieldDef.getName());
            }
            CalculateExpressionAnalyzer.validate(compiledExp);

            // 2. 执行表达式得到 SQL 片段（委托 CalculatedFieldService）
            boolean aggregateMeasureFormula = isGroupedMeasureFormula(fieldDef);
            context.setAggregateMeasureReferences(aggregateMeasureFormula);
            SqlFragment sqlFragment;
            try {
                sqlFragment = CalculatedFieldService.evaluateExpression(compiledExp, context, appCtx);
            } finally {
                context.setAggregateMeasureReferences(false);
            }

            // 2.1 如果有 partitionBy/windowOrderBy，包装窗口子句
            boolean wrappedWithWindowClause = false;
            if (fieldDef.getPartitionBy() != null || fieldDef.getWindowOrderBy() != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Window clause for {}: partitionBy={}, windowOrderBy={}, windowFrame={}",
                            fieldDef.getName(), fieldDef.getPartitionBy(),
                            fieldDef.getWindowOrderBy(), fieldDef.getWindowFrame());
                }
                sqlFragment = wrapWithWindowClause(sqlFragment, fieldDef, context, appCtx);
                wrappedWithWindowClause = true;
            }

            // 2.2 如果推断了聚合类型，传递到 SqlFragment
            if (fieldDef.getAgg() != null && sqlFragment.getAggregationType() == null && !sqlFragment.isHasWindow()) {
                sqlFragment.setAggregationType(fieldDef.getAgg().toUpperCase());
                if (log.isDebugEnabled()) {
                    log.debug("Applied inferred aggregation from CalculatedFieldDef: {} -> agg={}",
                            fieldDef.getName(), fieldDef.getAgg());
                }
            }
            sqlFragment = CalculatedFieldService.applyEmptyDefault(sqlFragment, fieldDef);

            // 3. 创建 CalculatedJdbcColumn
            String caption = StringUtils.isNotEmpty(fieldDef.getCaption()) ? fieldDef.getCaption() : fieldDef.getName();
            CalculatedDbColumn column = new CalculatedDbColumn(
                    fieldDef.getName(),
                    caption,
                    sqlFragment,
                    fieldDef.getDescription()
            );

            // Mark the column as needing CTE wrapping if it went through wrapWithWindowClause
            if (wrappedWithWindowClause) {
                column.setNeedsCteWrapping(true);
            }

            // 4. 注册到上下文（支持后续字段引用）
            context.registerCalculatedColumn(fieldDef.getName(), column);

            if (log.isDebugEnabled()) {
                log.debug("Processed calculated field: {} = {} (hasAggregate={})",
                        fieldDef.getName(), sqlFragment.getSql(), sqlFragment.isHasAggregate());
            }

            return column;

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = "编译计算字段表达式失败 [" + fieldDef.getName() + "]: " + e.getMessage();
            throw RX.throwAUserTip(errorMsg, errorMsg, null, e);
        }
    }

    /**
     * 包装窗口函数 OVER 子句
     * <p>
     * 将已编译的 SQL 表达式追加 OVER (PARTITION BY ... ORDER BY ... frame) 子句。
     * partitionBy/windowOrderBy 中的列名会被解析为实际的 SQL 引用。
     * </p>
     *
     * @param baseSql  基础 SQL 片段（如 "RANK()" 或 "AVG(m1.amount)"）
     * @param fieldDef 计算字段定义（含 partitionBy/windowOrderBy/windowFrame）
     * @param context  SQL 表达式上下文
     * @param appCtx   Spring ApplicationContext
     * @return 包含完整 OVER 子句的 SqlFragment
     */
    private SqlFragment wrapWithWindowClause(SqlFragment baseSql, CalculatedFieldDef fieldDef,
                                              SqlExpContext context, ApplicationContext appCtx) {
        StringBuilder overClause = new StringBuilder();
        Set<DbQueryColumn> refs = new LinkedHashSet<>(baseSql.getReferencedColumns());

        // PARTITION BY
        if (fieldDef.getPartitionBy() != null && !fieldDef.getPartitionBy().isEmpty()) {
            overClause.append("PARTITION BY ");
            List<String> partitionSqls = new ArrayList<>();
            for (String colName : fieldDef.getPartitionBy()) {
                DbQueryColumn col = context.resolveColumn(colName);
                String partAlias = context.getAlias(col);
                String colSql = resolveColumnSql(col, partAlias, appCtx, true);
                partitionSqls.add(colSql);
                refs.add(col);
            }
            overClause.append(String.join(", ", partitionSqls));
        }

        // ORDER BY
        if (fieldDef.getWindowOrderBy() != null && !fieldDef.getWindowOrderBy().isEmpty()) {
            if (overClause.length() > 0) {
                overClause.append(" ");
            }
            overClause.append("ORDER BY ");
            List<String> orderSqls = new ArrayList<>();
            for (WindowOrderDef orderDef : fieldDef.getWindowOrderBy()) {
                String orderField = orderDef.getField();
                // In CTE Wrapping architecture, Window CFs are evaluated in Stage 2.
                // We resolve the column and reference it by its alias (which will be projected by Stage 1).
                DbQueryColumn col = context.tryResolveColumn(orderField);
                if (col == null) {
                    throw RX.throwAUserTip("COMPOSE_WINDOW_ORDER_BY_UNRESOLVABLE: calculatedFields["
                            + fieldDef.getName() + "].windowOrderBy field '"
                            + orderField + "' cannot be resolved. Use a valid base model measure, dimension, or prior calc-field name.");
                }
                String alias = context.getAlias(col);
                String colSql = resolveColumnSql(col, alias, appCtx, true);
                orderSqls.add(colSql + " " + orderDef.getNormalizedDir());
                refs.add(col);
            }
            overClause.append(String.join(", ", orderSqls));
        }

        // Window frame
        if (StringUtils.isNotEmpty(fieldDef.getWindowFrame())) {
            if (overClause.length() > 0) {
                overClause.append(" ");
            }
            overClause.append(fieldDef.getWindowFrame());
        }

        // ── CTE Wrapping: rewrite base expression to use alias-based column references ──
        // In two-stage CTE architecture, the base expression (before OVER) must reference
        // columns by their projected alias from Stage 1, not physical table expressions.
        // e.g., AVG(t1.sales_amount) → AVG("salesAmount")
        String rewrittenBaseSql = rewriteBaseExpressionForCte(baseSql, context, appCtx);

        return SqlFragment.windowFunction(
                rewrittenBaseSql,
                overClause.toString(),
                refs,
                baseSql.getInferredType()
        );
    }

    /**
     * 解析列的 SQL 表达式
     */
    private String resolveColumnSql(DbQueryColumn col, String alias, ApplicationContext appCtx) {
        return resolveColumnSql(col, alias, appCtx, false);
    }

    /**
     * 解析列的 SQL 表达式
     * <p>
     * 在 CTE Wrapping 架构中，如果当前正在生成 Stage 2（外层）的窗口函数，
     * 需要设置 asAlias=true，这样它引用的内层列（如 SUM(...)）会直接引用其投影出的别名/名称，
     * 而不是展开为内层的物理表达式。
     * </p>
     */
    private String resolveColumnSql(DbQueryColumn col, String alias, ApplicationContext appCtx, boolean asAlias) {
        if (asAlias) {
            if (col instanceof CalculatedDbColumn) {
                return dialect.quoteIdentifier(((CalculatedDbColumn) col).getName());
            }
            if (col.getSelectColumn() != null) {
                return dialect.quoteIdentifier(col.getSelectColumn().getAlias());
            }
            return dialect.quoteIdentifier(col.getAlias());
        } else {
            if (col instanceof CalculatedDbColumn) {
                return ((CalculatedDbColumn) col).getDeclare();
            }
            if (col.getSelectColumn() != null) {
                return col.getSelectColumn().getDeclare(appCtx, alias);
            }
            return alias != null ? alias + "." + col.getAlias() : col.getAlias();
        }
    }

    /**
     * Rewrite the base SQL expression for CTE wrapping.
     * <p>
     * Replaces physical column references (e.g., {@code t1.sales_amount}) with
     * their projected alias (e.g., {@code "salesAmount"}) so the expression is
     * valid in Stage 2 of a CTE-wrapped query.
     * </p>
     * <p>
     * Uses the SqlFragment's referenced columns to build a mapping from
     * physical SQL declare → alias SQL, then performs string replacement.
     * Falls back to the original SQL if no replacements can be made.
     * </p>
     */
    private String rewriteBaseExpressionForCte(SqlFragment baseSql, SqlExpContext context, ApplicationContext appCtx) {
        String sql = baseSql.getSql();
        Set<DbQueryColumn> refs = baseSql.getReferencedColumns();
        if (refs == null || refs.isEmpty()) {
            return sql;
        }

        for (DbQueryColumn col : refs) {
            String tableAlias = context.getAlias(col);
            // Get the physical SQL declare (e.g., "t1.sales_amount")
            String physicalSql = resolveColumnSql(col, tableAlias, appCtx, false);
            // Get the alias-based SQL declare (e.g., "\"salesAmount\"")
            String aliasSql = resolveColumnSql(col, tableAlias, appCtx, true);

            if (physicalSql != null && aliasSql != null && !physicalSql.equals(aliasSql)) {
                // Use lookaround assertions to prevent substring matching.
                // Ensures we only match the physical SQL if it's not immediately preceded or followed by an identifier character [\\p{L}0-9_$].
                String regex = "(?<![\\\\p{L}0-9_$])" + Pattern.quote(physicalSql) + "(?![\\\\p{L}0-9_$])";
                sql = sql.replaceAll(regex, Matcher.quoteReplacement(aliasSql));
            }
        }

        if (log.isDebugEnabled() && !sql.equals(baseSql.getSql())) {
            log.debug("CTE rewrite: {} → {}", baseSql.getSql(), sql);
        }

        return sql;
    }

    /**
     * 获取 SQL 表达式上下文
     */
    public SqlExpContext getContext() {
        return context;
    }

    public void setCalculateQueryContext(CalculateQueryContext calculateQueryContext) {
        this.calculateQueryContext = calculateQueryContext;
        if (this.context != null) {
            this.context.setCalculateQueryContext(calculateQueryContext);
        }
    }

    public void setGroupedQuery(boolean groupedQuery) {
        this.groupedQuery = groupedQuery;
    }

    private boolean isGroupedMeasureFormula(CalculatedFieldDef fieldDef) {
        if (fieldDef == null || StringUtils.isEmpty(fieldDef.getExpression())) {
            return false;
        }
        if (fieldDef.getAgg() != null || fieldDef.getPartitionBy() != null || fieldDef.getWindowOrderBy() != null) {
            return false;
        }
        if (AGGREGATE_FUNCTION_CALL.matcher(fieldDef.getExpression()).find()) {
            return false;
        }
        for (String ref : CalculatedFieldService.extractColumnReferences(fieldDef.getExpression())) {
            DbQueryColumn column = context.tryResolveColumn(ref);
            if (column != null
                    && column.isMeasure()
                    && column.getAggregation() != null
                    && column.getAggregation() != DbAggregation.NONE) {
                return true;
            }
        }
        return false;
    }
}
