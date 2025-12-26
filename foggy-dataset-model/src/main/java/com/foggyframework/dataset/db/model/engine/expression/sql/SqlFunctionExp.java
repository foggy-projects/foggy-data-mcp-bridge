package com.foggyframework.dataset.db.model.engine.expression.sql;

import com.foggyframework.dataset.db.model.engine.expression.SqlExpContext;
import com.foggyframework.dataset.db.model.engine.expression.SqlFragment;
import com.foggyframework.fsscript.exp.AbstractExp;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.List;
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

        // 执行所有参数
        List<SqlFragment> argFragments = args.stream()
                .map(arg -> (SqlFragment) arg.evalResult(evaluator))
                .collect(Collectors.toList());

        // 根据方言转换函数名
        String dialectFuncName = translateFunction(ctx, functionName);

        return SqlFragment.function(dialectFuncName, argFragments);
    }

    /**
     * 根据方言转换函数名
     */
    private String translateFunction(SqlExpContext ctx, String funcName) {
        if (ctx == null || ctx.getDialect() == null) {
            return funcName;
        }

        // 这里可以根据方言进行函数名转换
        // 例如：MySQL 的 IFNULL vs Oracle 的 NVL vs PostgreSQL 的 COALESCE
        // 目前简单返回原函数名，后续可扩展
        return funcName;
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
