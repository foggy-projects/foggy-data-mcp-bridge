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
 * 0,null,""均表示 false
 * 
 * @author Foggy
 *
 */
public class ElseIf implements FunDef {

	@Override
	public Object execute(ExpEvaluator evaluator, Exp[] args)
			{
		Object c = args[0].evalValue(evaluator);

		if (c == Iif.NOT_MATCH) {
			try {
				evaluator.pushNewFoggyClosure();
				return args[1].evalValue(evaluator);
			}finally {
				evaluator.popFsscriptClosure();
			}
		} else {
			return c;
		}

	}

	@Override
	public String getName() {
		return "elseif";
	}

}
