package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.List;

public class CImportExp implements ImportExp {
    String file;

    public CImportExp(String file) {
        this.file = file;
    }

    @Override
    public Object evalValue(ExpEvaluator ee) {
//
//        Exp exp = FscriptImportMgr.getInstance().getImportExp(file, true);
//        return exp.evalResult(ee);
        throw new UnsupportedOperationException();
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return null;
    }

    @Override
    public void setName(String value) {

    }

    @Override
    public void setNames(List<String> names) {

    }

    @Override
    public void setExtNames(List<Object> names) {

    }
}
