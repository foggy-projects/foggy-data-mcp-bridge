package com.foggyframework.dataset.jdbc.model.engine.expression.mongo;

import com.foggyframework.dataset.jdbc.model.engine.expression.MongoFragment;
import com.foggyframework.dataset.jdbc.model.spi.JdbcColumnType;
import com.foggyframework.fsscript.exp.AbstractExp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.Getter;

/**
 * MongoDB 字面量表达式
 * <p>
 * 表示数字、字符串等字面量值。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
@Getter
public class MongoLiteralExp extends AbstractExp<Object> {

    private static final long serialVersionUID = 1L;

    private final Object literalValue;
    private final JdbcColumnType type;

    public MongoLiteralExp(Object value) {
        super(value);
        this.literalValue = value;
        this.type = inferType(value);
    }

    public MongoLiteralExp(Object value, JdbcColumnType type) {
        super(value);
        this.literalValue = value;
        this.type = type;
    }

    @Override
    public Object evalValue(ExpEvaluator context) {
        return MongoFragment.ofLiteral(literalValue, type);
    }

    private JdbcColumnType inferType(Object value) {
        if (value == null) {
            return JdbcColumnType.UNKNOWN;
        }
        if (value instanceof String) {
            return JdbcColumnType.TEXT;
        }
        if (value instanceof Boolean) {
            return JdbcColumnType.BOOL;
        }
        if (value instanceof Double || value instanceof Float) {
            return JdbcColumnType.NUMBER;
        }
        if (value instanceof Number) {
            return JdbcColumnType.INTEGER;
        }
        return JdbcColumnType.UNKNOWN;
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return MongoFragment.class;
    }

    @Override
    public String toString() {
        return "[MongoLiteral: " + literalValue + "]";
    }
}
