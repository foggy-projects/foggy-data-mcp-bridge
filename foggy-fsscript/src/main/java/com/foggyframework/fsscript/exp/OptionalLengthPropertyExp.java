package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;

/**
 * 可选链 length 属性访问表达式 (?.length)
 * <p>
 * 当左值为 null 时返回 null，否则返回 length。
 * </p>
 */
public class OptionalLengthPropertyExp extends LengthPropertyExp {

    public OptionalLengthPropertyExp(Exp exp, String name) {
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
