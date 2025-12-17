/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.parser.spi;


import java.io.Serializable;

/**
 * 闭包,它在Fsscript运时产生
 *
 * @author Foggy
 */
@SuppressWarnings({"rawtypes"})
public interface FsscriptClosure extends Serializable /** , UniqueNameHolder */
{
    enum FoggyClosureState {
        NORMAL, DESTORY
    }

    String IMPORT_MAP_KEY = "$IMPORT_MAP_KEY";
    String EXPORT_MAP_KEY = "$EXPORT_MAP_KEY";

    FsscriptClosureDefinitionSpace getBeanDefinitionSpace();

    FsscriptClosureDefinition getFoggyClosureDefinition();

    FoggyClosureState getState();


    Object getVar(String name);

    VarDef getVarDef(String name);

    boolean isSameFoggyClosure(FsscriptClosureDefinition def);

    /**
     * 创建一个子FoggyClosure topObject,用来判断isSameFoggyClosure,
     * 一般是FoggyClass也可能是EventProcessorDefinition
     *
     * @return
     */
    FsscriptClosure newFoggyClosure();

    /**
     * 注，它一定会被设置在当前的FsscriptClosure！！！
     * 如果要是有值才设置，请使用getVarDef(name)得到VarDef再设置！！！
     * @param varName
     * @param value
     * @return
     */
    Object setVar(String varName, Object value);

    VarDef setVarDef(VarDef varDef);

    int size();


}
