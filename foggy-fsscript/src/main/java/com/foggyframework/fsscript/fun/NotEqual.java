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

public class NotEqual implements FunDef {

	@Override
	public Object execute(ExpEvaluator evaluator, Exp[] args)
			{
		Object v1 = args[0].evalResult(evaluator);
		Object v2 = args[1].evalResult(evaluator);

		return !Equal.eq(v1, v2);
	}

	@Override
	public String getName() {
		return "<>";
	}

}
