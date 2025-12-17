/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.parser.spi;


import com.foggyframework.core.utils.ErrorUtils;

public abstract class FactoryFinder {

	public static ParserFactory find(String defaultParserName, String string) {
		String x = System.getProperty(defaultParserName);
		Class<?> cls = null;
		if (x != null) {
			try {
				cls = Class.forName(x);
			} catch (ClassNotFoundException e) {
				System.err.println("ParserFactory not found! error:" + e.getMessage());
			}
		}
		try {
			if (cls == null)
				cls = Class.forName(string);
		} catch (ClassNotFoundException e1) {
			throw new RuntimeException("class [" + string + "] not found!");
		}
		try {
			return (ParserFactory) cls.newInstance();
		} catch (InstantiationException e) {
			throw ErrorUtils.toRuntimeException(e);
		} catch (IllegalAccessException e) {
			throw ErrorUtils.toRuntimeException(e);
		}
	}

}
