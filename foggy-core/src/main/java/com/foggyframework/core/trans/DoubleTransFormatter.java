/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core.trans;

public class DoubleTransFormatter implements ObjectTransFormatter<Double> {

	public Object deserialize(final Object object) {
		if (object instanceof Integer) {
			return ((Integer) object).doubleValue();
		} else if (object instanceof Number) {
			return ((Number) object).doubleValue();
		} else if (object != null) {
			final String str = object.toString();
			return str.length() > 0 ? Double.valueOf((String) object) : null;
		}
		return null;
	}

	@Override
	public Double format(final Object object) {
		// if (object instanceof String) {
		// final String str = (String) object;
		// return str.length() > 0 ? Double.valueOf((String) object) : null;
		// }
		// return (Double) object;
		if (object instanceof Integer) {
			return ((Integer) object).doubleValue();
		} else if (object instanceof Number) {
			return ((Number) object).doubleValue();
		} else if (object != null) {
			final String str = object.toString();
			return str.length() > 0 ? Double.valueOf((String) object) : null;
		}
		return null;
	}

	public String getName() {
		return "DOUBLE";
	}

	public final boolean isEmpty(final Object value) {
		return value == null;
	}

	public int length() {
		return 8;
	}

	@Override
	public Class<Double> type() {
		return Double.class;
	}

}
