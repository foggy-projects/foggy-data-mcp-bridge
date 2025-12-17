package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

public class DollarExp extends AbstractExp<Exp> {

    /**
     *
     */
    private static final long serialVersionUID = 5478646961023760733L;

    public DollarExp(final Exp value) {
        super(value);
    }

    @Override
    public Object evalValue(final ExpEvaluator ee)
            {

        return value == null ? null : unWarpResult(value.evalValue(ee));
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return value.getReturnType(evaluator);
    }

    @Override
    public String toString() {
        return "[Dollar:" + value.toString() + "]";
    }

}
