/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.dataset.resultset;


import com.foggyframework.core.Decorate;
import com.foggyframework.dataset.resultset.query.SelectColumn;
import com.foggyframework.fsscript.parser.spi.SubHolder;

import java.sql.SQLException;

/**
 * ListResultSet与FormComponent将通过Record进行关联
 * 
 * @author foggy
 * 
 * @param <T>
 */
public interface Record<T> extends Decorate, Cloneable, SubHolder {
	public class XX implements Record {


		@Override
		public void apply(Object obj) {
			// TODO Auto-generated method stub

		}

		@Override
		public void beginEdit() {
			// TODO Auto-generated method stub

		}

		@Override
		public void cancelEdit() throws SQLException {
			// TODO Auto-generated method stub

		}

		@Override
		public boolean canSet(String name) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void commit() throws SQLException {
			// TODO Auto-generated method stub

		}

		@Override
		public void delete() {
			// TODO Auto-generated method stub

		}

		@Override
		public void endEdit() throws SQLException {
			// TODO Auto-generated method stub

		}


		@Override
		public <T> T getDecorate(Class<T> cls) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int getId() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public ListResultSetMetaData getMetaData() {
			return null;
		}

		@Override
		public Object getObject(int index) throws SQLException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Object getObject(SelectColumn sc) throws SQLException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Object getObject(String columnName) throws SQLException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Object getRoot() {
			return this;
		}

		@Override
		public Object getSubObject(int intValue) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Object getSubObject(String name) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Object getValue() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public boolean isCollapsed() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isDeleted() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isInDecorate(Object obj) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isLeaf() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isModified(int i) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isNew() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isUpdated() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean readOnly() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void set(int index, Object v) throws SQLException {
			// TODO Auto-generated method stub

		}

		@Override
		public void set(SelectColumn sc, Object object) throws SQLException {
			// TODO Auto-generated method stub

		}

		@Override
		public void set(String columnName, Object v) throws SQLException {
			// TODO Auto-generated method stub

		}

		@Override
		public void set(String columnName, Object v, boolean errorIfNotFound) throws SQLException {
			// TODO Auto-generated method stub

		}

		@Override
		public void setValue(Object t) {
			// TODO Auto-generated method stub

		}

		@Override
		public String toJson() {
			// TODO Auto-generated method stub
			return null;
		}

	}

	public static final Record<?> EMPTY = new XX();

	/**
	 * 该记录标记为删除
	 */
	public static final int DELETE = 0x01;
	/**
	 * 该记录标记为更新
	 */
	public static final int UPDATE = 0x02;
	/**
	 * 该记录标记为新建
	 */
	public static final int NEW = 0x04;
	/**
	 * 该记录标记为正在编辑 ,一般处于此标记的记录,set时不会发出事件
	 */
	public static final int EDITING = 0x08;
	/**
	 * 该记录标记为叶子节点,用于TreeResultSet
	 */
	public static final int LEAF = 0x10;

	/**
	 * 该记录标记为新建，游离状态(比如调用ResultSet.newRecord产生的记录)
	 */
	public static final int XNEW = 0x20;

	/**
	 * 将传入的值合并到当前Record，支持Map、普通Bean、Record。 apply会自行调用 beginEdit及endEditor
	 * 
	 * @param obj
	 * @throws SQLException
	 */
	void apply(Object obj) throws SQLException;

	/**
	 * 只有调用beginEdit之后，使用set方法才会发出ResultSetChangeEvent事件
	 */
	void beginEdit();

	/**
	 * 如果是一个新增的记录，则删除此记录 如果是一个处于更新状态的记录，则还原更新
	 * 
	 * @throws SQLException
	 */
	void cancelEdit() throws SQLException;

	/**
	 * 判断是否可以执行set方法,以避免调用set时发生异常
	 * 
	 * @param name
	 * @return
	 */
	boolean canSet(String name);

	void commit() throws SQLException;

	void delete();

	/**
	 * 如果有修改的话触发ResultSetChangeEvent事件
	 * 
	 * @throws SQLException
	 */
	void endEdit() throws SQLException;

	int getId();

	ListResultSetMetaData<T> getMetaData();

	/**
	 * @param index start 1
	 * @return
	 * @throws SQLException
	 */
	Object getObject(int index) throws SQLException;

	// ListResultSet<T> getResultSet();

	// int getState();

	Object getObject(SelectColumn sc) throws SQLException;

	// boolean hasMask(int state);

	/**
	 * 
	 * @param columnName 与ResultSet提供的columnName一致
	 * @return
	 */
	Object getObject(String columnName) throws SQLException;

	/**
	 * 如果ResultSet设置了beanClass,则返回此类的实例 如果ResultSet未设置beanClass,则返回自身
	 * 
	 * 重要,如果该Record处理被编辑但未Commit,getValue目前返回的将是原始的未被修改的记录
	 * 
	 * @return
	 */
	T getValue();

	boolean isCollapsed();

	boolean isDeleted();

	boolean isLeaf();

	boolean isModified(int i);

	// void mask(int state);

	boolean isNew();

	boolean isUpdated();

	/**
	 * 一般情况下为只读,仅
	 * org.foggysource.framework.fui.olap.model.support.EditResultModelSupport.
	 * EditResultSetSupport.EditRecordImpl为可写的,
	 * 事实上,即使readOnly为真,您也可以往其中写值,但是,调用commit将无法触发保存,主要是未指定dataWriter
	 * 
	 * @return
	 */
	boolean readOnly();

	/**
	 * start with 1
	 * 
	 * @param index
	 * @param v
	 * @throws SQLException
	 */
	void set(int index, Object v) throws SQLException;

	void set(SelectColumn sc, Object object) throws SQLException;

	// void setState(int i);

	void set(String columnName, Object v) throws SQLException;

	void set(String columnName, Object v, boolean errorIfNotFound) throws SQLException;

	void setValue(T t);

	String toJson();
	// void unMask(int state);

}
