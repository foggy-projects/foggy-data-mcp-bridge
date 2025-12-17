package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.io.Serializable;

public class FalseExp implements Exp, Serializable {
    public static final Exp FALSE_EXP = new FalseExp();
    /**
     *
     */
    private static final long serialVersionUID = -4844496669942805877L;

    @Override
    public Object evalValue(ExpEvaluator ee)
            {
        return Boolean.FALSE;
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return Boolean.class;
    }

    @Override
    public String toString() {
        return "false";
    };
}
