package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

public class ExpPlusExp extends AbstractExp<Object> {
    /**
     *
     */
    private static final long serialVersionUID = 3826021993794427252L;
    Exp exp1;
    Exp exp2;

    public ExpPlusExp(final Exp exp1, final Exp exp2) {
        super(null);
        this.exp1 = exp1;
        this.exp2 = exp2;
    }

    @Override
    public Object evalValue(final ExpEvaluator context)
            {
        Object v1 = exp1.evalResult(context);
        Object v2 = exp2.evalResult(context);
        if (v1 instanceof Number) {
            return ((Number) v1).doubleValue() + ((Number) v2).doubleValue();
        } else
            return (String) exp1.evalValue(context) + exp2.evalValue(context).toString();

    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return exp1.getReturnType(evaluator);
    }

}