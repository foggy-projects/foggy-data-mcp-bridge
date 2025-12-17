/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core.utils.beanhelper;

import com.foggyframework.core.ex.ExRuntimeExceptionImpl;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.core.utils.ErrorUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;

/**
 * java.lang.reflect.Field 代理 应当还可以代理Method
 *
 * @author seasoul
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@Slf4j
abstract class ClassItemHelper {

    BeanInfoHelper entityClass;

    AccessibleObject item;

    protected Class type;

    protected Method writerMehod;
    protected Method readerMethod;

    protected ObjectTransFormatter<?> formatter;

    ClassItemHelper(final BeanInfoHelper entityClass, final AccessibleObject f) {
        this.entityClass = entityClass;
        item = f;
    }

    /**
     * 断言,getMethod,setMethod不允许为空
     */
    public void assertInjectMethodNotNull() {
        if (writerMehod == null || readerMethod == null) {
            throw new RuntimeException("无法找到 Item:" + item + "的getter或setter方法,class:[" + entityClass.getClazz() + "]");
        }
    }

    public Object format(Object v) {
        if (formatter != null) {
            return formatter.format(v);
        }
        return v;
    }

    public Annotation getAnnotation(Class annotationClass) {
        return item.getAnnotation(annotationClass);
    }

    public final Object getBeanValue(final Object bean) {
        try {

            return readerMethod.invoke(bean);
        } catch (NullPointerException e) {
            if (bean == null) {
                throw new RuntimeException(this + "传入对像为空！", e);
            }
            throw new RuntimeException("读取bean:[" + bean + "]的属性:[" + (item == null ? readerMethod : item) + "]未定义readerMethod方法。", e);
        } catch (IllegalAccessException x) {
            /*****************************************************************/
            /**
             * 呃,为什么会有这种情况?假设一个类ClassA的私有的,但它实现了InterfaceB的方法M1 好吧,如果直接使用ClassA的M1方法
             * ,会报这个异常,但找到InterfaceB,再使用InterfaceB的M1方法,就不会出现异常... ...
             */
            for (Class cls : bean.getClass().getInterfaces()) {
                try {
                    Method m = cls.getMethod(readerMethod.getName());
                    return m.invoke(bean);
                } catch (Throwable e) {
                    log.warn("getMethod: " + e.getMessage());
                }
            }
            /*****************************************************************/
            throw RX.throwB("读取bean:[" + bean + "]的属性:[" + (item == null ? readerMethod : item) + "]时出现异常。", x);
        } catch (final Exception e) {
            String msg = "读取bean:[" + bean + "]的属性:[" + (item == null ? readerMethod : item) + "]时出现异常。" + ErrorUtils.getMessage(e);
            log.error(msg);
//			if(e instanceof ExRuntimeExceptionImpl){
//				throw (ExRuntimeExceptionImpl)e;
//			}
            ErrorUtils.throwIfExRuntimeExceptionImpl(e);
            // e.printStackTrace();
            throw RX.throwB("读取bean:[" + bean + "]的属性:[" + (item == null ? readerMethod : item) + "]时出现异常。" + ErrorUtils.getMessage(e), ErrorUtils.toRuntimeException(e));
        }
    }


    public String getCaption() {
        return getName();
    }

    public Field getField() {
        return null;
    }

    public abstract String getItemName();

    public abstract String getName();

    public Method getReaderMethod() {
        return readerMethod;
    }

    // public final String getName() {
    // return field.getName();
    // }
    //
    public Class getType() {
        return type;
    }

    public Method getWriterMehod() {
        return writerMehod;
    }

    public boolean hasAnnotation(final Class annotationClass) {
        return item.getAnnotation(annotationClass) != null;
    }

    // public String getCaption() {
    // return field.getName();
    // }

    public final boolean isArray() {
        return type.isArray();
    }

    public boolean isBoolean() {
        return type == Boolean.class || type == boolean.class;

    }

    public final boolean isDate() {
        return type == Date.class;
    }

    public final boolean isDouble() {
        return type == Double.class || type == double.class;
    }

    public final boolean isFile() {
        return type == File.class;
    }

    public final boolean isFloat() {
        return type == Float.class || type == float.class;
    }

    public final boolean isInteger() {
        return type == Integer.class || type == int.class;
    }

    public final boolean isNumber() {
        return isInteger() || isFloat() || isDouble();
    }

    public final boolean isString() {
        return type == String.class;
    }

    public final boolean isStringArray() {
        return type == String[].class;
    }

    /**
     * isTransient==true表示不需要序列化
     *
     * @return
     */
    public boolean isTransient() {
        return false;
    }

    public final void setBeanValue(final Object bean, Object value) {
        setBeanValue(bean, value, true);
    }

    //
    public final void setBeanValue(final Object bean, Object value, boolean errorIfFaild) {
        // 待重构,寻找ObjectTransFormatter的干活!
        // 不是所有的propertyClass都有ObjectTransFormatter
        if (formatter != null) {
            try {
                value = formatter.format(value);
            } catch (Throwable t) {
                throw new RuntimeException("setBeanValue时出现异常:" + getName() + "," + bean + "," + value, t);
            }
        }

        if (writerMehod == null) {
            // 尝试通过Field注入
            Field f = getField();
            if (f != null) {
                f.setAccessible(true);
                try {
                    f.set(bean, value);
                    return;
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    throw ErrorUtils.toRuntimeException(e);// e.printStackTrace();
                }
            }

            if (errorIfFaild) {
                throw new RuntimeException(
                        "writer method not found for property :[" + getItemName() + "] in class : " + bean.getClass());
            } else {
                return;
            }
        }
        try {

            writerMehod.invoke(bean, value);
        } catch (IllegalArgumentException e) {
            if (errorIfFaild) {
                throw new RuntimeException(" call setter [" + writerMehod + "] has error ", e);
            }
        } catch (InvocationTargetException e) {
            if (errorIfFaild) {
                log.error(" call setter [" + writerMehod + "] has error :" + e.getMessage());
                throw ErrorUtils.toRuntimeException(e.getTargetException());
            }
        } catch (final Throwable e) {
            if (errorIfFaild) {
                if (e instanceof RuntimeException) {
                    log.error(" call setter [" + writerMehod + "] has error :" + e.getMessage());
                    throw (RuntimeException) e;
                } else {
                    throw new RuntimeException(" call setter [" + writerMehod + "] has error ", e);
                }
            }
        }
    }

//	public void setBeanValueFromRequest(Object result, Request request) {
//		// 考虑分解成switch
//		if (getType().isArray()) {
//			String[] xx = request.getParameters(getName());
//			setBeanValue(result, xx);
//		} else if (getType() == List.class) {
//			String[] xx = request.getParameters(getName());
//			setBeanValue(result, xx == null ? null : Arrays.asList(xx));
//		} else {
//			Object pr = request.getParameter(getName());
//			if (!StringUtils.isEmpty(pr)) {
//				setBeanValue(result, pr);
//			}
//		}
//	}

    @Override
    public String toString() {
        return "ClassItemHelper [entityClass=" + entityClass + ", writerMehod="
                + (writerMehod == null ? "" : writerMehod.getName()) + ", readerMethod="
                + (readerMethod == null ? "" : readerMethod.getName()) + "]";
    }
}
