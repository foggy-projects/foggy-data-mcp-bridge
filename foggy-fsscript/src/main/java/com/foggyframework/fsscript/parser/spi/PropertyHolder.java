/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.parser.spi;

public interface PropertyHolder {
	public static final Object NO_MATCH = new Object();

	/**
	 * 如果返回NO_MATCH,则表示不处理该属性
	 * 
	 * @param name
	 * @return
	 */
	Object getProperty(String name);

}
