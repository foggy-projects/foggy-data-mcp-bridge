/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.dataset.model;

import java.util.List;

public interface PagingResult<T> {

    int getLimit();

    int getStart();

    long getTotal();

    boolean hasNextPage();

    boolean hasPreviousPage();

    List<T> getItems();

    void setItems(List<T> items);

    boolean isEmpty();

    void setLimit(int limit);

    void setStart(int start);

    void setTotal(long total);
}
