package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.io.Serializable;

public class EE_EXP implements Exp, Serializable {

    public static final Exp EE_EXP = new EE_EXP();
    @Override
    public Object evalValue(ExpEvaluator ee) {
        return ee;
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return ExpEvaluator.class;
    }

    @Override
    public String toString() {
        return "_ee";
    };
}
