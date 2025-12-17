package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;


public class ReturnExp extends AbstractExp<Exp> {
    public static final ReturnExpObject EMPTY_RETURN_EXP_OBJECT = new ReturnExpObject(null);


    public ReturnExp(Exp value) {
        super(value);
    }

    @Override
    public Object evalValue(ExpEvaluator ee) {
        if (value != null) {
            Object v = value.evalValue(ee);
            if (v == null) {
                // return EMPTY_RETURN;
                return EMPTY_RETURN_EXP_OBJECT;
            }
            return new ReturnExpObject(v);
        } else {
            // return EMPTY_RETURN;
            return EMPTY_RETURN_EXP_OBJECT;
        }
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator ee) {
        return value.getReturnType(ee);
    }

    @Override
    public String toString() {
        return "return :" + (value == null ? "" : value.toString());
    }
}