/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core;

public class AbstractDecorate implements Decorate {

	@Override
	public <T> T getDecorate(Class<T> cls) {
		if (cls.isInstance(this)) {
			return cls.cast(this);
		} else {
			return null;
		}
	}

	@Override
//	@Transient
//	@com.fasterxml.jackson.annotation.JsonIgnore
	public Object getRoot() {
		return this;
	}

	@Override
	public boolean isInDecorate(Object obj) {
		return obj == this;
	}
}
