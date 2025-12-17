/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.fun;


import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

/**
 * 注意，它永远返回double 型，如果有需要返回int,请自行调用int等函数
 */
public class Multiply implements FunDef {

    @Override
    public Object execute(ExpEvaluator evaluator, Exp[] args) {
        Object v1 = args[0].evalResult(evaluator);
        Object v2 = args[1].evalResult(evaluator);

        if (v1 == null || v2 == null) {
            return null;
        }
        Number n1 = 0;

        if (v1 instanceof Number) {
            n1 = (Number) v1;
        }
        Number n2 = 0;
        if (v2 instanceof Number) {
            n2 = (Number) v2;
        }
        return n1.doubleValue() * n2.doubleValue();
    }

    @Override
    public String getName() {
        return "*";
    }

}
