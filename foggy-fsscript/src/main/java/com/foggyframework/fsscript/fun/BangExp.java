/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.fun;


import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.lang.reflect.InvocationTargetException;

/**
 * 
 * 
 * !函数 如果为空,返回true
 * 
 * 0返回真 其他返回False
 * 
 * true返回 false
 * 
 * @author Foggy
 * 
 */

public class BangExp extends AbstractFunDef  {

	@Override
	public Object execute(ExpEvaluator ee, Exp[] args) {
		Object v = args[0].evalResult(ee);
		if (v == null) {
			return true;
		}
		if (v instanceof Boolean) {
			return !((Boolean) v).booleanValue();
		}
		if (v instanceof Integer) {
			return ((Integer) v).intValue() == 0;
		}
		String sv = v.toString();
		return sv.equals("0") || sv.isEmpty();
	}

	@Override
	public String getName() {
		return "!";
	}

}
