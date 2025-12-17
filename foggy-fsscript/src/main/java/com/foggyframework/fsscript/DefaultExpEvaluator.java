/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript;


import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.fsscript.closure.SimpleFsscriptClosureDefinition;
import com.foggyframework.fsscript.exp.ExportExp;
import com.foggyframework.fsscript.parser.spi.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * 不支持内置函数！
 *
 * @author oldseasoul
 */
@Getter
@Setter
public class DefaultExpEvaluator implements ExpEvaluator {

    @Override
    public ExpEvaluator clone() {
        DefaultExpEvaluator expEvaluator = new DefaultExpEvaluator(appCtx,null);
        expEvaluator.stack = new Stack<>();
//        for (FsscriptClosure fsscriptClosure : stack) {
//            expEvaluator.stack.add(fsscriptClosure);
//        }
        expEvaluator.stack.addAll(stack);

        expEvaluator.expFactory = expFactory;
        return expEvaluator;
    }

    public Stack<FsscriptClosure> getStack() {
        return stack;
    }

    private Stack<FsscriptClosure> stack = new Stack();

    private ApplicationContext appCtx;

    private ExpFactory expFactory;

//    public static

    @Override
    public ImportedFsscript getImport(String path) {
        FsscriptClosure fs = stack.firstElement();
        Map<String, ImportedFsscript> mm = (Map<String, ImportedFsscript>) fs.getVar(FsscriptClosure.IMPORT_MAP_KEY);

        if (mm == null) {
            mm = new HashMap<>();
            fs.setVar(FsscriptClosure.IMPORT_MAP_KEY, mm);
            return null;
        }

        return mm.get(path);
    }

    @Override
    public ImportedFsscript addImport(ExpEvaluator ee, String path, Fsscript fscript) {
        FsscriptClosure fs = stack.firstElement();
        Map<String, ImportedFsscript> mm = (Map<String, ImportedFsscript>) fs.getVar(FsscriptClosure.IMPORT_MAP_KEY);

        if (mm == null) {
            mm = new HashMap<>();
            fs.setVar(FsscriptClosure.IMPORT_MAP_KEY, mm);
        }
        if (mm.containsKey(path)) {
            return mm.get(path);
//            throw new RuntimeException("已经导入过" + path + "，请不要重复导入");
        }
        try {
            //执行fscript中的脚本，以得到export等初始化的数据
            pushFsscriptClosureOnly(fscript.getFsscriptClosureDefinition().newFoggyClosure());
            fscript.eval(ee);
            FsscriptClosure fss = popFsscriptClosure();
            //从fss弄到所有export的对象，放到ImportedFsscript

            ImportedFsscript importedFsscript = new ImportedFsscript(fss, fscript);
            mm.put(path, importedFsscript);
//        mm.put(path, fscript);

            return importedFsscript;
        } finally {
        }
    }

    public static final DefaultExpEvaluator newInstance() {
        return newInstance(null);
    }

    public static final DefaultExpEvaluator newInstance(ApplicationContext appCtx) {
        return new DefaultExpEvaluator(appCtx, SimpleFsscriptClosureDefinition.DEFAULT_FSCRIPT_CLOSUR_EDEFINITION.newFoggyClosure());
    }

    public static final DefaultExpEvaluator newInstance(ApplicationContext appCtx, FsscriptClosure fScriptClosure) {
        return new DefaultExpEvaluator(appCtx, fScriptClosure);
    }

    public DefaultExpEvaluator(ApplicationContext appCtx, FsscriptClosure fScriptClosure) {
        this.appCtx = appCtx;
        if (fScriptClosure != null) {
            stack.push(fScriptClosure);
        }

    }

    public FsscriptClosureDefinitionSpace getBeanDefinitionClosure() {
        FsscriptClosure fs = getCurrentFsscriptClosure();
        return fs.getBeanDefinitionSpace();
    }

    /**
     * 返回当前最后push进入的一个对象
     */
    public Object getContext() {
        return stack.size() > 0 ? stack.lastElement() : null;
    }

    @Override
    public FsscriptClosure pushNewFoggyClosure() {
        FsscriptClosure fc = getCurrentFsscriptClosure();
        FsscriptClosure fs = fc.newFoggyClosure();
        pushFsscriptClosure(fs);

        //继承EXPORT_MAP_KEY ！！
        Map<String, Object> exportMap = ExportExp.getExportMap(fc);
        fs.setVar(FsscriptClosure.EXPORT_MAP_KEY, exportMap);

        return fs;
    }

    @Override
    public Object getVar(String name) {
        VarDef def = getVarDef(stack, name);
        return def == null ? null : def.getValue();
    }


    public VarDef getVarDef(Stack<FsscriptClosure> stack, String name) {

        FsscriptClosureDefinitionSpace space = null;
        for (int i = stack.size() - 1; i >= 0; i--) {
            FsscriptClosure fScriptClosure = stack.get(i);
            if (space == null) {
                space = fScriptClosure.getBeanDefinitionSpace();
            }
            if (fScriptClosure.getBeanDefinitionSpace() == space) {
                VarDef def = fScriptClosure.getVarDef(name);
                if (def != null) {
                    return def;
                }
            } else {
                return null;
            }
        }
//        stack.


        return null;
    }

    @Override
    public <T> T getVar(String name, Class<T> cls) {
        return (T) getVar(name);
    }

    @Override
    public VarDef getVarDef(String name) {
        return getVarDef(stack, name);
    }


    @Override
    public Object setVar(String name, Object value) {
        getCurrentFsscriptClosure().setVar(name, value);

//        if (varDef == null) {
//            getCurrentFsscriptClosure().setVar(name, value);
//        } else {
//            varDef.setValue(value);
//        }

        return value;
    }

    @Override
    public FsscriptClosure getCurrentFsscriptClosure() {
        if (stack.isEmpty()) {
            return null;
        }
        return stack.lastElement();
    }

    @Override
    public FsscriptClosure pushFsscriptClosure(FsscriptClosure fScriptClosure) {
        //继承EXPORT_MAP_KEY ！！
        FsscriptClosure fc = getCurrentFsscriptClosure();
        Map<String, Object> exportMap = ExportExp.getExportMap(fc);
        fScriptClosure.setVar(FsscriptClosure.EXPORT_MAP_KEY, exportMap);

        return stack.push(fScriptClosure);
    }

    @Override
    public FsscriptClosure pushFsscriptClosureOnly(FsscriptClosure fScriptClosure) {
        //继承EXPORT_MAP_KEY ！！
        try {
            String d = System.getProperty("fsscript_debug");
            if (StringUtils.equals(d, "debug")) {
                return stack.push(fScriptClosure);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        //下面三段语句,还是要拿掉!
        FsscriptClosure fc = getCurrentFsscriptClosure();
        Map<String, Object> exportMap = ExportExp.getExportMap(fc);
        fScriptClosure.setVar(FsscriptClosure.EXPORT_MAP_KEY, exportMap);

        return stack.push(fScriptClosure);
    }

    @Override
    public FsscriptClosure popFsscriptClosure() {
        return stack.pop();
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return appCtx;
    }

    @Override
    public <T> T getContext(Class<T> cls) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            FsscriptClosure fScriptClosure = stack.get(i);

            if (cls.isInstance(fScriptClosure)) {
                return (T) fScriptClosure;
            }
        }
        return null;
    }

}
