/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core.utils;



public class UuidUtils {

	public static void main(String[] args) {
		long start = System.currentTimeMillis();
		System.err.println(newUuid());
		for (long i = 0; i != Long.MAX_VALUE; i++) {

		}
		System.err.println(System.currentTimeMillis() - start);
		System.err.println(Integer.MAX_VALUE);
	}
	public static final String getUUID() {
		final String str = java.util.UUID.randomUUID().toString();
		final StringBuilder builder = new StringBuilder(32);
		for (int i = 0; i < str.length(); i++) {
			if (str.charAt(i) != '-') {
				builder.append(str.charAt(i));
			}
		}
		return builder.toString();
	}
	public static final String newUuid() {
		return getUUID();
	}

//	@Deprecated
//	public static final String newUuid(String x) {
//		return x + (RequestUtils.getCurrentSession().increase());
//		// synchronized (key) {
//		// return x + start++;
//		// }
//	}

}
