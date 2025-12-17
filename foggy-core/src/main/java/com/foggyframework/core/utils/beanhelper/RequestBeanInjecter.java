/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core.utils.beanhelper;

import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.core.utils.ErrorUtils;
import com.foggyframework.core.utils.StringUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class RequestBeanInjecter {

	class BeanInjecterImpl implements BeanInjecter {

		BeanInfoHelper classHelper;

		public BeanInjecterImpl(Class<?> clazz) {
			classHelper = BeanInfoHelper.getClassHelper(clazz);

		}

		public void applyValue(Object bean, String name, Object value) {
			BeanProperty item = classHelper.getBeanProperty(name);
			if (item == null) {
				throw new RuntimeException("property [" + name + "] not found in beanClass : " + bean.getClass());
			}
			ObjectTransFormatter<?> dt = getObjectTransFormatter(item.getType());
			item.setBeanValue(bean, dt.format(value), false);
		}

//		@Override
//		public Object inject(Object bean, HttpServletRequest request) {
//			if (request != null) {
//				for (BeanProperty item : classHelper.getWriteMethods()) {
//					ObjectTransFormatter<?> dt = getObjectTransFormatter(item.getType());
//					if (dt == null) {
//						/**
//						 */
//					} else {
//						Object obj = request.getAttribute(item.getName());
//						if (obj == null) {
//							obj = request.getParameter(item.getName());
//						}
//						if (obj != null)
//							item.setBeanValue(bean, dt.format(obj), false);
//					}
//				}
//			} else {
//				// ?
//			}
//			return bean;
//		}
	}

	public static final String NAME = "/requestBeanInjecter";

	static {

	}

	private static final RequestBeanInjecter instance = new RequestBeanInjecter();

	public static final RequestBeanInjecter getInstance() {
		return instance;
	}

	private final Map<Class<?>, ObjectTransFormatter<?>> classToTransFormatterMap = new HashMap<Class<?>, ObjectTransFormatter<?>>();

	private final Map<Class<?>, BeanInjecter> classToBeanInjecter = new HashMap<Class<?>, BeanInjecter>();

	public RequestBeanInjecter() {

		regedit(ObjectTransFormatter.BIGDECIMAL_TRANSFORMATTERINSTANCE);
		regedit(ObjectTransFormatter.DATE_TRANSFORMATTERINSTANCE);
		regedit(ObjectTransFormatter.DOUBLE_TRANSFORMATTERINSTANCE);
		regedit(ObjectTransFormatter.FLOAT_TRANSFORMATTERINSTANCE);
		regedit(ObjectTransFormatter.INTEGER_TRANSFORMATTERINSTANCE);
		regedit(ObjectTransFormatter.NUMBER_TRANSFORMATTERINSTANCE);
		regedit(ObjectTransFormatter.SQL_DATE_TRANSFORMATTERINSTANCE);
		regedit(ObjectTransFormatter.STRING_TRANSFORMATTERINSTANCE);
		regedit(ObjectTransFormatter.BOOLEAN_TRANSFORMATTERINSTANCE);
		regedit(ObjectTransFormatter.BYTE_TRANSFORMATTERINSTANCE);
		regedit(ObjectTransFormatter.LONG_TRANSFORMATTERINSTANCE);
		regedit(boolean.class, ObjectTransFormatter.SBOOLEAN_TRANSFORMATTERINSTANCE);
		regedit(int.class, new ObjectTransFormatter.SIntegerTransFormatter());
		regedit(double.class, new ObjectTransFormatter.SDoubleTransFormatter());
		regedit(float.class, new ObjectTransFormatter.SFloatTransFormatter());

		classToTransFormatterMap.put(int.class, new ObjectTransFormatter<Object>() {

			@Override
			public Object format(Object object) {
				if (StringUtils.isEmpty(object)) {
					return 0;
				}
				return ObjectTransFormatter.INTEGER_TRANSFORMATTERINSTANCE.format(object);
			}

			@Override
			public Class<?> type() {
				return int.class;
			}

		});
		classToTransFormatterMap.put(long.class, new ObjectTransFormatter<Object>() {

			@Override
			public Object format(Object object) {
				if (StringUtils.isEmpty(object)) {
					return (long) 0;
				}
				return ObjectTransFormatter.LONG_TRANSFORMATTERINSTANCE.format(object);
			}

			@Override
			public Class<?> type() {
				return long.class;
			}

		});

		classToTransFormatterMap.put(Class.class, new ObjectTransFormatter<Object>() {

			@Override
			public Object format(Object object) {
				if (object instanceof Class) {
					return object;
				}
				if (StringUtils.isEmpty(object)) {
					return Object.class;
				}

				try {
					return Class.forName(object.toString());
				} catch (ClassNotFoundException e) {
					throw ErrorUtils.toRuntimeException(e);
				}
			}

			@Override
			public Class<?> type() {
				return Class.class;
			}

		});

		classToTransFormatterMap.put(boolean.class, ObjectTransFormatter.BOOLEAN_TRANSFORMATTERINSTANCE);

	}

	public BeanInjecter getBeanInjecter(Class<?> cls) {
		if (classToBeanInjecter.containsKey(cls)) {
			return classToBeanInjecter.get(cls);
		}
		synchronized (cls) {
			if (classToBeanInjecter.containsKey(cls)) {
				return classToBeanInjecter.get(cls);
			}
			BeanInjecter bi = new BeanInjecterImpl(cls);
			classToBeanInjecter.put(cls, bi);
			return bi;
		}
	}

	public ObjectTransFormatter<?> getObjectTransFormatter(Class<?> type) {
		return classToTransFormatterMap.get(type);
	}

	// public static void inject(final Object source, final Request request) {
	// RequestBeanInjecter rbi = null;
	// final Class cls = source.getClass();
	// synchronized (cls) {
	// rbi = getRequestBeanInjecter();
	//
	// }
	// rbi.injectObject(source, request);
	// }

	// public static RequestBeanInjecter getRequestBeanInjecter() {
	// return (RequestBeanInjecter) BKTFramework.lookupObject(NAME);
	// }

//	public void injectObject(final Object source, final HttpServletRequest request) {
//		getBeanInjecter(source.getClass()).inject(source, request);
//	}

	public void regedit(Class cls, ObjectTransFormatter<?> formatter) {
		try {
			classToTransFormatterMap.put(cls, formatter);
		} catch (SecurityException e) {
			throw ErrorUtils.toRuntimeException(e);
		}
	}

	public void regedit(ObjectTransFormatter<?> formatter) {
		try {
			Method m = formatter.getClass().getMethod("format", Object.class);
			regedit(m.getReturnType(), formatter);
		} catch (SecurityException e) {
			throw ErrorUtils.toRuntimeException(e);
		} catch (NoSuchMethodException e) {
			throw ErrorUtils.toRuntimeException(e);
		}
	}

}
