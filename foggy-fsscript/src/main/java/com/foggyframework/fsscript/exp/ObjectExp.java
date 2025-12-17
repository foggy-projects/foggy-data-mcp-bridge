package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

public  class ObjectExp<T> extends AbstractExp<T> {

    /**
     *
     */
    private static final long serialVersionUID = -1994237875179049704L;

    public ObjectExp(T value) {
        super(value);
    }

    @Override
    public T evalValue(ExpEvaluator evaluator) {
        return value;
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return value == null ? Object.class : value.getClass();
    }

    public void setValue(T v) {
        this.value = v;
    }

}
