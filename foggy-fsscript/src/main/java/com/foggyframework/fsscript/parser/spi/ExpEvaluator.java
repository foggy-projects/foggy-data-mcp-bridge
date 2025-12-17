/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.parser.spi;

import com.foggyframework.fsscript.exp.EmptyExp;
import com.foggyframework.fsscript.exp.NullExp;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

import java.util.*;
import java.util.Map.Entry;

public interface ExpEvaluator {

     String _argumentsKey = "_arguments";

    /**
     * 呃threadSafeAccept需要clone一份新的，否则会有问题
     * @return
     */
    ExpEvaluator clone();
    Stack<FsscriptClosure> getStack();

    FsscriptClosure pushNewFoggyClosure();

    /**
     * 从当前FsscriptClosure得到
     *
     * @param name 变量名
     * @return 变量名对应的值
     */
    Object getVar(String name);

    ImportedFsscript getImport(String path);

    ImportedFsscript addImport(ExpEvaluator ee,String path, Fsscript fscript);

    default ImportedFsscript addImport(String path, Fsscript fscript){
        return addImport(this,path,fscript);
    }


    default Object getVarResult(String name) {

        Object v = getVar(name);
        if (v == EmptyExp.EMPTY || v == NullExp.NULL) {
            return null;
        }
        return name;
    }

    <T> T getVar(String name, Class<T> cls);

    VarDef getVarDef(String name);

    /**
     * 返回被替换的旧对象，不存在则返回 空
     *
     * @param name  变量名
     * @param value 需要设置对应的变量的值
     * @return 返回value
     */
    Object setVar(String name, Object value);

    /**
     * 返回被替换的旧对象，不存在则返回 空
     * 与setVar不同的是，它优先找VarDef
     *
     * @param name  变量名
     * @param value 需要设置对应的变量的值
     * @return 返回value
     */
    default Object setParentVarFirst(String name, Object value) {
        VarDef varDef = getVarDef(name);
        if (varDef == null) {
            setVar(name, value);
        } else {
            varDef.setValue(value);
        }
        return value;
    }

    FsscriptClosure getCurrentFsscriptClosure();

    FsscriptClosure pushFsscriptClosure(FsscriptClosure fScriptClosure);

    FsscriptClosure pushFsscriptClosureOnly(FsscriptClosure fScriptClosure);

    FsscriptClosure popFsscriptClosure();

    ApplicationContext getApplicationContext();

    default Resource getResource(String location) {
        FsscriptClosure fs = getCurrentFsscriptClosure();
        if (fs == null || fs.getBeanDefinitionSpace() == null) {
            return getApplicationContext().getResource(location);
        } else {
//            fs.
            return fs.getBeanDefinitionSpace().getResource(this, location);
        }

    }
    default ExpFactory getExpFactory() {
        return null;
    }
    default void setExpFactory(ExpFactory expFactory) {

    }
    default void setMap2Var(Map<String, Object> args) {
        if (args != null) {
            for (Entry<String, Object> e : args.entrySet()) {
                setVar(e.getKey(), e.getValue());
            }
        }
    }

    default Map<String, Object> getExportMap() {
        Map<String, Object> exportMap = (Map<String, Object>) getCurrentFsscriptClosure().getVar(FsscriptClosure.EXPORT_MAP_KEY);
        return exportMap == null ? Collections.EMPTY_MAP : exportMap;
    }

    default <T> T getExportObject(String name) {
        Map<String, Object> mm = getExportMap();
        return mm == null ? null : (T) mm.get(name);
    }
    default <T> T getExportObjectInDefault(String name) {
        Map<String, Object> mm = getExportMap();
        if(mm!=null){
            return (T) mm.get(name);
        }
        return null;
    }
    default <T> T getExportObject(String name, T nullValue) {
        T t = getExportObject(name);
        return t == null ? nullValue : t;
    }
    default <T> T getExportObjectInDefault(String name, T nullValue) {
        Map<String, Object> mm = getExportMap();
        if(mm!=null){
            return (T) mm.get(name);
        }
        return null;
    }

    default <T> T getBean(String name) {
        return (T) getApplicationContext().getBean(name);
    }

    <T> T getContext(Class<T> cls);

    default FsscriptClosureDefinitionSpace getBeanDefinitionSpace() {
        FsscriptClosure fs = getCurrentFsscriptClosure();
        return fs == null ? null : fs.getBeanDefinitionSpace();
    }

    default void pushFsscriptClosure(List<FsscriptClosure> savedStack) {
        for (FsscriptClosure fsscriptClosure : savedStack) {
            pushFsscriptClosure(fsscriptClosure);
        }
//        for (int i = savedStack.size() - 1; i >= 0; i--) {
//            pushFsscriptClosure(savedStack.get(i));
//        }
    }

    default void popFsscriptClosure(int length) {
        for (int i = 0; i < length; i++) {
            popFsscriptClosure();
        }
    }
}
