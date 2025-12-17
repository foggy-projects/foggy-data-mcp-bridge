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
import org.springframework.stereotype.Component;

public class MathCeil implements FunDef {

    @Override
    public Object execute(ExpEvaluator evaluator, Exp[] args) {
        Object obj1 = args[0].evalResult(evaluator);
        double d = 0d;
        if (obj1 instanceof String) {
            d = Double.parseDouble(obj1.toString());
        } else if (obj1 instanceof Integer) {
            return obj1;
        } else if (obj1 instanceof Number) {
            d = ((Number) obj1).doubleValue();
        }
        return Math.ceil(d);
    }

    @Override
    public String getName() {
        return "ceil,math_ceil";
    }

}
