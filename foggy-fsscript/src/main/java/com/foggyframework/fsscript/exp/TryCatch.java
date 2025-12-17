package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.io.Serializable;

public class TryCatch implements Exp, Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 3632556069843334310L;
    Exp tryExp;
    Exp finalyExp;
    Exp catchExp;
    String catchArgName;

    public TryCatch(Exp tryExp, Exp finalyExp, Exp catchExp, String catchArgName) {
        super();
        this.tryExp = tryExp;
        this.catchExp = catchExp;
        this.finalyExp = finalyExp;
        this.catchArgName = catchArgName;
    }

    @Override
    public Object evalValue(ExpEvaluator ee) {
        try {
            return tryExp.evalValue(ee);
        } catch (Throwable t) {
            if (catchExp != null) {
                Object tmp = ee.getVar(catchArgName);
                try {
                    ee.setVar(catchArgName,
                            t instanceof ThrowException ? ((ThrowException) t).getErrorObject() : t);
                    return catchExp.evalValue(ee);
                } finally {
                    ee.setVar(catchArgName, tmp);
                }
            }
        } finally {
            if (finalyExp != null) {
                finalyExp.evalValue(ee);
            }
        }
        return null;
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator ee) {
        return tryExp.getReturnType(ee);
    }

    @Override
    public String toString() {
        return "[try {\n" + tryExp + "}catch{\n" + catchExp + "}finally{\n" + finalyExp + "}\n";
    }

}
