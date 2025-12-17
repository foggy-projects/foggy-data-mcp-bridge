package com.foggyframework.core.spring.bean;

import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

public interface IMethodInterceptor {
    Object intercept(Object obj, Method method, Object[] args, MethodProxy var4) throws Throwable;
}
