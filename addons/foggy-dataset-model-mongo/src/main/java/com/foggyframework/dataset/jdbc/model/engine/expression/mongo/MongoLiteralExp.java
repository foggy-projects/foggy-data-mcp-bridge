package com.foggyframework.dataset.jdbc.model.engine.expression.mongo;

import com.foggyframework.dataset.jdbc.model.engine.expression.MongoFragment;
import com.foggyframework.dataset.jdbc.model.spi.DbColumnType;
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
    private final DbColumnType type;

    public MongoLiteralExp(Object value) {
        super(value);
        this.literalValue = value;
        this.type = inferType(value);
    }

    public MongoLiteralExp(Object value, DbColumnType type) {
        super(value);
        this.literalValue = value;
        this.type = type;
    }

    @Override
    public Object evalValue(ExpEvaluator context) {
        return MongoFragment.ofLiteral(literalValue, type);
    }

    private DbColumnType inferType(Object value) {
        if (value == null) {
            return DbColumnType.UNKNOWN;
        }
        if (value instanceof String) {
            return DbColumnType.TEXT;
        }
        if (value instanceof Boolean) {
            return DbColumnType.BOOL;
        }
        if (value instanceof Double || value instanceof Float) {
            return DbColumnType.NUMBER;
        }
        if (value instanceof Number) {
            return DbColumnType.INTEGER;
        }
        return DbColumnType.UNKNOWN;
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
