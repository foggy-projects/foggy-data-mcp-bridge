package com.foggyframework.core.spring.proxy;

import com.foggyframework.core.filter.FoggyFilterChainImpl;
import com.foggyframework.core.spring.bean.FoggyMethodCtx;
import com.foggyframework.core.spring.bean.FoggyMethodFilter;
import com.foggyframework.core.spring.bean.IMethodInterceptor;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpringFoggyBeanProxy implements IMethodInterceptor {

    private final Class<?> beanClass;
    Object bean;
    private Map<Method, MF> method2Filter= Collections.EMPTY_MAP;

    @Data
    @AllArgsConstructor
    private static class MF{
        String []methodNames;
        List<FoggyMethodFilter> filters;

    }

    public SpringFoggyBeanProxy(Object bean, Class<?> beanClass) {
        this.beanClass = beanClass;
        this.bean = bean;
    }

    public void addFilters(Method method, String[] methodNames,List<FoggyMethodFilter> filters) {
        if(method2Filter==Collections.EMPTY_MAP){
            method2Filter = new HashMap<>();
        }

        method2Filter.put(method, new MF(methodNames,filters));
    }


    @Override
    public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
        MF mf = method2Filter.get(method);
        if(mf == null){
           return method.invoke(bean,objects);
        }
        List<FoggyMethodFilter> ll= mf.filters;
//        methodProxy.getSignature().getArgumentTypes()[0].
        FoggyFilterChainImpl chain = new FoggyFilterChainImpl(ll);
        FoggyMethodCtx ctx = new FoggyMethodCtx(bean,mf.methodNames,objects);
        chain.doFilter(ctx);
        return ctx.getResult();
    }

    public Object interceptCtx(FoggyMethodCtx ctx,Method method) throws Throwable {
        MF mf = method2Filter.get(method);
        if(mf == null){
            return ctx.getResult();
        }
        List<FoggyMethodFilter> ll= mf.filters;
//        methodProxy.getSignature().getArgumentTypes()[0].
        ctx.setParameterNames(mf.getMethodNames());
        FoggyFilterChainImpl chain = new FoggyFilterChainImpl(ll);
        chain.doFilter(ctx);
        return ctx.getResult();
    }
}
