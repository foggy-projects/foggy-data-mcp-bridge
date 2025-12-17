package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

public class NullExp extends AbstractExp<Object> {

    public static final NullExp NULL = new NullExp();


    public NullExp() {
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
