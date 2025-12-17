/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core.trans;

public class NumberTransFormatter implements ObjectTransFormatter<Number> {
	public Object deserialize(final Object object) {
		return object;
	}

	@Override
	public Number format(final Object object) {
		if (object instanceof String str) {
            return str.length() > 0 ? Double.valueOf((String) object) : null;
		}
		return (Number) object;
	}

	public String getName() {
		return "NUMBER";
	}

	public final boolean isEmpty(final Object value) {
		return value == null;
	}

	public int length() {
		return 8;
	}

	@Override
	public Class<?> type() {
		return Number.class;
	}
}
