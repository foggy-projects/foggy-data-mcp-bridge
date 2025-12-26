package com.foggyframework.dataset.jdbc.model.engine.expression.mongo;

import com.foggyframework.dataset.jdbc.model.engine.expression.MongoFragment;
import com.foggyframework.fsscript.exp.AbstractExp;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.Getter;

/**
 * MongoDB 一元运算表达式
 * <p>
 * 处理负号和逻辑非运算。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
@Getter
public class MongoUnaryExp extends AbstractExp<Exp> {

    private static final long serialVersionUID = 1L;

    private final String operator;
    private final Exp operand;

    public MongoUnaryExp(String operator, Exp operand) {
        super(operand);
        this.operator = operator;
        this.operand = operand;
    }

    @Override
    public Object evalValue(ExpEvaluator context) {
        Object result = operand.evalValue(context);
        MongoFragment fragment = toMongoFragment(result);

        // 负号运算: 使用 $multiply: [expr, -1]
        if ("-".equals(operator)) {
            return MongoFragment.binary(
                    fragment,
                    "$multiply",
                    MongoFragment.ofLiteral(-1)
            );
        }

        // 逻辑非运算
        if ("!".equals(operator) || "NOT".equalsIgnoreCase(operator)) {
            return MongoFragment.unary("$not", fragment);
        }

        throw new RuntimeException("Unsupported unary operator: " + operator);
    }

    private MongoFragment toMongoFragment(Object result) {
        if (result instanceof MongoFragment) {
            return (MongoFragment) result;
        }
        return MongoFragment.ofLiteral(result);
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return MongoFragment.class;
    }

    @Override
    public String toString() {
        return "[MongoUnary: " + operator + operand + "]";
    }
}
