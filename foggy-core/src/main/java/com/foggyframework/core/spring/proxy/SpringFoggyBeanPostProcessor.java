package com.foggyframework.core.spring.proxy;

import com.foggyframework.core.ex.ExRuntimeExceptionImpl;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.spring.bean.FoggyMethodCtx;
import com.foggyframework.core.spring.bean.FoggyMethodFilter;
import com.foggyframework.core.spring.bean.FoggyMethodFilterBuilder;
import com.foggyframework.core.spring.bean.ICGLibProxy;
import com.foggyframework.core.utils.FoggyBeanUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@Slf4j
public class SpringFoggyBeanPostProcessor implements BeanPostProcessor, Ordered {

    private final ApplicationContext appCtx;

    private final List<FoggyMethodFilterBuilder> builders;

    private final Map<String, Class<?>> beanName2Class = new HashMap<>();

    ICGLibProxy cglibProxy;

    public SpringFoggyBeanPostProcessor(List<FoggyMethodFilterBuilder> builders,ICGLibProxy cglibProxy, ApplicationContext appCtx) {
        this.builders = builders;
        this.cglibProxy = cglibProxy;
        this.appCtx = appCtx;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        beanName2Class.put(beanName, bean.getClass());


        return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        bean = BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
        Object oriBean = BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
        Class cls = beanName2Class.remove(beanName);

        if (match(cls, beanName)) {

            SpringFoggyBeanProxy proxy = null;
            if (bean instanceof SpringCGLibProxy.FoggySuperclass) {
                //呃 已经 被 代理 过了~~
                List<Class> clss = Arrays.stream(cls.getInterfaces()).collect(Collectors.toList());

                for (Class anInterface : clss) {
                    proxy = build(proxy, anInterface.getMethods(), bean, beanName, cls,true);
                }
                if (proxy != null) {

                    SpringFoggyBeanProxy foggyBeanProxy = proxy;
                    Object newBean = cglibProxy.newProxyInterface((o, method, objects, methodProxy) -> {

                        //调用原生oriBean方法
                        Method oriMethod = oriBean.getClass().getDeclaredMethod(method.getName(), method.getParameterTypes());
                        Object result = oriMethod.invoke(oriBean, objects);

                        //执行代理过滤器
                        FoggyMethodCtx ctx = new FoggyMethodCtx(o, null, objects);
                        ctx.setResult(result);
                        foggyBeanProxy.interceptCtx(ctx, method);

                        return ctx.getResult();
                    }, clss.toArray(new Class[0]));

                    return newBean;
                }
            } else if (Modifier.isFinal(cls.getModifiers())) {

                if (cls.getInterfaces().length > 1) {
                    throw new RuntimeException("仅支持一个接口的！" + cls + "," + beanName);
                }
                for (Class anInterface : cls.getInterfaces()) {
                    proxy = build(proxy, anInterface.getMethods(), bean, beanName, cls,false);
                }
            } else {
                //还是使用类
                proxy = build(null, cls.getMethods(), bean, beanName, cls,false);
            }

            if (proxy == null) {
                return bean;
            }
            bean = cglibProxy.newProxyInterface(cls, proxy, null);


        } else {

        }

        return bean;
    }

    private SpringFoggyBeanProxy build(SpringFoggyBeanProxy proxy, Method[] methods, Object bean, String beanName, Class<?> beanClass, boolean ignoreInvoke) {

//        FoggyBeanProxy proxy = null;
        for (Method method : methods) {
            int modifiers = method.getModifiers();
            if (Modifier.isStatic(modifiers)) {
                //不会对表态方法进行拦截！！！
                continue;
            }
            List<FoggyMethodFilter> filters = null;
            for (FoggyMethodFilterBuilder build : builders) {

                FoggyMethodFilter filter = build.build(method, beanName, beanClass);
                if (filter != null) {
                    if (filters == null) {
                        filters = new ArrayList<>();
                    }
                    filters.add(filter);
                }

            }
            if (filters != null) {
                if (proxy == null) {
                    proxy = new SpringFoggyBeanProxy(bean, beanClass);
                }
                if (!ignoreInvoke) {


                    filters.add((ctx, chain) -> {
                        try {
                            Object ret = method.invoke(ctx.getBean(), ctx.getArgs());
                            ctx.setResult(ret);
                        } catch (IllegalAccessException e) {
                            throw RX.throwB(e.getMessage(), null, e);
                        } catch (InvocationTargetException e) {

                            Throwable t = getCause(e);
                            if (t instanceof ExRuntimeExceptionImpl) {
                                throw (ExRuntimeExceptionImpl) t;
                            }
                            throw RX.throwB(getMsg(t), null, t);
                        }

                    });
                }
                String[] methodNames = FoggyBeanUtils.getParameterNames(method);
                proxy.addFilters(method, methodNames, filters);
            }

        }

        return proxy;
    }

    private String getMsg(Throwable e) {
        String msg = e.getMessage();
        Throwable ex = e;
        int times = 10;
        while (times > 0 ) {
            if(ex instanceof InvocationTargetException){
                ex = ((InvocationTargetException) ex).getTargetException();
                times--;
            }else if(ex instanceof UndeclaredThrowableException){
                times--;
                ex =  ex.getCause();
            }else{
               break;
            }

        }

        if (ex != null) {
            msg = ex.getMessage();
        }
        return msg;

    }

    private Throwable getCause(InvocationTargetException e) {
//        String msg = e.getMessage();
        Throwable ex = e;
        int times = 10;
        while (times > 0 ) {
            if(ex instanceof InvocationTargetException){
                ex = ((InvocationTargetException) ex).getTargetException();
                times--;
            }else if(ex instanceof UndeclaredThrowableException){
                times--;
                ex =  ex.getCause();
            }else{
                break;
            }

        }

        if (ex != null) {
            return ex;
        }
        return e;

    }

    private boolean match(Class<?> cls, String beanName) {
        if (cls == null) {

            return false;
        }
        //呃，FeignClient的cls没有package
        String pn;
        if (cls.getPackage() == null || cls.getPackage().getName().startsWith("jdk.proxy")
                || cls.getName().startsWith(SpringCGLibProxy.class.getName())) {
            //对于TestFeignMockClient，jpaResp***这类，本身就是代理类的～～直接用beanName为包名进行查找
            //呃，有时候会以jdk.proxy开头
            pn = beanName;
        } else {
            pn = cls.getPackage().getName();
        }
        if (pn.startsWith("com.sun.proxy")) {
            if (beanName.startsWith("(inner bean)")) {
                log.info("跳过inner bean: " + beanName);
                return false;
            }

            log.debug("收到代理类: " + cls + "，我们尝试从它实现的接口来找: " + cls.getInterfaces());
            for (Class<?> anInterface : cls.getInterfaces()) {
                String interfacePackNage = anInterface.getPackage() == null ? null : anInterface.getPackage().getName();
                if (interfacePackNage != null) {
                    for (String p : ICGLibProxy.packages) {
                        if (interfacePackNage.startsWith(p)) {
                            log.debug("通过接口" + anInterface + "找到匹配的packages: " + p);
                            return true;
                        }
                    }
                }
            }
            log.warn("未能从接口中找到适合的，我们最后用beanName再挣扎下吧～～: " + beanName);
            pn = beanName;
        }


        for (String p : ICGLibProxy.packages) {
            if (pn.startsWith(p)) {
                return true;
            }
        }


        for (FoggyMethodFilterBuilder builder : builders) {
            if (builder.match(cls, beanName, pn)) {
                return true;
            }
        }

        return false;
//        throw new UnsupportedOperationException();
//        return cls.getAnnotation(FoggyFramework.class) != null;
    }

    @Override
    public int getOrder() {
        //警告，它的顺序，必须在事务处理器等bean之后
        return Ordered.LOWEST_PRECEDENCE;
    }

}
