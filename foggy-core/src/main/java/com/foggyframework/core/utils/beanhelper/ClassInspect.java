/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core.utils.beanhelper;


import com.foggyframework.core.utils.ErrorUtils;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class ClassInspect {

	private static final List<Class> pojoPropertyList = new ArrayList<Class>();
	static {
		ClassInspect.pojoPropertyList.add(String.class);
		ClassInspect.pojoPropertyList.add(Integer.class);
		ClassInspect.pojoPropertyList.add(boolean.class);
		ClassInspect.pojoPropertyList.add(Boolean.class);
	}

	public static String field2GetterMethodName(final String fieldName) {
		return "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
	}

	public static String field2SetterMethodName(final String fieldName) {
		return "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
	}

	/**
	 * 如果未找到，尝试从cls的父类中寻找。。
	 * 
	 * @param cls
	 * @param anncls
	 * @return
	 */
	public final static Annotation getAnnotation(final Class cls, final Class anncls) {
		if (cls == null) {
			return null;
		}
		final Annotation r = cls.getAnnotation(anncls);
		if (r == null) {
			return ClassInspect.getAnnotation(cls.getSuperclass(), anncls);
		}
		// 也许应当尝试从其实现的接口中查找
		return r;
	}

	@Deprecated
	public final static ClassLoader getCassLoader() {
		final ClassLoader loader = Thread.currentThread().getContextClassLoader();
		if (loader != null) {
			return loader;
		} else {
			return ClassLoader.getSystemClassLoader();
		}
	}

	@Deprecated
	public final static Class getClass(final String name) {
		try {
			final ClassLoader loader = Thread.currentThread().getContextClassLoader();
			if (loader != null) {
				return loader.loadClass(name);
			}
			return Class.forName(name);
		} catch (final Exception e) {
			throw ErrorUtils.toRuntimeException(e);
		}
	}

	/**
	 * 得到一个类的所有属性，包括其所继承的属性
	 */
	public static Field[] getClassFields(final Class clazz) {
		final List<Field> fieldList = new ArrayList<Field>();
		if (clazz == null) {
			// TODO
			log.error("参数不应该为空!!");
		}
		Class c = clazz;

		while (null != c) {
			final List<Field> l = Arrays.asList(c.getDeclaredFields());
			fieldList.addAll(l);
			c = c.getSuperclass();
		}
		return fieldList.toArray(new Field[] {});
	}

	/**
	 * 尝试在类clazz中查找方法名为methodName及参数类型为type的方法
	 * 第一如未找到,将依次尝试使用type的parent及其所实现的接口来查找
	 * 
	 * @param clazz
	 * @param methodName
	 * @param type
	 * @param useCache
	 * @return
	 */
	public static Method getMethod(final Class clazz, final String methodName, final Class type,
			final boolean useCache) {
		Method method = null;
		Class argType = type;// .getSuperclass();
		while (argType != null) {
			try {
				method = clazz.getMethod(methodName, argType);
				return method;
			} catch (final SecurityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (final NoSuchMethodException e) {
				argType = argType.getSuperclass();
			}
		}
        // 尝试使用接口寻找
		if (type != null)
			for (final Class cls : type.getInterfaces()) {
				try {
					method = clazz.getMethod(methodName, cls);
					return method;
				} catch (final Exception e) {
				}
			}
		// 尝试遍历所有的方法
		for (Method m : clazz.getMethods()) {
			if (m.getName().equals(methodName)) {
				if (m.getParameterTypes().length == 1) {
					return m;
				}
			}
		}
		return method;
	}

	public static boolean isPojoProperty(final Class clazz) {
		return ClassInspect.pojoPropertyList.contains(clazz);
	}

	public final static Object newInstance(final String name) {
		try {
			final ClassLoader loader = Thread.currentThread().getContextClassLoader();
			if (loader != null) {
				return loader.loadClass(name).newInstance();
			}
			return Class.forName(name).newInstance();
		} catch (final Exception e) {
			throw ErrorUtils.toRuntimeException(e);
		}
	}
}
