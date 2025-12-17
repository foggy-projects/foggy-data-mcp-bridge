package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.fun.Iif;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.io.Serializable;

public class ForExp implements Exp, Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 984313084333757885L;

    final Exp defExp;
    final Exp booleanExp;
    final Exp addExp;
    final Exp forBodyExp;

    public ForExp(Exp defExp, Exp booleanExp, Exp addExp, Exp forBodyExp) {
        super();
        this.defExp = defExp;
        this.booleanExp = booleanExp;
        this.addExp = addExp;
        this.forBodyExp = forBodyExp;
    }

    @Override
    public Object evalValue(ExpEvaluator ee) {
        try {
            ee.pushNewFoggyClosure();
            if (defExp != null) {
                defExp.evalValue(ee);
            }
            Object obj;
            if (addExp != null) {

                for (; Iif.check(booleanExp.evalValue(ee)); addExp.evalValue(ee)) {
                    obj = forBodyExp.evalValue(ee);
                    if (obj == BreakExp.BREAK) {
                        break;
                    }
                    if (obj instanceof ReturnExpObject) {
                        return obj;
                    }
                }

            } else {
                for (; Iif.check(booleanExp.evalValue(ee)); ) {
                    obj = forBodyExp.evalValue(ee);
                    if (obj == BreakExp.BREAK) {
                        break;
                    }
                    if (obj instanceof ReturnExpObject) {
                        return obj;
                    }
                }

            }
        } finally {
            ee.popFsscriptClosure();
        }

        return null;
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return Object.class;
    }
}
