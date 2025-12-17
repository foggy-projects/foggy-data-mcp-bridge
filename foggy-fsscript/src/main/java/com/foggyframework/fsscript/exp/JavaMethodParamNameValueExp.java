package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.closure.MethodFilterFsscriptClosure;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import org.springframework.util.Assert;

public class JavaMethodParamNameValueExp extends AbstractExp<String> {

    public JavaMethodParamNameValueExp(String value) {
        super(value);
    }

    @Override
    public Object evalValue(ExpEvaluator ee) {
        Object obj = null;// = evaluator.getVar(value);

        MethodFilterFsscriptClosure m = ee.getContext(MethodFilterFsscriptClosure.class);
        Assert.notNull(m,"");

        obj = m.getArgByName(value);

        return obj;
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        // TODO Auto-generated method stub
        return null;
    }

}
