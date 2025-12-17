package com.foggyframework.fsscript.closure;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.springframework.core.io.Resource;

public class SimpleFsscriptClosureDefinitionSpace extends AbstractFsscriptClosureDefinitionSpace {
    public static final SimpleFsscriptClosureDefinitionSpace SIMPL_FSCRIPT_CLOSURE_DEFINITION_SPACE = new SimpleFsscriptClosureDefinitionSpace();

    @Override
    public Fsscript loadFsscript(ExpEvaluator ee, String file) {
        throw new UnsupportedOperationException("SimpleFsscriptClosureDefinitionSpace不支持加载其他Fsscript，请使用FileFsscriptClosureDefinitionSpace或其他Space: "+file);
    }

    @Override
    public String getPath() {
        return "";
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public Resource getResource(ExpEvaluator ee, String location) {
        return null;
    }

    @Override
    public Resource getResource() {
        return null;
    }

    @Override
    public Bundle getBundle() {
        return null;
    }

}
