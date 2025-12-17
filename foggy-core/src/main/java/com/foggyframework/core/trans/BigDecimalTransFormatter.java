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

public class BigDecimalTransFormatter extends TransFormatterSupport implements ObjectTransFormatter<BigDecimal> {
	public Object deserialize(final Object object) {
		if (object instanceof BigDecimal) {
			return object;
		} else if (object instanceof Integer) {
			return new BigDecimal((Integer) object);
		} else if (object instanceof Number) {
			return new BigDecimal(object.toString());
		} else if (object != null) {
			final String str = object.toString();
			return str.length() > 0 ? new BigDecimal(str) : null;
		}
		return null;
	}

	@Override
	public BigDecimal format(final Object object) {
		// if (object instanceof String) {
		// final String str = (String) object;
		// return str.length() > 0 ? new BigDecimal(str) : null;
		// }
		// return (BigDecimal) object;
		if (object instanceof BigDecimal) {
			return (BigDecimal) object;
		} else if (object instanceof Integer) {
			return new BigDecimal((Integer) object);
		} else if (object instanceof Number) {
			return new BigDecimal(object.toString());
		} else if (object != null) {
			final String str = object.toString();
			return str.length() > 0 ? new BigDecimal(str) : null;
		}
		return null;
	}

	@Override
	public Class<BigDecimal> type() {
		return BigDecimal.class;
	}

}
