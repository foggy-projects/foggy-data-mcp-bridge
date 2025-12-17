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

public class LT_equal implements FunDef {

	@Override
	public Object execute(ExpEvaluator evaluator, Exp[] args)
			{
		Number v1 = (Number) args[0].evalResult(evaluator);
		Number v2 = (Number) args[1].evalResult(evaluator);
		if (v1 == null) {
			v1 = 0;
		}
		if (v2 == null) {
			v2 = 0;
		}
		return v1.doubleValue() <= v2.doubleValue();
	}

	@Override
	public String getName() {
		return "<=";
	}
}
