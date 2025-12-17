package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

public class ArgmentExp extends AbstractExp<String>{
    public ArgmentExp(String value) {
        super(value);
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {
        /**
         * 待改动
         */
//        if (evaluator instanceof FoggyObjectExpEvaluator) {
//            FoggyClosure fc = evaluator.getContext(FoggyClosure.class);
//            return fc.getArg(value, null);
//        } else {
            Object obj = evaluator.getVar(value);
//				if (obj instanceof ReturnExpObject) {
//					return ((ReturnExpObject) obj).value;
//				}
//				return obj;

            return unWarpResult(obj);
//        }
    }

    @Override
    public Class<Object> getReturnType(ExpEvaluator evaluator) {

        return Object.class;
    }

    @Override
    public String toString() {
        return "[$.:" + super.toString() + "]";
    }
}
