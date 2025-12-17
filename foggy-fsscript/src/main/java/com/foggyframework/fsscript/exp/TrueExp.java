package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.io.Serializable;

public class TrueExp implements Exp, Serializable {
    public static final Exp TRUE_EXP = new TrueExp();
    /**
     *
     */
    private static final long serialVersionUID = -8850871551217386079L;

    @Override
    public Object evalValue(ExpEvaluator ee)
            {
        return Boolean.TRUE;
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return Boolean.class;
    }

    @Override
    public String toString() {
        return "true";
    };
}