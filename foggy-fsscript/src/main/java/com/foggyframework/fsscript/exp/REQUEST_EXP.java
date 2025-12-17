package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.io.Serializable;

public class REQUEST_EXP implements Exp, Serializable {
    public static final Exp REQUEST_EXP = new REQUEST_EXP();
    /**
     *
     */
    private static final long serialVersionUID = 7606348046188143330L;

    @Override
    public Object evalValue(ExpEvaluator ee) {
        throw  new UnsupportedOperationException();
//        return ee.getRequest();
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return Object.class;
    }

    @Override
    public String toString() {
        return "request";
    };
}
