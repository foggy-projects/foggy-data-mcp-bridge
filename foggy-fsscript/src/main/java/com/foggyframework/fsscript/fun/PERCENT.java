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

public class PERCENT implements FunDef {


	@Override
	public Object execute(ExpEvaluator evaluator, Exp[] args)
			{
		Number n1 = asNumber(args[0].evalResult(evaluator));
		Number n2 = asNumber(args[1].evalResult(evaluator));
		if (n2 == null || n2.intValue() == 0) {
			return 0;
		}
		int left = n1 == null ? 0 : n1.intValue();
		return left % n2.intValue();
	}

	private Number asNumber(Object value) {
		return value instanceof Number ? (Number)value : null;
	}

	@Override
	public String getName() {
		return "%";
	}

}
