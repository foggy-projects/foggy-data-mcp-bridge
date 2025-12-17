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

import java.math.BigDecimal;

public class GT_equal implements FunDef {
	public static void main(String[] args) {
		System.out.println(GTE(new BigDecimal("1"), 1.0));
	}

	@Override
	public Object execute(ExpEvaluator evaluator, Exp[] args)
			{
		Object v1 = args[0].evalResult(evaluator);
		Object v2 = args[1].evalResult(evaluator);
//		if (v1 == null) {
//			v1 = 0;
//		}
//		if (v2 == null) {
//			v2 = 0;
//		}
		return GTE(v1, v2);
	}

	/**
	 * 判断obj1>=obj2
	 * 
	 * @param obj1
	 * @param obj2
	 * @return
	 */
	public static final boolean GTE(Object obj1, Object obj2) {
		if (obj1 == obj2) {
			return true;
		}
		if (obj1 instanceof Number && obj2 instanceof Number) {
			Number v1 = (Number) obj1;
			Number v2 = (Number) obj2;
			return v1.doubleValue() >= v2.doubleValue();
		} else {

			if (obj1 == null) {
				return false;
			}
			if (obj2 == null) {
				return true;
			}
			return obj1.toString().compareTo(obj2.toString()) >= 0;
		}
	}

	/**
	 * 判断obj1>obj2
	 * 
	 * @param obj1
	 * @param obj2
	 * @return
	 */
	public static final boolean GT(Object obj1, Object obj2) {
		if (obj1 == obj2) {
			return false;
		}
		if (obj1 instanceof Number && obj2 instanceof Number) {
			Number v1 = (Number) obj1;
			Number v2 = (Number) obj2;
			return v1.doubleValue() > v2.doubleValue();
		} else {

			if (obj1 == null) {
				return false;
			}
			if (obj2 == null) {
				return false;
			}
			return obj1.toString().compareTo(obj2.toString()) > 0;
		}
	}

	@Override
	public String getName() {
		return ">=";
	}
}
