/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.parser.spi;


import java.util.Collection;

/**
 * 
 * @author Foggy
 * 
 */
public interface FoggyClosureManager {

	/************************************************************************/

	Collection<FsscriptClosure> getChild(FsscriptClosure parent);

//	FCEntry getFCItemEntry(String gloableId);

	FsscriptClosure getFoggyClosure(String id);

	/************************************************************************/

	Collection<FsscriptClosure> getFoggyClosures();

	FsscriptClosure getRootFoggyClosure();

	FsscriptClosure newFoggyClosure(FsscriptClosureDefinition foggyClosureDefinition, FsscriptClosure parent);

	/************************************************************************/

}
