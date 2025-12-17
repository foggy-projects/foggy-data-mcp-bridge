package com.foggyframework.fsscript.parser.spi;

import org.springframework.context.ApplicationContext;

import java.util.Map;

public interface Fsscript {

    Object eval(ExpEvaluator ee);

    default Object evalResult(ExpEvaluator ee) {
        Object t = eval(ee);
        if (t instanceof Exp.ReturnExpObject) {
            return ((Exp.ReturnExpObject) t).value;
        }
        return t;
    }

//    ExpEvaluator eval(ApplicationContext appCtx);

    default ExpEvaluator eval(ApplicationContext appCtx) {
        ExpEvaluator ee = newInstance(appCtx);
        eval(ee);
        return ee;
    }

    default <T> T evalResult(ApplicationContext appCtx, Map<String, Object> args) {
        ExpEvaluator ee = newInstance(appCtx);
        if (args != null) {
            ee.setMap2Var(args);
        }

        return (T) evalResult(ee);
    }

    FsscriptClosureDefinition getFsscriptClosureDefinition();

    String getPath();

    ExpEvaluator newInstance(ApplicationContext appCtx);

    boolean hasImport(Fsscript fscript);

}
