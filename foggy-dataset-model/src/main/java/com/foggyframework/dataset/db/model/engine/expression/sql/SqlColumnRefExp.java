package com.foggyframework.dataset.db.model.engine.expression.sql;

import com.foggyframework.dataset.db.model.engine.expression.SqlExpContext;
import com.foggyframework.dataset.db.model.engine.expression.SqlFragment;
import com.foggyframework.dataset.db.model.spi.DbQueryColumn;
import com.foggyframework.dataset.db.model.spi.support.CalculatedJdbcColumn;
import com.foggyframework.fsscript.exp.AbstractExp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.extern.slf4j.Slf4j;

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
        if (log.isDebugEnabled()) {
            log.debug("SqlColumnRefExp.evalValue: resolved column type={}", column.getClass().getName());
        }

        // 通过 getDeclare() 获取 SQL 片段
        // - 普通列: "t0.order_date"
        // - formulaDef 列: "t0.send_addr_info ->> '$.send_company_name'"
        // - 计算字段: "(t0.totaldue - d1.salesquota)"
        String sqlDeclare = column.getDeclare(ctx.getAppCtx(), ctx.getAlias(column));
        if (log.isDebugEnabled()) {
            log.debug("SqlColumnRefExp.evalValue: sqlDeclare='{}'", sqlDeclare);
        }

        // 构建 SqlFragment
        SqlFragment fragment = SqlFragment.ofColumn(column, sqlDeclare);

        // 如果是计算字段，需要合并其依赖的列
        if (column instanceof CalculatedJdbcColumn) {
            CalculatedJdbcColumn calcColumn = (CalculatedJdbcColumn) column;
            fragment.getReferencedColumns().addAll(calcColumn.getReferencedColumns());
        }

        return fragment;
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
