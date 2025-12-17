/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core;

import java.io.Serializable;

public abstract class AbstractDelegateDecorate<T extends Decorate> extends AbstractDecorate
		implements Decorate, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6384885723472092574L;
	public T delegate;

	public AbstractDelegateDecorate(T delegate) {
		super();
		// if (delegate == null) {
		// throw new RuntimeException("delegate object can't be null!");
		// }
		this.delegate = delegate;
	}

	@Override
	public <C> C getDecorate(Class<C> cls) {
		if (cls.isInstance(this)) {
			return cls.cast(this);
		} else if (delegate != null) {
			return delegate.getDecorate(cls);
		} else {
			return null;
		}
	}

	public T getDelegate() {
		return delegate;
	}

	@Override
	public Object getRoot() {
		return delegate.getRoot();
	}

	@Override
	public boolean isInDecorate(Object obj) {
		if (super.isInDecorate(obj)) {
			return true;
		}
		return delegate.isInDecorate(obj);
	}

	public void setDelegate(T delegate) {
		this.delegate = delegate;
	}
}
