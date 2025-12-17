package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

public class EmptyExp extends AbstractExp<Object> {

    public static final EmptyExp EMPTY = new EmptyExp();

    /**
     *
     */
    private static final long serialVersionUID = 6867251793054472374L;

    public EmptyExp() {
        super(null);
    }

    @Override
    public String evalValue(final ExpEvaluator context) {
        return null;
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return Object.class;
    }

    @Override
    public String toString() {
        return "";
    }
}
