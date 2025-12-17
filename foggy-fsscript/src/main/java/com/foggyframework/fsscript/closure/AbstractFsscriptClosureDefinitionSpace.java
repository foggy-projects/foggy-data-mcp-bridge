package com.foggyframework.fsscript.closure;

import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinitionSpace;
import org.springframework.core.io.Resource;

public abstract  class AbstractFsscriptClosureDefinitionSpace implements FsscriptClosureDefinitionSpace {
    @Override
    public void destroy() {

    }
    public FsscriptClosureDefinition newFsscriptClosureDefinition() {
        return new SimpleFsscriptClosureDefinition(this);
    }
}
