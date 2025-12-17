package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

public class RequestParameterExp extends AbstractExp<String> {

    /**
     *
     */
    private static final long serialVersionUID = 3191294764502553925L;

    public RequestParameterExp(String value) {
        super("$"+value);
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {
        Object obj = evaluator.getVar(value);
//throw new UnsupportedOperationException();
        return obj;
    }

    @Override
    public Class<String> getReturnType(ExpEvaluator evaluator) {

        return String.class;
    }

    @Override
    public String toString() {
        return "[" + super.toString() + "]";
    }

}