package com.foggyframework.dataset.model.engine.expression.sql;

import com.foggyframework.dataset.model.engine.expression.AllowedFunctions;
import com.foggyframework.dataset.model.engine.expression.SqlFragment;
import com.foggyframework.fsscript.exp.AbstractExp;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.extern.slf4j.Slf4j;

/**
 * SQL 二元运算表达式
 * <p>
 * 表示二元运算：+, -, *, /, %, =, <>, >, <, >=, <=, AND, OR
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
@Slf4j
public class SqlBinaryExp extends AbstractExp<String> {

    private static final long serialVersionUID = 1L;

    private final Exp left;
    private final String operator;
    private final Exp right;

    public SqlBinaryExp(Exp left, String operator, Exp right) {
        super(operator);
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {
        if (log.isDebugEnabled()) {
            log.debug("SqlBinaryExp.evalValue: left.type={}, right.type={}, op={}",
                    left.getClass().getName(), right.getClass().getName(), operator);
        }

        Object leftResult = left.evalResult(evaluator);
        Object rightResult = right.evalResult(evaluator);

        if (log.isDebugEnabled()) {
            log.debug("SqlBinaryExp.evalValue: leftResult.type={}, rightResult.type={}",
                    leftResult != null ? leftResult.getClass().getName() : "null",
                    rightResult != null ? rightResult.getClass().getName() : "null");
        }

        SqlFragment leftFrag = (SqlFragment) leftResult;
        SqlFragment rightFrag = (SqlFragment) rightResult;

        // 转换运算符（FSScript → SQL）
        String sqlOperator = AllowedFunctions.toSqlOperator(operator);

        var context = (com.foggyframework.dataset.model.engine.expression.SqlExpContext)
                evaluator.getVar(com.foggyframework.dataset.model.engine.expression.SqlExpContext.CONTEXT_KEY);
        if ("/".equals(sqlOperator) && context != null && context.isResultStageReferences()) {
            // Preserve SQL NULL and make zero denominators portable; force noninteger ratios.
            var divisor = SqlFragment.customFunction("NULLIF(" + rightFrag.getSql() + ", 0)", "NULLIF", java.util.List.of(rightFrag));
            return SqlFragment.binary(SqlFragment.binary(SqlFragment.ofLiteral("1e0"), "*", leftFrag), sqlOperator, divisor);
        }
        return SqlFragment.binary(leftFrag, sqlOperator, rightFrag);
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return SqlFragment.class;
    }

    public Exp getLeft() {
        return left;
    }

    public String getOperator() {
        return operator;
    }

    public Exp getRight() {
        return right;
    }

    @Override
    public String toString() {
        return "[SqlBinary:" + left + " " + operator + " " + right + "]";
    }
}
