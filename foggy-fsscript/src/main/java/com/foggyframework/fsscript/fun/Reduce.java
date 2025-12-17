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

public class Reduce implements FunDef {

	@Override
	public Object execute(ExpEvaluator evaluator, Exp[] args)
			{
		Object v1 = args[0].evalResult(evaluator);
		Object v2 = null;
		if (args.length > 1) {
			v2 = args[1].evalResult(evaluator);
		} else {
			// 只有一个参数？负数
			v2 = v1;
			v1 = 0;
		}
		Number n1 = 0;
		if ((v1 instanceof Integer || v1 instanceof Long) && (v2 instanceof Integer || v1 instanceof Long)) {
			return ((Number) v1).intValue() - ((Number) v2).intValue();
		}
		if (v1 instanceof Number) {
			n1 = (Number) v1;
		}
		Number n2 = 0;
		if (v2 instanceof Number) {
			n2 = (Number) v2;
		}
		return n1.doubleValue() - n2.doubleValue();
	}

	@Override
	public String getName() {
		return "-";
	}

}
