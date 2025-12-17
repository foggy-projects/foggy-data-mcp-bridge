/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.exp;


import com.foggyframework.fsscript.parser.FunDef;

/**
 * 
 *
 */
public interface FunctionSet {
	public void append(final FunDef fd);

	/**
	 * @param funName
	 * @return
	 */
	public FunDef getFun(final String funName);

	public FunDef getFun(final UnresolvedFunCall funCall);

	public void append(String name, FunDef f);

	public void clear();
}
