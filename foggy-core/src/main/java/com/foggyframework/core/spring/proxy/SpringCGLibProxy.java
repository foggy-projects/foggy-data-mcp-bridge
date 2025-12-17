package com.foggyframework.core.spring.proxy;

//import net.sf.cglib.proxy.Enhancer;
//import net.sf.cglib.proxy.MethodInterceptor;

import com.foggyframework.core.spring.bean.ICGLibProxy;
import com.foggyframework.core.spring.bean.IMethodInterceptor;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;

import java.lang.reflect.Modifier;


public class SpringCGLibProxy implements ICGLibProxy {

    public static Object createProxyObject(MethodInterceptor callback, Class clazz) {
        Enhancer enhancer = new Enhancer();
        //enhancer.setSuperclass(Object.class);

        enhancer.setSuperclass(clazz);

        enhancer.setCallback(callback);
        // System.err.println("createProxyObject : " + targetObject);
        return enhancer.create();

    }



    @Override
    public Object newProxyInterface(Class targetClass, IMethodInterceptor methodInterceptor, Class[] interfaces) {

        return newProxyInterface1(targetClass,methodInterceptor,interfaces);
    }

    @Override
    public Object newProxyInterface(IMethodInterceptor methodInterceptor, Class... interfaceCls) {
        return newProxyInterface1(methodInterceptor,interfaceCls);
    }

    public static  Object newProxyInterface1(Class targetClass, IMethodInterceptor methodInterceptor, Class[] interfaces) {
        if(Modifier.isFinal(targetClass.getModifiers())){
            //这个一个final类，我们使用它的接口来创建代理吧～～
            return newProxyInterface1(methodInterceptor,targetClass.getInterfaces());
        }else {
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(targetClass);
            if (interfaces != null) {
                enhancer.setInterfaces(interfaces);
            }
            enhancer.setCallback((MethodInterceptor) methodInterceptor::intercept);

            // System.err.println("createProxyObject : " + targetObject);
            return enhancer.create();
        }
    }

    public  static Object newProxyInterface1(IMethodInterceptor methodInterceptor, Class... interfaceCls) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(FoggySuperclass.class);

        enhancer.setInterfaces(interfaceCls);

        enhancer.setCallback((MethodInterceptor) methodInterceptor::intercept);

        return enhancer.create();
    }



    public static class FoggySuperclass {

    }

}
