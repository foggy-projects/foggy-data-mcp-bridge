/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.dataset.model;

import com.foggyframework.dataset.model.support.PagingObjectImpl;

public interface PagingObject {

	 PagingObject DEFAULT_PAGING_OBJECT = new PagingObjectImpl(0, 10);

	int getLimit();

	int getStart();
}
