package com.foggyframework.fsscript.fun;

import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import org.springframework.util.Assert;

/**
 * SQL 风格 {@code value not in (v1, v2, ...)} 成员测试取反。
 *
 * <p>由 grammar rule {@code term3:x NOT IN term2:y} 生成
 * {@code UnresolvedFunCall("NOT_IN", [x, y])}；运行时直接复用 {@link IN#containsMember} 并取反。
 *
 * <p>注意：不承担 {@code (item, index) in collection} 这种迭代语义（那条路径只出现在 IN
 * 表达式中，NOT IN 在 fsscript 里没有对应的迭代场景）。
 */
public class NOT_IN implements FunDef {

    @Override
    public Object execute(ExpEvaluator ee, Exp[] args) {
        Assert.isTrue(args.length == 2, "not in 表达式的参数必须是 2 个");
        return !IN.containsMember(ee, args[0], args[1]);
    }

    @Override
    public String getName() {
        return "NOT_IN";
    }
}
