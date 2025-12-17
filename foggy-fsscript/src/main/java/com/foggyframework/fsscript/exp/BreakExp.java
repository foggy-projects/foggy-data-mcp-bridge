package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.io.Serializable;

public class BreakExp implements Exp, Serializable {
    public static final Exp BREAK = new BreakExp();
    /**
     *
     */
    private static final long serialVersionUID = -8850871551217386079L;

    @Override
    public Object evalValue(ExpEvaluator ee)
            {
        return BREAK;
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return BreakExp.class;
    }

    @Override
    public String toString() {
        return "break";
    }

    ;
}
