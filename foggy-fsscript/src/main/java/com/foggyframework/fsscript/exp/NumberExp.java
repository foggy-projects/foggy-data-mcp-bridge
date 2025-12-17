package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

public class NumberExp extends AbstractExp<Number> {

    /**
     *
     */
    private static final long serialVersionUID = -3402605020135770112L;

    public NumberExp(final Number n) {
        super(n);
    }

    public NumberExp(final String str) {
        this(Double.parseDouble(str));
    }

    @Override
    public Number evalValue(final ExpEvaluator context) {
        return value;
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return value == null ? Number.class : value.getClass();
    }

}
