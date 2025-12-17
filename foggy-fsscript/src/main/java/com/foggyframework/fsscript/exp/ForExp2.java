package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.io.Serializable;

public class ForExp2 implements Exp, Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 984313084333757885L;

    final String leftId;
    final Exp rightExp;
    final Exp forBodyExp;

    public ForExp2(String leftId, Exp rightExp, Exp forBodyExp) {
        super();
        this.leftId = leftId;
        this.rightExp = rightExp;
        this.forBodyExp = forBodyExp;
    }

    @Override
    public Object evalValue(ExpEvaluator ee)
            {

        Object obj = rightExp.evalValue(ee);
        if (obj instanceof Object[]) {
            Object tmp = ee.getVar(leftId);
            try {
                for (Object x : (Object[]) obj) {
                    ee.setVar(leftId, x);
                    forBodyExp.evalValue(ee);
                }
            } finally {
                ee.setVar(leftId, tmp);
            }
        }
//        else if (obj instanceof ListResultSet) {
//            Object tmp = ee.getVar(leftId);
//            try {
//                ListResultSet ll = (ListResultSet) obj;
//                while (ll.next()) {
//                    ee.setVar(leftId, ll.getRecord());
//                    forBodyExp.evalValue(ee);
//                }
//            } catch (SQLException e) {
//                throw ErrorUtils.toRuntimeException(e);
//            } finally {
//                ee.setVar(leftId, tmp);
//            }
//        }
        else {

            Iterable<?> right = (Iterable<?>) obj;
            if (right == null) {
                return null;
            }
            Object tmp = ee.getVar(leftId);
            try {
                for (Object x : right) {
                    ee.setVar(leftId, x);
                    forBodyExp.evalValue(ee);
                }
            } finally {
                ee.setVar(leftId, tmp);
            }
        }
        return null;
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return Object.class;
    }
}
