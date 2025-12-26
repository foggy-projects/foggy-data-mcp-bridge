package com.foggyframework.dataset.db.model.engine.expression.sql;

import com.foggyframework.dataset.db.model.engine.expression.SqlFragment;
import com.foggyframework.fsscript.exp.AbstractExp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

/**
 * SQL 字面量表达式
 * <p>
 * 表示数字、字符串等字面量值，执行时直接返回 SQL 字面量。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
public class SqlLiteralExp extends AbstractExp<String> {

    private static final long serialVersionUID = 1L;

    public SqlLiteralExp(String literal) {
        super(literal);
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {
        return SqlFragment.ofLiteral(value);
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return SqlFragment.class;
    }

    @Override
    public String toString() {
        return "[SqlLiteral:" + value + "]";
    }
}
