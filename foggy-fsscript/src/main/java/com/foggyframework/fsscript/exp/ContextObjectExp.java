package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

public class ContextObjectExp extends AbstractExp<String> {

    /**
     *
     */
    private static final long serialVersionUID = -1137668323401695302L;

    public ContextObjectExp(String name) {
        super(name);
    }

    @Override
    public Object evalValue(ExpEvaluator ee) {
        Object bean = ee.getApplicationContext().getBean(value);
        return bean;
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return Object.class;
    }

}
