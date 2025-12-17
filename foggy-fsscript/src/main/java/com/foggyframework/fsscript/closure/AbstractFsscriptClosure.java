/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.closure;

import com.foggyframework.core.AbstractDecorate;
import com.foggyframework.fsscript.parser.spi.FsscriptClosure;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.parser.spi.VarDef;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public abstract class AbstractFsscriptClosure extends AbstractDecorate implements FsscriptClosure {

    protected FsscriptClosureDefinition foggyClosureDefinition;

    Map<String, VarDef> name2VarDef = new HashMap<>();

    public AbstractFsscriptClosure(FsscriptClosureDefinition foggyClosureDefinition) {
        this.foggyClosureDefinition = foggyClosureDefinition;
    }

    @Override
    public FoggyClosureState getState() {
        return null;
    }

    @Override
    public Object getVar(String name) {
        VarDef def = getVarDef(name);
        return def == null ? null : def.getValue();
    }

    @Override
    public VarDef getVarDef(String name) {
        return name2VarDef.get(name);
    }

    @Override
    public VarDef setVarDef(VarDef varDef) {
        return name2VarDef.put(varDef.getName(), varDef);
    }

    @Override
    public Object setVar(String varName, Object value) {
        VarDef def = getVarDef(varName);
        if (def == null) {
            def = new SimpleVarDef(varName, value);
            name2VarDef.put(varName, def);
        } else {
            def.setValue(value);
        }
        return value;
    }

    @Override
    public int size() {
        return 0;
    }


    @Override
    public FsscriptClosureDefinitionSpace getBeanDefinitionSpace() {
        if (foggyClosureDefinition == null) {
            return null;
        }
        return foggyClosureDefinition.getFsscriptClosureDefinitionSpace();
    }

    @Override
    public FsscriptClosureDefinition getFoggyClosureDefinition() {
        return foggyClosureDefinition;
    }


    @Override
    public boolean isSameFoggyClosure(FsscriptClosureDefinition def) {
        return def == foggyClosureDefinition;
    }

    @Override
    public FsscriptClosure newFoggyClosure() {
        return foggyClosureDefinition.newFoggyClosure();
    }

}
