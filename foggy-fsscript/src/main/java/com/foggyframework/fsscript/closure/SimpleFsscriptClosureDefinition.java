package com.foggyframework.fsscript.closure;

import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinitionSpace;

/**
 *
 */
public class SimpleFsscriptClosureDefinition extends AbstractFsscriptClosureDefinition {

    public static final SimpleFsscriptClosureDefinition DEFAULT_FSCRIPT_CLOSUR_EDEFINITION = new SimpleFsscriptClosureDefinition(SimpleFsscriptClosureDefinitionSpace.SIMPL_FSCRIPT_CLOSURE_DEFINITION_SPACE);

    public SimpleFsscriptClosureDefinition(FsscriptClosureDefinitionSpace fScriptClosureDefinitionSpace) {
        super(fScriptClosureDefinitionSpace);
    }
}
