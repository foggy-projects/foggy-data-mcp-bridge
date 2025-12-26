package com.foggyframework.dataset.jdbc.model.engine.expression.mongo;

import com.foggyframework.dataset.jdbc.model.engine.expression.MongoAllowedFunctions;
import com.foggyframework.dataset.jdbc.model.engine.expression.MongoFragment;
import com.foggyframework.fsscript.exp.AbstractExp;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * MongoDB 二元运算表达式
 * <p>
 * 处理算术、比较、逻辑运算，生成对应的 MongoDB 聚合表达式。
 * </p>
 *
 * <h3>运算符映射</h3>
 * <ul>
 *     <li>算术: + -> $add, - -> $subtract, * -> $multiply, / -> $divide, % -> $mod</li>
 *     <li>比较: == -> $eq, != -> $ne, > -> $gt, >= -> $gte, < -> $lt, <= -> $lte</li>
 *     <li>逻辑: && -> $and, || -> $or</li>
 * </ul>
 *
 * @author Foggy
 * @since 1.0
 */
@Slf4j
@Getter
public class MongoBinaryExp extends AbstractExp<Exp[]> {

    private static final long serialVersionUID = 1L;

    private final Exp left;
    private final String operator;
    private final Exp right;

    public MongoBinaryExp(Exp left, String operator, Exp right) {
        super(new Exp[]{left, right});
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    @Override
    public Object evalValue(ExpEvaluator context) {
        // 执行左右操作数
        Object leftResult = left.evalValue(context);
        Object rightResult = right.evalValue(context);

        MongoFragment leftFragment = toMongoFragment(leftResult);
        MongoFragment rightFragment = toMongoFragment(rightResult);

        // 根据运算符类型生成 MongoDB 表达式
        if (MongoAllowedFunctions.isArithmeticOperator(operator)) {
            String mongoOp = MongoAllowedFunctions.getArithmeticOperator(operator);
            return MongoFragment.binary(leftFragment, mongoOp, rightFragment);
        }

        if (MongoAllowedFunctions.isComparisonOperator(operator)) {
            String mongoOp = MongoAllowedFunctions.getComparisonOperator(operator);
            return MongoFragment.comparison(leftFragment, mongoOp, rightFragment);
        }

        if (MongoAllowedFunctions.isLogicalOperator(operator)) {
            String mongoOp = MongoAllowedFunctions.getLogicalOperator(operator);
            return MongoFragment.logical(mongoOp, java.util.Arrays.asList(leftFragment, rightFragment));
        }

        throw new RuntimeException("Unsupported operator: " + operator);
    }

    private MongoFragment toMongoFragment(Object result) {
        if (result instanceof MongoFragment) {
            return (MongoFragment) result;
        }
        // 字面量
        return MongoFragment.ofLiteral(result);
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return MongoFragment.class;
    }

    @Override
    public String toString() {
        return "[MongoBinary: " + left + " " + operator + " " + right + "]";
    }
}
