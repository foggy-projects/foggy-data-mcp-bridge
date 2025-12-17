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
import com.foggyframework.dataset.resultset.query.ResultSetIndex;
import com.foggyframework.dataset.resultset.query.ResultSetQuery;
import com.foggyframework.dataset.resultset.query.SelectColumn;
import com.foggyframework.fsscript.parser.spi.PropertyHolder;
import com.foggyframework.fsscript.parser.spi.SubHolder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * 
 * @author foggy
 * @since 1.0
 * @version 1.1
 */
public interface ListResultSet<T> extends /** FoggyEventMulticaster, */
		ResultSet, Decorate, SubHolder {

	 enum ResultSetType {
		LIST, TREE, GROUP
	}

	  int AFTER = 0;

	  int BEFORE = 1;

	/**
	 * 向当前游标所在的节点新增兄弟节点
	 */
	 int NEXT_SIBLING = 2;

	/**
	 * 向当游标所在的节点新增子节点
	 */
	 int NEXT_DEPTH = 3;

	boolean absoluteById(int id) throws SQLException;

	void commit() throws SQLException;

	ResultSetQuery createQuery() throws SQLException;

	PropertyHolder getColumns();

	Record<T> getFirstRecord() throws SQLException;

	@Override
	ListResultSetMetaData<T> getMetaData() throws SQLException;

	/**
	 * 获取当前记录的Record对象
	 * 
	 * @return
	 * @since v1.1
	 */
	Record<T> getRecord() throws SQLException;

	/**
	 * 
	 * @param id
	 * @return
	 * @throws SQLException
	 * @since v1.1
	 */
	Record<T> getRecordById(int id) throws SQLException;

	/**
	 * 
	 * @return
	 * @throws SQLException
	 * @since v1.1
	 */
	int getRecordId() throws SQLException;

	List<Record<T>> getRecords();

	Object getRowValue() throws SQLException;

	SelectColumn getSelectColumn(String string);

	List<SelectColumn> getSelectColumns();

	/**
	 * 对指定列创建索引
	 * 
	 * @param unique
	 * @return
	 * @throws SQLException
	 * @since v1.2
	 */
	ResultSetIndex index(boolean unique, int indexType, Object... columns) throws SQLException;

	ResultSetIndex index(Object columns) throws SQLException;

	ResultSetIndex indexByArray(Object... columns) throws SQLException;

	/**
	 * 将指定的记录插入当前结果集
	 * 
	 * @param rec
	 * @param pos
	 * @return
	 * @throws SQLException
	 */
	Record<T> insertRecord(Record<T> rec, int pos) throws SQLException;

	boolean isEmpty();

	/**
	 * 判断给写的列是否唯一
	 * 
	 * @param column
	 * @return
	 * @since v1.2
	 */
	boolean isUnique(String column);

	/**
	 * 
	 * 创建一条新的记录，但不加入当前的结果集中
	 * 
	 * @return
	 * @since v1.1
	 */
	Record<T> newRecord() throws SQLException;

	ListResultSet<T> query(Map<String, Object> args) throws SQLException;

	 Record<T> queryFirstByColumn(String name, Object value) throws SQLException;

	default PagingResultSet getPagingResultSet() {
		return getDecorate(PagingResultSet.class);
	}
	
}
