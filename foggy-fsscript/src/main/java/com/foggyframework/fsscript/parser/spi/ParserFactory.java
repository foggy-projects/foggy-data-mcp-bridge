/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.parser.spi;

public abstract class ParserFactory {

	private static final String DEFAULT_PARSER_NAME = "com.foggyframework.fsscript.parser.FoggyParserFactory";

	public static final ParserFactory newInstance() {
		ParserFactory pf = FactoryFinder.find(DEFAULT_PARSER_NAME,
				"com.foggyframework.fsscript.parser.FoggyParserFactory");
		return pf;

	}
	

	public abstract Parser newExpParser();

	public abstract Parser newExpParser(ExpFactory expFactory);
}
