/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.fun;


import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

/**
 * 0,null,""均表示 false
 *
 * @author Foggy
 */
public final class Iif implements FunDef {

    public static final Object NOT_MATCH = new Object() {

        @Override
        public String toString() {
            return "";
        }

    };

    public static boolean check(Object c) {
        return check(c, false);
    }

    public static boolean check(Object c, boolean nullValue) {
        // if (c == null || c == AbstractExp.EMPTY_RETURN) {
        // return false;
        // }
        if (c == null) {
            return nullValue;
        }
        boolean x = false;
        if (c instanceof Boolean) {
            x = (Boolean) c;
        } else if (c instanceof Integer) {
            if (((Integer) c).intValue() == 0) {
                x = false;
            } else {
                x = true;
            }
        } else if (c instanceof Long) {
            if (((Long) c).intValue() == 0) {
                x = false;
            } else {
                x = true;
            }
        } else if (c instanceof Double) {
            if (((Double) c) == 0.0) {
                x = false;
            } else {
                x = true;
            }
        } else {
            x = !StringUtils.isEmpty(c);
        }
        return x;
    }

    @Override
    public Object execute(ExpEvaluator evaluator, Exp[] args) {
        Object c = args[0].evalResult(evaluator);

        // Object v1 = args[1].evalResult(evaluator);
        // Object v2 = args.length>2?args[2].evalResult(evaluator):null;
        boolean x = check(c);


        if (x) {
            try {
                evaluator.pushNewFoggyClosure();
                // 匹配,执行
                Object obj = args[1].evalValue(evaluator);
                if (obj == NOT_MATCH) {
                    //如果为NOT_MATCH，不能向外传递了
                    //详见String test = "if(true){ if(false){}}else{syserr('???')}";的执行
                    return null;
                }
                return obj;
            } finally {
                evaluator.popFsscriptClosure();
            }
        } else {
            // 不匹配，执行第三个参数，如果存在
            return args.length > 2 ? args[2].evalValue(evaluator) : NOT_MATCH;
        }

        // x= ((Boolean) c) ? v1 : v2;
        // x= StringUtils.isEmpty(c) ? v2 : v1;

    }

    @Override
    public String getName() {
        return "iif";
    }

}
