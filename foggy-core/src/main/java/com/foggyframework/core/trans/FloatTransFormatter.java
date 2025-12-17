/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core.trans;

import java.math.BigDecimal;

public class FloatTransFormatter implements ObjectTransFormatter<Float> {

	public Object deserialize(final Object object) {
		if (object instanceof Integer) {
			return object;
		} else if (object instanceof Number) {
			return ((Number) object).floatValue();
		} else if (object != null) {
			final String str = object.toString();
			return str.length() > 0 ? Float.valueOf((String) object) : null;
		}
		return null;
	}

	@Override
	public Float format(final Object object) {
		if (object instanceof String str) {
            return str.length() > 0 ? Float.valueOf((String) object) : null;
		}else if (object instanceof Integer v) {
			return v.floatValue();
		}else if (object instanceof BigDecimal v) {
			return v.floatValue();
		}
		return (Float) object;
	}

	public String getName() {
		return "FLOAT";
	}

	public final boolean isEmpty(final Object value) {
		return value == null;
	}

	public int length() {
		return 8;
	}

	@Override
	public Class<Float> type() {
		return Float.class;
	}

}
