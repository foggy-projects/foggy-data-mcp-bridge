package com.foggyframework.dataset.model.support;


import com.foggyframework.dataset.model.PagingObject;

public class PagingObjectImpl implements PagingObject {

	protected int start;
	protected int limit;

	public PagingObjectImpl() {
		this(0, 6);
	}

	public PagingObjectImpl(int start, int limit) {
		super();
		this.start = start;
		this.limit = limit;
	}

	@Override
	public int getLimit() {
		return limit;
	}

	@Override
	public int getStart() {
		return start;
	}

}
