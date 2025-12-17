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
 * 
 * see BaseFunSupport.typeof
 * 
 * @author fengjianguang
 *
 */
//@Named
//@Singleton
@Deprecated
public class Typeof implements FunDef {

	@Override
	public Object execute(ExpEvaluator ee, Exp[] args)
			{

		if (args.length == 0) {
			return null;
		}
		Object obj = args[0].evalResult(ee);
		if (obj == null) {
			return null;
		}
		String cls = obj.getClass().getName();
		if (cls.startsWith("java.lang.")) {
			return cls.substring("java.lang.".length(), cls.length());
		}
		return cls;
	}

	@Override
	public String getName() {
		return "typeof";
	}
}
