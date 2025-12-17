package com.foggyframework.fsscript.closure;

import com.foggyframework.fsscript.parser.spi.ExpFactory;
import com.foggyframework.fsscript.parser.spi.FsscriptClosure;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinitionSpace;

public abstract class AbstractFsscriptClosureDefinition implements FsscriptClosureDefinition {

    FsscriptClosureDefinitionSpace fScriptClosureDefinitionSpace;

    public AbstractFsscriptClosureDefinition(FsscriptClosureDefinitionSpace fScriptClosureDefinitionSpace) {
        this.fScriptClosureDefinitionSpace = fScriptClosureDefinitionSpace;
    }

//    @Override
//    public ExpFactory getExpFactory() {
//        return fScriptClosureDefinitionSpace==null?null:fScriptClosureDefinitionSpace.getExpFactory();
//    }

    @Override
    public FsscriptClosureDefinitionSpace getFsscriptClosureDefinitionSpace() {
        return fScriptClosureDefinitionSpace;
    }

//    @Override
//    public String getUniqueName() {
//        return null;
//    }

    @Override
    public FsscriptClosure newFoggyClosure() {
        return new SimpleFsscriptClosure(this);
    }

    @Override
    public String toString() {
        return "AbstractFsscriptClosureDefinition{" +
                "fScriptClosureDefinitionSpace=" + fScriptClosureDefinitionSpace +
                '}';
    }
}
