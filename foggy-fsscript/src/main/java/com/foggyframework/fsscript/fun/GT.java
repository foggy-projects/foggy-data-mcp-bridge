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

public class GT implements FunDef {

	@Override
	public Object execute(ExpEvaluator evaluator, Exp[] args)
			{
		Object obj1 = args[0].evalResult(evaluator);
		Object obj2 = args[1].evalResult(evaluator);
		if (obj1 == obj2) {
			return false;
		}

		if (obj1 instanceof Number && obj2 instanceof Number) {
			Number v1 = (Number) obj1;
			Number v2 = (Number) obj2;
//			if (v1 == null) {
//				v1 = 0;
//			}
//			if (v2 == null) {
//				v2 = 0;
//			}
			return v1.doubleValue() > v2.doubleValue();
		} else {

			if (obj1 == null) {
				return false;
			}
			if (obj2 == null) {
				return true;
			}
			return obj1.toString().compareTo(obj2.toString()) > 0;
		}
	}

	public static void main(String[] args) {
		System.out.println("2019-05-01".compareTo("2019-06-30"));
	}

	@Override
	public String getName() {
		return ">";
	}
}
