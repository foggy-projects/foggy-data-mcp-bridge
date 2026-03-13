package com.foggyframework.dataset.db.model.engine.expression.sql;

import com.foggyframework.dataset.db.model.engine.expression.AllowedFunctions;
import com.foggyframework.dataset.db.model.engine.expression.SqlExpContext;
import com.foggyframework.dataset.db.model.engine.expression.SqlFragment;
import com.foggyframework.dataset.db.model.spi.DbColumnType;
import com.foggyframework.fsscript.exp.AbstractExp;
import com.foggyframework.fsscript.exp.EmptyExp;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.List;
import java.util.Objects;
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

        // 执行所有参数（过滤掉 EmptyExp，它代表零参数函数调用如 ROW_NUMBER()）
        List<SqlFragment> argFragments = args.stream()
                .filter(arg -> !(arg instanceof EmptyExp))
                .map(arg -> (SqlFragment) arg.evalResult(evaluator))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        String upper = functionName.toUpperCase();

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
