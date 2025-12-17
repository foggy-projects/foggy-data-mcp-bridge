package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;

/**
 * 可选链属性访问表达式 (?.)
 * <p>
 * 当左值为 null 或 undefined 时，返回 null 而不是抛出异常。
 * 符合 JavaScript 可选链操作符规范。
 * </p>
 *
 * <h3>示例</h3>
 * <pre>
 * let obj = null;
 * obj?.name;     // 返回 null，不抛错
 * obj?.method(); // 返回 null，不抛错
 * </pre>
 *
 * @author Foggy
 * @since 1.0
 */
public class OptionalPropertyExp extends PropertyExp {

    public OptionalPropertyExp(Exp exp, String name) {
        super(exp, name);
    }

    @Override
    protected boolean checkLeft() {
        return false;
    }

    @Override
    public String toString() {
        return exp.toString() + "?." + value;
    }
}
