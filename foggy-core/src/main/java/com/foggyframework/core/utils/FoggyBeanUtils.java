/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core.utils;

import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.core.utils.beanhelper.BeanProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.context.annotation.Bean;
import org.springframework.core.DefaultParameterNameDiscoverer;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

//import org.springframework.context.ApplicationContext;

/**
 * @author Foggy
 * @since foggy-1.0
 */
@SuppressWarnings("unchecked")
@Slf4j
public class FoggyBeanUtils {
    private static final DefaultParameterNameDiscoverer discover = new DefaultParameterNameDiscoverer();

    /**
     * 获取类cls上的指定注释annCls，包含父类
     *
     * @param cls
     * @return
     */
    public final static <A> List<A> getAllClassAnn(Class<?> cls, Class<A> annCls) {
        List<A> list = new ArrayList<A>();

        Class<?>[] cc = getAllSuperClasses(cls);
        for (Class c : cc) {
            if (c.getAnnotation(annCls) != null) {
                list.add((A) c.getAnnotation(annCls));
            }
        }
        return list;
    }

    /**
     * 获取指定类上的所有注释，包含父类
     *
     * @param cls
     * @return
     */
    public final static List<Annotation> getAllClassAnn(Class<?> cls) {
        List<Annotation> list = new ArrayList<Annotation>();

        Class<?>[] cc = getAllSuperClasses(cls);
        for (Class c : cc) {
            Collections.addAll(list, c.getAnnotations());

        }
        return list;
    }

    /**
     * 获取传入类的所有父类及其实现的接口,好吧，包含它自己
     *
     * @param cls
     * @return
     */
    public final static Class<?>[] getAllSuperClasses(Class<?> cls) {
        List<Class<?>> list = new ArrayList<Class<?>>();

        while (cls != null) {

            list.add(cls);
            Collections.addAll(list, cls.getInterfaces());

            cls = cls.getSuperclass();
        }
        Class<?>[] xx = new Class[list.size()];
        return list.toArray(xx);
    }

    public static String[] getParameterNames(Method m) {

        String[] names = discover.getParameterNames(m);

        return names;
    }

    /**
     * 从右向左赋值，非空不赋，右边的非空值拥有最高优先级
     *
     * @param beans
     * @return
     */
    public static Object apply(Object... beans) {
        if (beans.length == 0) {
            return null;
        }
        if (beans.length == 1) {
            return beans[0];
        }
        Object root = beans[0];
        for (int i = 1; i < beans.length; i++) {
            root = apply(root, beans[i]);
        }
        return root;
    }

    public static Object apply(Object obj1, Object obj2) {
//        BeanUtils.
        BeanInfoHelper beanInfoHelper = BeanInfoHelper.getClassHelper(obj1.getClass());

        if (obj2 instanceof Map) {
            for (Map.Entry<String, ?> entry : ((Map<String, ?>) obj2).entrySet()) {
                if (entry.getValue() != null) {
                    beanInfoHelper.getBeanProperty(entry.getKey()).setBeanValue(obj1, entry.getValue());
                }
            }
        } else {
            BeanInfoHelper beanInfoHelper2 = BeanInfoHelper.getClassHelper(obj2.getClass());

            for (BeanProperty beanProperty : beanInfoHelper2.getFieldProperties()) {
                Object value = beanProperty.getBeanValue(obj2);
                if (value != null) {
                    beanInfoHelper.getBeanProperty(beanProperty.getName()).setBeanValue(obj1, value);
                }
            }

        }
        return obj1;

    }

    /**
     * 把bean(支持map)中的java字段命名格式，转换为vehicle_type格式
     *
     * @param bean
     * @return
     */
    public static Map<String, Object> bean2SmString(Object bean) {
        if (bean == null) {
            return null;
        }
        Map<String, Object> r = new HashMap<>();
        if (bean instanceof Map) {
            for (Map.Entry<String, ?> entry : ((Map<String, ?>) bean).entrySet()) {
                r.put(StringUtils.to_sm_string(entry.getKey()), entry.getValue());
            }
        } else {
            for (BeanProperty fieldProperty : BeanInfoHelper.getClassHelper(bean).getFieldProperties()) {
                Object v = fieldProperty.getBeanValue(bean);
                r.put(StringUtils.to_sm_string(fieldProperty.getName()), v);
            }
        }
        return r;
    }

    public static List<Map<String, Object>> bean2SmStringList(List<Object> beans) {
        if (beans == null) {
            return Collections.EMPTY_LIST;
        }
        return beans.stream().map(e -> bean2SmString(e)).collect(Collectors.toList());
    }

    public static List<Map<String, Object>> sm2BeanStringList(List<Object> beans) {
        if (beans == null) {
            return Collections.EMPTY_LIST;
        }
        return beans.stream().map(e -> sm2BeanString(e)).collect(Collectors.toList());
    }

    /**
     * 把vehicle_type格式转换为java的vehicleType格式
     *
     * @param bean
     * @return
     */
    public static Map<String, Object> sm2BeanString(Object bean) {
        if (bean == null) {
            return null;
        }
        Map<String, Object> r = new HashMap<>();
        if (bean instanceof Map) {
            for (Map.Entry<String, ?> entry : ((Map<String, ?>) bean).entrySet()) {
                r.put(StringUtils.to(entry.getKey()), entry.getValue());
            }
        } else {
            for (BeanProperty fieldProperty : BeanInfoHelper.getClassHelper(bean).getFieldProperties()) {
                Object v = fieldProperty.getBeanValue(bean);
                r.put(StringUtils.to(fieldProperty.getName()), v);
            }
        }
        return r;
    }

    public static void copyPropertiesIfHasSource(Object source, Object target) throws BeansException {
        if (source == null) {
            return;
        }
        BeanUtils.copyProperties(source, target);
    }

    /**
     * Copy the property values of the given source bean into the given target bean,
     * only setting properties defined in the given "editable" class (or interface).
     * <p>Note: The source and target classes do not have to match or even be derived
     * from each other, as long as the properties match. Any bean properties that the
     * source bean exposes but the target bean does not will silently be ignored.
     * <p>This is just a convenience method. For more complex transfer needs,
     * consider using a full BeanWrapper.
     *
     * @param source   the source bean
     * @param target   the target bean
     * @param editable the class (or interface) to restrict property setting to
     * @throws BeansException if the copying failed
     * @see BeanWrapper
     */
    public static void copyPropertiesIfHasSource(Object source, Object target, Class<?> editable) throws BeansException {
        if (source == null) {
            return;
        }
        BeanUtils.copyProperties(source, target, editable);
    }

    /**
     * Copy the property values of the given source bean into the given target bean,
     * ignoring the given "ignoreProperties".
     * <p>Note: The source and target classes do not have to match or even be derived
     * from each other, as long as the properties match. Any bean properties that the
     * source bean exposes but the target bean does not will silently be ignored.
     * <p>This is just a convenience method. For more complex transfer needs,
     * consider using a full BeanWrapper.
     *
     * @param source           the source bean
     * @param target           the target bean
     * @param ignoreProperties array of property names to ignore
     * @throws BeansException if the copying failed
     * @see BeanWrapper
     */
    public static void copyPropertiesIfHasSource(Object source, Object target, String... ignoreProperties) throws BeansException {
        if (source == null) {
            return;
        }
        BeanUtils.copyProperties(source, target, ignoreProperties);
    }
}
