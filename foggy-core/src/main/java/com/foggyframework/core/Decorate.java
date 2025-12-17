/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core;

/**
 * 
 * top buttom A |--B |--C |--root
 * 这是个单向链表结构,ROOT无法取得A...是否考虑在root上加个变量,指向A?即在Decorate添加方法 : getTop...()
 * 
 * 问题是。C中如何得到A？调方法时，带个上下文，由上下文来维护？ 这个问题主要体现在：事件与Command两处。。
 * 
 * @author Foggy
 * 
 */
public interface Decorate {
	<T> T getDecorate(Class<T> cls);

	Object getRoot();

	boolean isInDecorate(Object obj);
}
