package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.io.Serializable;

public class ContinueExp implements Exp, Serializable {
    public static final Exp CONTINUE = new ContinueExp();

    @Override
    public Object evalValue(ExpEvaluator ee)
            {
        return CONTINUE;
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return ContinueExp.class;
    }

    @Override
    public String toString() {
        return "continue";
    }

}
