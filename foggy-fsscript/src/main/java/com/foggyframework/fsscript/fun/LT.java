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

public class LT implements FunDef {

	@Override
	public Object execute(ExpEvaluator evaluator, Exp[] args)
			{
		Object obj1 = args[0].evalResult(evaluator);
		Object obj2 = args[1].evalResult(evaluator);
		return LT(obj1, obj2);
	}

	public static final boolean LT(Object obj1, Object obj2) {
		if (obj1 == obj2) {
			return false;
		}
		if (isNumberOrNull(obj1) && isNumberOrNull(obj2)) {
			return doubleValueOrZero(obj1) < doubleValueOrZero(obj2);
		}
		if (obj1 == null) {
			return true;
		}
		if (obj2 == null) {
			return false;
		}
		return obj1.toString().compareTo(obj2.toString()) < 0;
	}

	static boolean isNumberOrNull(Object obj) {
		return obj == null || obj instanceof Number;
	}

	static double doubleValueOrZero(Object obj) {
		if (obj == null) {
			return 0D;
		}
		return ((Number) obj).doubleValue();
	}

	@Override
	public String getName() {
		return "<";
	}
}
