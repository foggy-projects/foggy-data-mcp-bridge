package com.foggyframework.core.spring.bean;

import java.util.HashSet;
import java.util.Set;

public interface ICGLibProxy {

    Set<String> packages = new HashSet<>();


    static void addFoggyFrameworkPackage(String p) {
        packages.add(p);
    }

    static void addFoggyFrameworkPackage(Set<String> p) {
        packages.addAll(p);
    }

    Object newProxyInterface(IMethodInterceptor methodInterceptor, Class... interfaces);

    Object newProxyInterface(Class targetClass, IMethodInterceptor methodInterceptor, Class[] interfaces);
}
