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

public class Plus implements FunDef {

	@Override
	public Object execute(ExpEvaluator evaluator, Exp[] args)
			{
		Object obj1 = args[0].evalResult(evaluator);
		Object obj2 = args[1].evalResult(evaluator);
		if (obj1 == null) {
			return obj2;
		}
		if (obj2 == null) {
			return obj1;
		}
		if (obj1 instanceof String) {
			return (String) obj1 + obj2;
		} else if (obj1 instanceof Integer && obj2 instanceof Integer) {
			return ((Number) obj1).intValue() + ((Number) obj2).intValue();
		} else if (obj1 instanceof Number && obj2 instanceof Number) {
			return ((Number) obj1).doubleValue() + ((Number) obj2).doubleValue();
		}
		return obj1.toString() + obj2.toString();
	}

	@Override
	public String getName() {
		return "+";
	}

}
