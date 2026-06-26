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

public class LT_equal implements FunDef {

	@Override
	public Object execute(ExpEvaluator evaluator, Exp[] args)
			{
		Object obj1 = args[0].evalResult(evaluator);
		Object obj2 = args[1].evalResult(evaluator);
		return LTE(obj1, obj2);
	}

	public static final boolean LTE(Object obj1, Object obj2) {
		if (obj1 == obj2) {
			return true;
		}
		if (LT.isNumberOrNull(obj1) && LT.isNumberOrNull(obj2)) {
			return LT.doubleValueOrZero(obj1) <= LT.doubleValueOrZero(obj2);
		}
		if (obj1 == null) {
			return true;
		}
		if (obj2 == null) {
			return false;
		}
		return obj1.toString().compareTo(obj2.toString()) <= 0;
	}

	@Override
	public String getName() {
		return "<=";
	}
}
