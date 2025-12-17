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

import java.lang.reflect.InvocationTargetException;

/**
 * 
 * 
 * eee||er
 * 
 * @author Foggy
 * 
 */

public class ConcatExp extends AbstractFunDef implements FunDef {

	@Override
	public Object execute(ExpEvaluator evaluator, Exp[] args) {

		Object obj = args[0].evalResult(evaluator);
		// 非常重要，也许应该告诉evalValue，不要给我返回 AbstractExp.EMPTY_RETURN
//		if (obj == AbstractExp.EMPTY_RETURN) {
//			obj = null;
//		}
		if (obj == null) {
			return args[1].evalResult(evaluator);
		}
		return Iif.check(obj) ? obj : args[1].evalResult(evaluator);

	}

	@Override
	public String getName() {
		return "||";
	}

}
