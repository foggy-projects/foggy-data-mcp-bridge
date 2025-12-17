/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core.utils.beanhelper;



import java.lang.reflect.Field;

public interface BeanProperty {

	Object format(Object v);

	<T> T getAnnotation(Class<T> annotationClass);

	Object getBeanValue(Object bean);

	Field getField();

	String getName();

	Class<?> getType();

	boolean hasReader();

	boolean hasWriter();

	Object newInstance();

	/**
	 * 呃，带自动格式化
	 * 
	 * @param bean
	 * @param value
	 */
	void setBeanValue(Object bean, Object value);

	void setBeanValue(Object bean, Object value, boolean errorIfNotFound);

	//void setBeanValueFromRequest(Object result, Request request);

//	default int getSqlType() {
//		Class c = getType();
//
//		return DbUtils.getJdbcType(c);
//	};
}
