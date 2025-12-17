/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.parser;


import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

/**
 * 
 * @author SeaSoul
 * 
 */
public interface FunDef {

	 Object execute(ExpEvaluator ee, Exp[] args) ;

	/**
	 * 如果对应多个，用","隔开
	 * 
	 * @return	函数名称
	 */
	 String getName();

}
