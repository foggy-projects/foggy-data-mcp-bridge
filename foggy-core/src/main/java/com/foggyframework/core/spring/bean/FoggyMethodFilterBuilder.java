package com.foggyframework.core.spring.bean;

import java.lang.reflect.Method;

/**
 * @author fengjianguang
 */
public interface FoggyMethodFilterBuilder extends Comparable<FoggyMethodFilterBuilder> {

    FoggyMethodFilter build(Method method, String beanName, Class beanClass);

    int ORDER_MAX = Integer.MAX_VALUE;
    int ORDER_MIN = Integer.MIN_VALUE;
    @Override
    default int compareTo(FoggyMethodFilterBuilder o) {

        return Integer.compare(o.order(), this.order());
    }

    /**
     * 越大越靠前！
     *
     * @return
     */
    default int order() {
        return 100;
    }

    default boolean match(Class<?> beanClass, String beanName,String packageName){
        return false;
    }
}

