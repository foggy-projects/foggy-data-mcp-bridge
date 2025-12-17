package com.foggyframework.fsscript.support;

import com.foggyframework.core.utils.ErrorUtils;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.exp.ImportFsscriptExp;
import com.foggyframework.fsscript.exp.NCountExp;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
@Slf4j
public class FsscriptImpl implements Fsscript {
    FsscriptClosureDefinition fsscriptClosureDefinition;

    Exp exp;

    public FsscriptClosureDefinition getFsscriptClosureDefinition() {
        return fsscriptClosureDefinition;
    }

    @Override
    public String getPath() {
        return fsscriptClosureDefinition.getFsscriptClosureDefinitionSpace().getPath();
    }

    @Override
    public ExpEvaluator newInstance(ApplicationContext appCtx) {
        return DefaultExpEvaluator.newInstance(appCtx, fsscriptClosureDefinition.newFoggyClosure());
    }

    @Override
    public boolean hasImport(Fsscript fscript) {
        if (exp instanceof ImportFsscriptExp) {

            return ((ImportFsscriptExp) exp).hasImport(fscript, true, true);
        } else if (exp instanceof NCountExp) {
            for (Exp exp1 : ((NCountExp) exp).value) {
                if (exp1 instanceof ImportFsscriptExp) {
                    if (((ImportFsscriptExp) exp1).hasImport(fscript, true, true)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public FsscriptImpl(FsscriptClosureDefinition fsscriptClosureDefinition, Exp exp) {
        this.fsscriptClosureDefinition = fsscriptClosureDefinition;
        this.exp = exp;
    }

    @Override
    public Object eval(ExpEvaluator ee) {
        try {
            return exp.evalValue(ee);
        }catch (Throwable t){
            log.error("执行fsscript异常: "+fsscriptClosureDefinition);
            throw ErrorUtils.toRuntimeException(t);
        }
    }



    @Override
    public String toString() {
        return "FsscriptImpl{" +
                "fsscriptClosureDefinition=" + fsscriptClosureDefinition +
                '}';
    }
}
