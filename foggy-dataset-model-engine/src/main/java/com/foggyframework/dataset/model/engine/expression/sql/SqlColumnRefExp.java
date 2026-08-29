package com.foggyframework.dataset.model.engine.expression.sql;

import com.foggyframework.dataset.model.engine.expression.SqlExpContext;
import com.foggyframework.dataset.model.engine.expression.SqlFragment;
import com.foggyframework.dataset.model.engine.expression.BoundSqlExpression;
import com.foggyframework.dataset.model.engine.expression.TotalExpressionNode;
import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.DbQueryColumn;
import com.foggyframework.dataset.model.spi.support.CalculatedDbColumn;
import com.foggyframework.fsscript.exp.AbstractExp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

/**
 * SQL 列引用表达式
 * <p>
 * 表示对模型中列的引用。执行时从上下文解析列，并调用 getDeclare() 获取 SQL 声明。
 * 支持:
 * <ul>
 *     <li>普通列: table.column</li>
 *     <li>带 formulaDef 的列: 返回 formulaDef 定义的 SQL</li>
 *     <li>维度列: dimension$caption, dimension$id</li>
 *     <li>计算字段引用: 返回计算字段的 SQL 表达式</li>
 * </ul>
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
@Slf4j
public class SqlColumnRefExp extends AbstractExp<String> {

    private static final long serialVersionUID = 1L;

    public SqlColumnRefExp(String columnName) {
        super(columnName);
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {
        if (log.isDebugEnabled()) {
            log.debug("SqlColumnRefExp.evalValue: columnName='{}'", value);
        }

        // 从上下文获取 SQL 表达式上下文
        SqlExpContext ctx = (SqlExpContext) evaluator.getVar(SqlExpContext.CONTEXT_KEY);
        if (ctx == null) {
            throw new IllegalStateException("SqlExpContext not found in evaluator. " +
                    "Make sure to set '" + SqlExpContext.CONTEXT_KEY + "' before evaluating SQL expressions.");
        }

        // 解析列名 → JdbcQueryColumn
        DbQueryColumn column = ctx.resolveColumn(value);
        if (ctx.isInsideAggregateFunctionArgument()
                && column instanceof CalculatedDbColumn calcColumn
                && calcColumn.hasAggregate()) {
            DbQueryColumn modelColumn = ctx.tryResolveModelColumn(value);
            if (modelColumn != null) {
                column = modelColumn;
                if (log.isDebugEnabled()) {
                    log.debug("SqlColumnRefExp.evalValue: aggregate argument '{}' resolved to model column to avoid nested aggregate alias",
                            value);
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("SqlColumnRefExp.evalValue: resolved column type={}", column.getClass().getName());
        }

        // 通过 getDeclare() 获取 SQL 片段
        // - 普通列: "t0.order_date"
        // - formulaDef 列: 由 builder 函数生成的原生 SQL（方言相关，如 MySQL: "t0.data ->> '$.key'", PG: "t0.data ->> 'key'"）
        // - 计算字段: "(t0.totaldue - d1.salesquota)"
        String sqlDeclare = column.getDeclare(ctx.getAppCtx(), ctx.getAlias(column), ctx.getDialect());
        if (log.isDebugEnabled()) {
            log.debug("SqlColumnRefExp.evalValue: sqlDeclare='{}'", sqlDeclare);
        }

        String aggregateSourceDeclare = sqlDeclare;
        DbAggregation groupedAggregation = resolveGroupedAggregation(ctx, column);
        if (groupedAggregation != null && !ctx.isInsideAggregateFunctionArgument()) {
            sqlDeclare = buildAggregateSql(ctx, groupedAggregation, sqlDeclare);
            if (log.isDebugEnabled()) {
                log.debug("SqlColumnRefExp.evalValue: aggregated measure ref='{}'", sqlDeclare);
            }
        }

        // 构建 SqlFragment
        SqlFragment fragment = SqlFragment.ofColumn(column, sqlDeclare);
        if (groupedAggregation != null && !ctx.isInsideAggregateFunctionArgument()) {
            fragment.setHasAggregate(true);
            fragment.setAggregationType(groupedAggregation.name());
            fragment.setTotalExpression(TotalExpressionNode.aggregate(
                    groupedAggregation.name(), BoundSqlExpression.of(aggregateSourceDeclare)));
        }

        // 如果是计算字段，需要合并其依赖的列和聚合状态
        if (column instanceof CalculatedDbColumn) {
            CalculatedDbColumn calcColumn = (CalculatedDbColumn) column;
            fragment.getReferencedColumns().addAll(calcColumn.getReferencedColumns());
            // 传播聚合状态，避免外层表达式再次包裹聚合函数导致嵌套聚合
            if (calcColumn.hasAggregate()) {
                fragment.setHasAggregate(true);
            }
            if (fragment.getAggregationType() == null) {
                fragment.setAggregationType(calcColumn.getAggregationType());
            }
        }

        return fragment;
    }

    private DbAggregation resolveGroupedAggregation(SqlExpContext ctx, DbQueryColumn column) {
        if (!ctx.isAggregateMeasureReferences()) {
            return null;
        }
        if (column instanceof CalculatedDbColumn calcColumn) {
            if (calcColumn.hasWindow()) {
                return null;
            }
            if (calcColumn.hasAggregate()) {
                return null;
            }
            return parseAggregation(calcColumn.getAggregationType());
        }
        if (column.isMeasure()
                && column.getAggregation() != null
                && column.getAggregation() != DbAggregation.NONE) {
            return column.getAggregation();
        }
        return null;
    }

    private DbAggregation parseAggregation(String aggregationType) {
        if (aggregationType == null || aggregationType.isBlank()) {
            return null;
        }
        try {
            DbAggregation aggregation = DbAggregation.valueOf(aggregationType.toUpperCase(Locale.ROOT));
            return aggregation == DbAggregation.NONE || aggregation == DbAggregation.WINDOW ? null : aggregation;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String buildAggregateSql(SqlExpContext ctx, DbAggregation aggregation, String sqlDeclare) {
        return switch (aggregation) {
            case COUNT -> "COUNT(*)";
            case COUNT_DISTINCT -> "COUNT(DISTINCT " + sqlDeclare + ")";
            case GROUP_CONCAT -> {
                if (ctx != null && ctx.getDialect() != null) {
                    yield ctx.getDialect().buildStringAggFunction(sqlDeclare, ",");
                }
                yield "GROUP_CONCAT(" + sqlDeclare + " SEPARATOR ',')";
            }
            case PK -> "MAX(" + sqlDeclare + ")";
            case STDDEV_POP, STDDEV_SAMP, VAR_POP, VAR_SAMP -> {
                if (ctx != null && ctx.getDialect() != null) {
                    yield ctx.getDialect().buildStatFunction(aggregation.name(), sqlDeclare);
                }
                yield aggregation.name() + "(" + sqlDeclare + ")";
            }
            case CUSTOM, WINDOW, NONE -> sqlDeclare;
            default -> aggregation.name() + "(" + sqlDeclare + ")";
        };
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return SqlFragment.class;
    }

    @Override
    public String toString() {
        return "[SqlColumnRef:" + value + "]";
    }

    /**
     * 获取列名
     */
    public String getColumnName() {
        return value;
    }
}
