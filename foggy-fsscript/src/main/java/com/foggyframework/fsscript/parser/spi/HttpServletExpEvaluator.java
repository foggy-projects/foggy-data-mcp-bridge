package com.foggyframework.fsscript.parser.spi;

import com.foggyframework.core.ex.RX;
import lombok.Data;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 若是post,需要读取body,应当使用 import {bodyData} from '@foggyWebUtils';
 */
@Data
public class HttpServletExpEvaluator extends ExpEvaluatorDelegate implements ExpEvaluator {

    HttpServletRequest request;

    HttpServletResponse response;

    @Override
    public ExpEvaluator clone() {
        ExpEvaluator c = delegate.clone();

        return new HttpServletExpEvaluator(request,response,c);
    }

    public HttpServletExpEvaluator() {
    }

    public HttpServletExpEvaluator(HttpServletRequest request, HttpServletResponse response, ExpEvaluator delegate) {
        super(delegate);
        this.request = request;
        this.response = response;
    }

    @Override
    public Object getVarResult(String name) {
        Object v = super.getVarResult(name);

        return v == null ? getHttpServletVar(name) : v;
    }

    @Override
    public <T> T getVar(String name, Class<T> cls) {
        T v = super.getVar(name, cls);
        return v == null ? getHttpServletVar(name) : v;
    }

    private <T> T getHttpServletVar(String name) {
        String v = request.getParameter(name);
        return (T) v;
//        if(v==null && name.equals("_body"))
    }

    @Override
    public VarDef getVarDef(String name) {
        VarDef varDef = super.getVarDef(name);
        if (varDef != null) {
            return varDef;
        }
        String v = request.getParameter(name);
        if (v != null) {
            return new HttpServletVarDef(name, v);
        }
        return null;
    }

    @Override
    public Object getVar(String name) {
        Object v = super.getVar(name);
        return v == null ? getHttpServletVar(name) : v;
    }

    public static class HttpServletVarDef implements VarDef {
        String name;
        String value;

        public HttpServletVarDef(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public void setValue(Object x) {
            throw RX.throwB("HttpServletVarDef中不允许修改值！");
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
