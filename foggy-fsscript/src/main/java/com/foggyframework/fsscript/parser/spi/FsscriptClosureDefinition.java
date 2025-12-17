package com.foggyframework.fsscript.parser.spi;

public interface FsscriptClosureDefinition {



	FsscriptClosureDefinitionSpace getFsscriptClosureDefinitionSpace();

//	String getUniqueName();

	FsscriptClosure newFoggyClosure();

//	ExpFactory getExpFactory();
}
