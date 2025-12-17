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

import java.math.BigDecimal;

public class Equal implements FunDef {

	public static final boolean eq(Object v1, Object v2) {
		if (v1 == null) {
			return v2 == null ? true : false;
		}
		if (v2 == null) {
			return false;
		}
		/***********************************************************************/
		/**
		 * 临时解决方案,用于处理BigDecimal与各种类型的比较
		 */
		if (v1 instanceof BigDecimal) {
			return ((Comparable) v1).compareTo(new BigDecimal(v2.toString())) == 0 ? true : false;
		}
		if (v2 instanceof BigDecimal) {
			return ((Comparable) v2).compareTo(new BigDecimal(v1.toString())) == 0 ? true : false;
		}
		/***********************************************************************/
		return v1.equals(v2);
	}


	@Override
	public Object execute(ExpEvaluator evaluator, Exp[] args)
			{
		Object v1 = args[0].evalResult(evaluator);
		Object v2 = args[1].evalResult(evaluator);

		return eq(v1, v2);
	}

	@Override
	public String getName() {
		return "==";
	}

}
