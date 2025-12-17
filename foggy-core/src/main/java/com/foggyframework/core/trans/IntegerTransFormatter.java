/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core.trans;

public class IntegerTransFormatter implements ObjectTransFormatter<Integer> {

	public Object deserialize(final Object object) {

		if (object instanceof Integer) {
			return object;
		} else if (object instanceof Number) {
			return ((Number) object).intValue();
		} else if (object != null) {
			final String str = object.toString();
			return str.length() > 0 ? Integer.valueOf((String) object) : null;
		}
		return null;
	}

	@Override
	public Integer format(final Object object) {
		if (object instanceof Number) {
			return ((Number) object).intValue();
		} else if (object instanceof String str) {
            if(str.length()==1 && str.contentEquals("-")) {
				return -0;
			}
			return str.length() > 0 ? Double.valueOf((String) object).intValue() : null;
		} else if (object instanceof Boolean) {
			return ((Boolean) object).booleanValue() ? 1 : 0;
		}
		return (Integer) object;
	}

	public String getName() {
		return "INTEGER";
	}

	public final boolean isEmpty(final Object value) {
		return value == null;
	}

	public int length() {
		return 8;
	}

	@Override
	public Class<Integer> type() {
		return Integer.class;
	}

}