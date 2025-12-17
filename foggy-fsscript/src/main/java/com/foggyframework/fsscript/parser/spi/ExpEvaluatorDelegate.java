package com.foggyframework.fsscript.parser.spi;


import lombok.experimental.Delegate;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;
import java.util.Stack;


public class ExpEvaluatorDelegate implements ExpEvaluator{
    protected ExpEvaluator delegate;
    @Override
    public ExpEvaluator clone() {
        ExpEvaluator c = delegate.clone();

        return new ExpEvaluatorDelegate(c);
    }

    @Override
    public Stack<FsscriptClosure> getStack() {
        return delegate.getStack();
    }

    @Override
    public FsscriptClosure pushNewFoggyClosure() {
        return delegate.pushNewFoggyClosure();
    }

    @Override
    public Object getVar(String name) {
        return delegate.getVar(name);
    }

    @Override
    public ImportedFsscript getImport(String path) {
        return delegate.getImport(path);
    }

    @Override
    public ImportedFsscript addImport(ExpEvaluator ee, String path, Fsscript fscript) {
        return delegate.addImport(ee, path, fscript);
    }

    @Override
    public ImportedFsscript addImport(String path, Fsscript fscript) {
        return delegate.addImport(path, fscript);
    }

    @Override
    public Object getVarResult(String name) {
        return delegate.getVarResult(name);
    }

    @Override
    public <T> T getVar(String name, Class<T> cls) {
        return delegate.getVar(name, cls);
    }

    @Override
    public VarDef getVarDef(String name) {
        return delegate.getVarDef(name);
    }

    @Override
    public Object setVar(String name, Object value) {
        return delegate.setVar(name, value);
    }

    @Override
    public Object setParentVarFirst(String name, Object value) {
        return delegate.setParentVarFirst(name, value);
    }

    @Override
    public FsscriptClosure getCurrentFsscriptClosure() {
        return delegate.getCurrentFsscriptClosure();
    }

    @Override
    public FsscriptClosure pushFsscriptClosure(FsscriptClosure fScriptClosure) {
        return delegate.pushFsscriptClosure(fScriptClosure);
    }

    @Override
    public FsscriptClosure pushFsscriptClosureOnly(FsscriptClosure fScriptClosure) {
        return delegate.pushFsscriptClosureOnly(fScriptClosure);
    }

    @Override
    public FsscriptClosure popFsscriptClosure() {
        return delegate.popFsscriptClosure();
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return delegate.getApplicationContext();
    }

    @Override
    public Resource getResource(String location) {
        return delegate.getResource(location);
    }

    @Override
    public ExpFactory getExpFactory() {
        return delegate.getExpFactory();
    }

    @Override
    public void setExpFactory(ExpFactory expFactory) {
        delegate.setExpFactory(expFactory);
    }

    @Override
    public void setMap2Var(Map<String, Object> args) {
        delegate.setMap2Var(args);
    }

    @Override
    public Map<String, Object> getExportMap() {
        return delegate.getExportMap();
    }

    @Override
    public <T> T getExportObject(String name) {
        return delegate.getExportObject(name);
    }

    @Override
    public <T> T getExportObject(String name, T nullValue) {
        return delegate.getExportObject(name, nullValue);
    }

    @Override
    public <T> T getBean(String name) {
        return delegate.getBean(name);
    }

    @Override
    public <T> T getContext(Class<T> cls) {
        return delegate.getContext(cls);
    }

    @Override
    public FsscriptClosureDefinitionSpace getBeanDefinitionSpace() {
        return delegate.getBeanDefinitionSpace();
    }

    @Override
    public void pushFsscriptClosure(List<FsscriptClosure> savedStack) {
        delegate.pushFsscriptClosure(savedStack);
    }

    @Override
    public void popFsscriptClosure(int length) {
        delegate.popFsscriptClosure(length);
    }


    public ExpEvaluatorDelegate(){
    }
    public ExpEvaluatorDelegate(ExpEvaluator delegate){
        this.delegate = delegate;
    }

}
