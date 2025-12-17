/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.parser.spi;

public interface Parser {

    /**
     * "awerwer,asdferwe--wre"+${ee-2}+"werw"
     *
     * @param str
     * @return
     * @throws Exception
     */
    Exp compile(FsscriptClosureDefinition fcDefinition, String str) throws CompileException;

    /**
     * ds.select('s')
     *
     * @param str
     * @return
     * @throws Exception
     */
    Exp compileEl(FsscriptClosureDefinition fcDefinition, String str) throws CompileException;


}
