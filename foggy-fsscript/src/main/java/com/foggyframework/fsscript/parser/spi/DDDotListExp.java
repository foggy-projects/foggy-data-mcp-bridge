package com.foggyframework.fsscript.parser.spi;

import com.foggyframework.fsscript.exp.AbstractExp;

import java.util.Collection;
import java.util.List;

public class DDDotListExp extends AbstractExp<Exp> {

    public DDDotListExp(Exp value) {
        super(value);
    }

    @Override
    public Object evalValue(ExpEvaluator ee) {
        return value.evalValue(ee);
    }

    public Object apply2List(List ll, ExpEvaluator ee) {
        Object v = value.evalValue(ee);
        if (v instanceof Collection) {
            ll.addAll((Collection) v);
        } else {
            ll.add(v);
        }
        return v;
    }
    public Object apply2List2(List ll, ExpEvaluator ee) {
        Object v = value.evalResult(ee);
        if (v instanceof Collection) {
            ll.addAll((Collection) v);
        } else {
            ll.add(v);
        }
        return v;
    }
    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return null;
    }
}
