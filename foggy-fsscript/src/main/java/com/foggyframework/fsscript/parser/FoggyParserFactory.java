/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.parser;


import com.foggyframework.fsscript.parser.spi.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

public class FoggyParserFactory extends ParserFactory {

	static class FoggyParser implements Parser {
		ExpParser expParser = null;

		@Override
		public Exp compile(FsscriptClosureDefinition fcDefinition, String str) throws CompileException {
			if (expParser == null) {
				expParser = new ExpParser();
			}
			expParser.setFcDefinition(fcDefinition);
			return expParser.compile(str);
		}

		@Override
		public Exp compileEl(FsscriptClosureDefinition fcDefinition, String str) throws CompileException {
			if (expParser == null) {
				expParser = new ExpParser();
			}
			expParser.setFcDefinition(fcDefinition);
			return expParser.compileEl(str);
		}

	}
@AllArgsConstructor
@NoArgsConstructor
	static class FoggyParserX implements Parser {
		ExpFactory expFactory = null;

		@Override
		public Exp compile(FsscriptClosureDefinition fcDefinition, String str) throws CompileException {
			ExpParser expParser = null;
			if (expFactory == null) {
				expParser = new ExpParser();
			}else{
				expParser = new ExpParser(expFactory);
			}
			expParser.setFcDefinition(fcDefinition);
			return expParser.compile(str);
		}

		@Override
		public Exp compileEl(FsscriptClosureDefinition fcDefinition, String str) throws CompileException {
			ExpParser expParser = null;
			if (expFactory == null) {
				expParser = new ExpParser();
			}else{
				expParser = new ExpParser(expFactory);
			}
			expParser.setFcDefinition(fcDefinition);
			return expParser.compileEl(str);
		}

	}
	@Override
	public Parser newExpParser() {
		return new FoggyParser();
	}

	@Override
	public Parser newExpParser(ExpFactory expFactory) {
		return new FoggyParserX(expFactory);
	}
}
