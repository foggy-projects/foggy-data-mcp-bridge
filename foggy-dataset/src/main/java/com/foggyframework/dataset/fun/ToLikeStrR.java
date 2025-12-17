/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.dataset.fun;


import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

/**
 * 
 * 
 * 
 * @author Foggy
 * 
 */

public class ToLikeStrR  implements FunDef {

	@Override
	public Object execute(ExpEvaluator ee, Exp[] args) {
		Object str = args[0].evalResult(ee);
		return StringUtils.isEmpty(str) ? null : (str + "%");
	}

	@Override
	public String getName() {
		return "toLikeStrR";
	}

}
