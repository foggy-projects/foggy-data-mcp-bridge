package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

public class IdExp extends AbstractExp<String> implements NamedExp {

    public IdExp(String value) {
        super(value);
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {

        Object x = evaluator.getVar(value);
        return (x == EmptyExp.EMPTY || x == NullExp.NULL) ? null : unWarpResult(x);
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        if (value.equalsIgnoreCase("true")) {
            return Boolean.class;
        }
        if (value.equalsIgnoreCase("false")) {
            return Boolean.class;
        }
//        if (value.equals("this") && evaluator != null) {
//            return evaluator.getContext().getClass();
//        }
        return Object.class;
    }

    @Override
    public String toString() {
        return "[ID:" + super.toString() + "]";
    }

}
