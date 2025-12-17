/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.fun.ext;

import com.foggyframework.fsscript.fun.AbstractFunDef;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * 
 * 
 * sqlexp(${xx}," and t.xx=?")
 * 
 * @author Foggy
 * 
 */

public final class DateTimeFormatFunDef extends AbstractFunDef implements FunDef {

	@Override
	public Object execute(ExpEvaluator evaluator, Exp[] args){
		SimpleDateFormat xx = new SimpleDateFormat(
				args.length > 1 ? ((String) args[1].evalResult(evaluator)) : "yyyy-MM-dd HH:mm:ss");
		Object v = null;

		// v = FoggyRuntime.getNowTimeOffsetByMin(90);
		if (args.length == 0) {
			v = System.currentTimeMillis();
		} else {
			v = args[0].evalResult(evaluator);
		}
		if (v == null) {
			// v = FoggyRuntime.currentTimeMillis();
			// 如果指定了参数，但该参数返回空，则同样使用空返回，不使用当前时间
			return null;
		}
		if (v instanceof Date) {
			return xx.format(v);
		} else if (v instanceof Number) {
			return xx.format(((Number) v).longValue());
		}

		return v;
	}

	public static String _format(Date d) {
		SimpleDateFormat xx = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		return xx.format(d);
	}

	public static String _format(Calendar d) {
		return _format(d.getTime());
	}

	public static String _nowTime() {
		SimpleDateFormat xx = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		return xx.format(new Date());
	}

	@Override
	public String getName() {
		return "dateTimeFormat";
	}

}
