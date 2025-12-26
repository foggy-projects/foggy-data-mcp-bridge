package com.foggyframework.dataset.db.model.engine.expression.mongo;

import com.foggyframework.dataset.db.model.engine.expression.MongoAllowedFunctions;
import com.foggyframework.dataset.db.model.engine.expression.MongoFragment;
import com.foggyframework.fsscript.exp.AbstractExp;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB 函数表达式
 * <p>
 * 将 SQL 风格的函数调用转换为 MongoDB 聚合操作符。
 * </p>
 *
 * <h3>函数映射示例</h3>
 * <ul>
 *     <li>YEAR(date) -> { $year: "$date" }</li>
 *     <li>ABS(value) -> { $abs: "$value" }</li>
 *     <li>CONCAT(a, b) -> { $concat: ["$a", "$b"] }</li>
 * </ul>
 *
 * @author Foggy
 * @since 1.0
 */
@Slf4j
@Getter
public class MongoFunctionExp extends AbstractExp<List<Exp>> {

    private static final long serialVersionUID = 1L;

    private final String funcName;
    private final List<Exp> args;

    public MongoFunctionExp(String funcName, List<Exp> args) {
        super(args);
        this.funcName = funcName;
        this.args = args;
    }

    @Override
    public Object evalValue(ExpEvaluator context) {
        // 获取 MongoDB 操作符
        String mongoOp = MongoAllowedFunctions.getMongoOperator(funcName);
        if (mongoOp == null) {
            throw new SecurityException("Function not supported in MongoDB: " + funcName);
        }

        // 执行所有参数
        List<MongoFragment> argFragments = new ArrayList<>();
        for (Exp arg : args) {
            Object result = arg.evalValue(context);
            argFragments.add(toMongoFragment(result));
        }

        // 特殊处理某些函数
        String upperFuncName = funcName.toUpperCase();

        // COALESCE/IFNULL: 使用 $ifNull
        if ("COALESCE".equals(upperFuncName) || "IFNULL".equals(upperFuncName) || "NVL".equals(upperFuncName)) {
            if (argFragments.size() >= 2) {
                return MongoFragment.ifNull(argFragments.get(0), argFragments.get(1));
            }
        }

        // IF: 使用 $cond
        if ("IF".equals(upperFuncName) && argFragments.size() >= 3) {
            return MongoFragment.cond(argFragments.get(0), argFragments.get(1), argFragments.get(2));
        }

        // ROUND: MongoDB $round 需要两个参数 [value, place]
        if ("ROUND".equals(upperFuncName)) {
            if (argFragments.size() == 1) {
                // 没有精度参数，默认为 0
                argFragments.add(MongoFragment.ofLiteral(0));
            }
        }

        // SUBSTRING/SUBSTR: MongoDB $substrCP 需要 [string, start, length]
        // SQL SUBSTRING 索引从 1 开始，MongoDB 从 0 开始
        if ("SUBSTRING".equals(upperFuncName) || "SUBSTR".equals(upperFuncName)) {
            if (argFragments.size() >= 2) {
                // 调整起始索引（SQL 从 1 开始，MongoDB 从 0 开始）
                MongoFragment startFragment = argFragments.get(1);
                MongoFragment adjustedStart = MongoFragment.binary(
                        startFragment,
                        "$subtract",
                        MongoFragment.ofLiteral(1)
                );
                argFragments.set(1, adjustedStart);
            }
        }

        // 生成函数调用
        return MongoFragment.function(mongoOp, argFragments);
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
        return "[MongoFunction: " + funcName + "(" + args + ")]";
    }
}
