package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

/**
 * 带默认值的参数表达式
 * 用于支持函数参数默认值语法，例如: function foo(options = {})
 *
 * @author Foggy
 */
public class DefaultArgExp implements Exp, NamedExp {

    private final String paramName;
    private final Exp defaultValue;

    public DefaultArgExp(String paramName, Exp defaultValue) {
        this.paramName = paramName;
        this.defaultValue = defaultValue;
    }

    public String getParamName() {
        return paramName;
    }

    public Exp getDefaultValue() {
        return defaultValue;
    }

    @Override
    public String getValue() {
        return paramName;
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {
        // 计算默认值
        return defaultValue.evalValue(evaluator);
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return defaultValue.getReturnType(evaluator);
    }

    @Override
    public String toString() {
        return "[DefaultArg: " + paramName + " = " + defaultValue + "]";
    }
}
