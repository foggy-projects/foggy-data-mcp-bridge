/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core.trans;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

/**
 * 
 * @author seasoul
 * 
 */
public class SqlDateTransFormatter implements ObjectTransFormatter<Date> {
	// private static final transient Logger logger = Logger
	// .getLogger(SqlDateTransFormatter.class);
	// private final SimpleDateFormat format01 = new
	// SimpleDateFormat("yyyy-MM-dd");
	// private final SimpleDateFormat format02 = new
	// SimpleDateFormat("yyyy-mm-dd");
	// private final SimpleDateFormat format03 = new
	// SimpleDateFormat("yyyyMMdd");
	// private final SimpleDateFormat format04 = new SimpleDateFormat(
	// "yyyy-MM-dd HH:mm:ss");

	public static void main(final String[] args) {
		System.out.println(pattern01.matcher("08-1-01").find());
		// // Systemx.out.println(format03.format(new Date()));
	}

	public Object deserialize(final Object object) {
		final SimpleDateFormat format01 = new SimpleDateFormat("yyyy-MM-dd");
		if (object instanceof Date date) {
            // logger.error("serialize java.util.Date : "+obj+" to
			// java.sql.Date ");
			// format01.f
			return format01.format(new java.util.Date(date.getTime()));
		} else if (object instanceof Timestamp time) {
            return format01.format(new java.util.Date(time.getTime()));
		} else if (object instanceof Time time) {
            return format01.format(new java.util.Date(time.getTime()));
		} else {
			return object;
		}
	}

	@Override
	public Date format(final Object obj) {
		try {
			if (obj == null) {
				return null;
			} else if (obj instanceof Date) {
				return (Date) obj;
			} else if (obj instanceof java.util.Date) {
				return new Date(((java.util.Date) obj).getTime());
			} else if (obj instanceof String str) {
                if (StringUtils.isTrimEmpty(str)) {
					return null;
				}
				if (pattern03.matcher(str).find()) {
					return new Date(new SimpleDateFormat("yyyyMMdd").parse(str).getTime());
				} else if (pattern04.matcher(str).find()) {
					return new Date(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(str).getTime());

				} else if (pattern01.matcher(str).find()) {
					final java.util.Date r = new SimpleDateFormat("yyyy-MM-dd").parse(str);
					return new Date(r.getTime());
				} else if (pattern02.matcher(str).find()) {
					return new Date(new SimpleDateFormat("yyyy-mm-dd").parse(str).getTime());
				}
			}
		} catch (final Exception e) {
			throw RX.throwB(e.getMessage(),null,e);
		}
		throw new RuntimeException("can't format [" + obj + "] to SqlDate");
	}

	public String getName() {
		return "DATE";
	}

	public final boolean isEmpty(final Object value) {
		if (value == null){
			return true;
		}

		else if (value instanceof String) {
			return ((String) value).length() == 0;
		} else {
			return false;
		}
	}

	public int length() {
		return ObjectTransFormatter.UNKNOW_DATALENGTH;
	}

	@Override
	public Class<?> type() {
		return Date.class;
	}

}
