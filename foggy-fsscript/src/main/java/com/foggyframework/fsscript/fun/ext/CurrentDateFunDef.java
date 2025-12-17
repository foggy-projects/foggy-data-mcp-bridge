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

public final class CurrentDateFunDef extends AbstractFunDef implements FunDef {

	@Override
	public Object execute(ExpEvaluator evaluator, Exp[] args){
		return new Date();
	}

	@Override
	public String getName() {
		return "currentDate,now";
	}

}
