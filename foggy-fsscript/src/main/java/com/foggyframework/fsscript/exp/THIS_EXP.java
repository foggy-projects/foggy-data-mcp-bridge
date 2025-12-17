package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.io.Serializable;

public class THIS_EXP implements Exp, Serializable {
    public static final Exp THIS_EXP = new THIS_EXP();
    /**
     *
     */
    private static final long serialVersionUID = -4225554314982636721L;

    @Override
    public Object evalValue(ExpEvaluator ee) {
        throw new UnsupportedOperationException();
//        return ee.getContext();
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return Object.class;
    }

    @Override
    public String toString() {
        return "this";
    };
}