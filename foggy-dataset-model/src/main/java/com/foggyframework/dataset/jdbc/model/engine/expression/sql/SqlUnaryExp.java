package com.foggyframework.dataset.jdbc.model.engine.expression.sql;

import com.foggyframework.dataset.jdbc.model.engine.expression.AllowedFunctions;
import com.foggyframework.dataset.jdbc.model.engine.expression.SqlFragment;
import com.foggyframework.fsscript.exp.AbstractExp;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

/**
 * SQL 一元运算表达式
 * <p>
 * 表示一元运算：-, NOT
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
public class SqlUnaryExp extends AbstractExp<String> {

    private static final long serialVersionUID = 1L;

    private final String operator;
    private final Exp operand;

    public SqlUnaryExp(String operator, Exp operand) {
        super(operator);
        this.operator = operator;
        this.operand = operand;
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {
        SqlFragment operandFrag = (SqlFragment) operand.evalResult(evaluator);

        // 转换运算符
        String sqlOperator = AllowedFunctions.toSqlOperator(operator);

        return SqlFragment.unary(sqlOperator, operandFrag);
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return SqlFragment.class;
    }

    public String getOperator() {
        return operator;
    }

    public Exp getOperand() {
        return operand;
    }

    @Override
    public String toString() {
        return "[SqlUnary:" + operator + " " + operand + "]";
    }
}
