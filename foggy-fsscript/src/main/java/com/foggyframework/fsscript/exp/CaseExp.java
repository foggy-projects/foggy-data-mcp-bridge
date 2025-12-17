package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.fun.Equal;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

public class CaseExp implements Exp {
    Exp con;
    Exp body;

    public CaseExp(Exp con, Exp body) {
        super();
        this.con = con;
        this.body = body;
    }

    public boolean match(ExpEvaluator ee, Object v) {
            Object conValue = con.evalResult(ee);
            return Equal.eq(conValue, v);

    }

    @Override
    public Object evalValue(ExpEvaluator ee)
            {
        if(body == null) {
            return null;
        }
        return body.evalResult(ee);
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return Object.class;
    }

}
