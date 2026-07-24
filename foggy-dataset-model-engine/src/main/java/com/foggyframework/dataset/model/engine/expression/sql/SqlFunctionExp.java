package com.foggyframework.dataset.model.engine.expression.sql;

import com.foggyframework.dataset.model.engine.expression.AllowedFunctions;
import com.foggyframework.dataset.model.engine.expression.SqlExpContext;
import com.foggyframework.dataset.model.engine.expression.SqlFragment;
import com.foggyframework.dataset.model.spi.DbColumnType;
import com.foggyframework.fsscript.exp.AbstractExp;
import com.foggyframework.fsscript.exp.EmptyExp;
import com.foggyframework.fsscript.exp.NullExp;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * SQL 函数调用表达式
 * <p>
 * 表示 SQL 函数调用：YEAR(), MONTH(), ABS(), ROUND(), CONCAT() 等。
 * 根据数据库方言转换函数名。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
public class SqlFunctionExp extends AbstractExp<String> {

    private static final long serialVersionUID = 1L;

    private final String functionName;
    private final List<Exp> args;

    public SqlFunctionExp(String functionName, List<Exp> args) {
        super(functionName);
        this.functionName = functionName;
        this.args = args;
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {
        SqlExpContext ctx = (SqlExpContext) evaluator.getVar(SqlExpContext.CONTEXT_KEY);
        String upper = functionName.toUpperCase();

        // 执行所有参数（过滤掉 EmptyExp，它代表零参数函数调用如 ROW_NUMBER()）
        // NullExp 需要显式保留为 SQL NULL，不能在这里被丢弃，否则 IF(cond, x, null) 会只剩两个参数。
        List<SqlFragment> argFragments = new ArrayList<>(args.size());
        boolean aggregateFunction = AllowedFunctions.isAggregateFunction(upper);
        if (aggregateFunction && ctx != null) {
            ctx.enterAggregateFunctionArgument();
        }
        try {
            for (Exp arg : args) {
                if (arg instanceof EmptyExp) {
                    continue;
                }
                if (arg instanceof NullExp) {
                    argFragments.add(SqlFragment.ofLiteral("NULL"));
                    continue;
                }
                SqlFragment fragment = (SqlFragment) arg.evalResult(evaluator);
                if (fragment != null) {
                    argFragments.add(fragment);
                }
            }
        } finally {
            if (aggregateFunction && ctx != null) {
                ctx.exitAggregateFunctionArgument();
            }
        }

        // IF/IIF(cond, thenExpr, elseExpr) 在 JDBC 侧降级为标准 CASE WHEN，便于复用现有 DSL 语法支持条件聚合
        if ("IF".equals(upper) || "IIF".equals(upper)) {
            if (argFragments.size() != 3) {
                throw new IllegalArgumentException("IF/IIF function requires exactly 3 arguments");
            }
            SqlFragment condition = argFragments.get(0);
            SqlFragment thenExpr = argFragments.get(1);
            SqlFragment elseExpr = argFragments.get(2);
            String caseSql = "CASE WHEN " + condition.getSql()
                    + " THEN " + thenExpr.getSql()
                    + " ELSE " + elseExpr.getSql()
                    + " END";
            return SqlFragment.customFunction(caseSql, "IF", argFragments);
        }

        // v1.4 Step 3.4 · Spec v1 方言分派函数 · 路由到 DialectAwareFunctionExp
        // B-5 核实结论 B：date_diff / date_add / now 走 ctx.getDialect() 分派
        if ("DATE_DIFF".equals(upper)) {
            return DialectAwareFunctionExp.renderDateDiff(ctx, argFragments);
        }
        if ("HOURS_BETWEEN".equals(upper)) {
            return DialectAwareFunctionExp.renderHoursBetween(ctx, argFragments);
        }
        if ("DATE_ADD".equals(upper)) {
            return DialectAwareFunctionExp.renderDateAdd(ctx, argFragments);
        }
        if ("NOW".equals(upper)) {
            return DialectAwareFunctionExp.renderNow(ctx, argFragments);
        }

        // v1.4 Step 3.2 · Spec v1 MUST 函数（ANSI 方言无关 lowering）
        // R-2 括号规则：二元/一元运算外包一层括号，便于嵌套组合
        if ("IS_NULL".equals(upper)) {
            if (argFragments.size() != 1) {
                throw new IllegalArgumentException("is_null function requires exactly 1 argument, got " + argFragments.size());
            }
            String sql = "(" + argFragments.get(0).getSql() + " IS NULL)";
            return SqlFragment.customFunction(sql, "IS_NULL", argFragments);
        }
        if ("IS_NOT_NULL".equals(upper)) {
            if (argFragments.size() != 1) {
                throw new IllegalArgumentException("is_not_null function requires exactly 1 argument, got " + argFragments.size());
            }
            String sql = "(" + argFragments.get(0).getSql() + " IS NOT NULL)";
            return SqlFragment.customFunction(sql, "IS_NOT_NULL", argFragments);
        }
        if ("BETWEEN".equals(upper)) {
            if (argFragments.size() != 3) {
                throw new IllegalArgumentException("between function requires exactly 3 arguments (value, lo, hi), got " + argFragments.size());
            }
            String sql = "(" + argFragments.get(0).getSql()
                    + " BETWEEN " + argFragments.get(1).getSql()
                    + " AND " + argFragments.get(2).getSql() + ")";
            return SqlFragment.customFunction(sql, "BETWEEN", argFragments);
        }

        // COUNT(DISTINCT) 特殊处理
        if ("COUNTD".equals(upper) || "COUNT_DISTINCT".equals(upper)) {
            String argsStr = argFragments.stream().map(SqlFragment::getSql).collect(Collectors.joining(", "));
            SqlFragment f = new SqlFragment();
            f.setSql("COUNT(DISTINCT " + argsStr + ")");
            f.setHasAggregate(true);
            f.setAggregationType("COUNT_DISTINCT");
            f.setInferredType(DbColumnType.INTEGER);
            argFragments.forEach(arg -> f.getReferencedColumns().addAll(arg.getReferencedColumns()));
            return f;
        }

        // 统计函数方言适配
        if ("STDDEV_POP".equals(upper) || "STDDEV_SAMP".equals(upper)
                || "VAR_POP".equals(upper) || "VAR_SAMP".equals(upper)) {
            String dialectFunc = translateStatFunction(ctx, upper);
            String argsStr = argFragments.stream().map(SqlFragment::getSql).collect(Collectors.joining(", "));
            SqlFragment f = new SqlFragment();
            f.setSql(dialectFunc + "(" + argsStr + ")");
            f.setHasAggregate(true);
            f.setAggregationType(upper);
            f.setInferredType(DbColumnType.NUMBER);
            argFragments.forEach(arg -> f.getReferencedColumns().addAll(arg.getReferencedColumns()));
            return f;
        }

        // 窗口函数标记
        if (AllowedFunctions.isWindowFunction(upper)) {
            SqlFragment f = SqlFragment.function(upper, argFragments);
            f.setHasWindow(true);
            f.setHasAggregate(false);
            return f;
        }

        // 尝试方言特定的函数调用构建（处理需要语法重构的函数，如 YEAR→EXTRACT）
        if (ctx != null && ctx.getDialect() != null) {
            java.util.List<String> argsSql = argFragments.stream()
                    .map(SqlFragment::getSql)
                    .collect(Collectors.toList());
            String dialectSql = ctx.getDialect().buildFunctionCall(functionName, argsSql);
            if (dialectSql != null) {
                return SqlFragment.customFunction(dialectSql, functionName, argFragments);
            }
        }

        // 根据方言转换函数名
        String dialectFuncName = translateFunction(ctx, functionName);

        return SqlFragment.function(dialectFuncName, argFragments);
    }

    /**
     * 根据方言转换函数名
     */
    private String translateFunction(SqlExpContext ctx, String funcName) {
        if (ctx == null) {
            return funcName;
        }
        return ctx.translateFunction(funcName);
    }

    /**
     * 统计函数方言适配
     */
    private String translateStatFunction(SqlExpContext ctx, String funcName) {
        if (ctx == null || ctx.getDialect() == null) {
            return funcName;
        }
        return ctx.getDialect().buildStatFunction(funcName);
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return SqlFragment.class;
    }

    public String getFunctionName() {
        return functionName;
    }

    public List<Exp> getArgs() {
        return args;
    }

    @Override
    public String toString() {
        String argsStr = args.stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));
        return "[SqlFunction:" + functionName + "(" + argsStr + ")]";
    }
}
