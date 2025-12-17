package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

public class ThrowExp extends AbstractExp<Exp> {

    /**
     *
     */
    private static final long serialVersionUID = 5250267884505466414L;

    public ThrowExp(Exp value) {
        super(value);
    }

    @Override
    public Object evalValue(ExpEvaluator ee) {
        Object v = value.evalValue(ee);
        throw new ThrowException(v);
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator ee) {
        return value.getReturnType(ee);
    }

}
