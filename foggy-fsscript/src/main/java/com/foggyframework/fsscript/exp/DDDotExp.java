package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.Collection;

@Deprecated
public class DDDotExp extends AbstractExp<Exp> {

    public DDDotExp(Exp value) {
        super(value);
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {
        return value.evalValue(evaluator);
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return Collection.class;
    }


}
