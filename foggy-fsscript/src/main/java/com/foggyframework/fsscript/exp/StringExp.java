package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

public class StringExp extends AbstractExp<String> {
    public static final StringExp EMPTY_STRING = new StringExp("");
    /**
     *
     */
    private static final long serialVersionUID = 828510156345913667L;

    public StringExp(final String str) {
        super(str);
    }

    @Override
    public String evalValue(final ExpEvaluator context) {
        return value;
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return String.class;
    }

    @Override
    public String toString() {
        return value;
    }

}
