package com.foggyframework.fsscript.fun.log;

import com.foggyframework.fsscript.fun.AbstractFunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.FsscriptClosure;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Debug extends AbstractFunDef {

    @Override
    public Object execute(ExpEvaluator ee, Exp[] args) {
        StringBuilder sb = new StringBuilder();
        FsscriptClosure fsscriptClosure = ee.getCurrentFsscriptClosure();
        if (fsscriptClosure != null && fsscriptClosure.getBeanDefinitionSpace() != null) {
            sb.append(fsscriptClosure.getBeanDefinitionSpace().getPath());
        }
        for (Exp arg : args) {
            Object obj = arg.evalResult(ee);
            if (obj != null) {
                sb.append(obj);
            }
        }

        log.debug(sb.toString());
        return sb.toString();
    }

    @Override
    public String getName() {
        return "debug";
    }
}
