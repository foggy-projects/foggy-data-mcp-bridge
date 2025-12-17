/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.dataset.resultset.support;


import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.resultset.ListResultSet;
import com.foggyframework.dataset.resultset.ListResultSetMetaData;
import com.foggyframework.dataset.resultset.Record;
import com.foggyframework.dataset.resultset.query.ResultSetIndex;
import com.foggyframework.dataset.resultset.query.ResultSetQuery;
import com.foggyframework.dataset.resultset.query.ResultSetQueryImpl;
import com.foggyframework.dataset.resultset.query.SelectColumn;
import com.foggyframework.fsscript.parser.spi.PropertyHolder;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 考虑设计成final类
 * 
 * @author foggy
 * 
 * @param <T>
 */
public class ListResultSetSupport<T> extends ResultSetSupport implements ListResultSet<T> {

	protected int increase = 0;

	protected int cursor = 0;

	protected int length = 0;

	protected ListResultSetMetaData<T> meta;

	protected List<Record<T>> data = new ArrayList<Record<T>>();

	// protected FoggyEventMulticaster eventHolder = null;

	ResultSetExtension extension;

	public List<Record<T>> getItems(){
		return data;
	}
	@Override
	public Object getSubObject(int intValue) {
		return data.get(intValue);
	}

	@Override
	public Object getSubObject(String name) {
		return null;
	}

	public ListResultSetSupport() {

	}

	public ListResultSetSupport(ListResultSetMetaData<T> meta) {
		this(meta, Collections.EMPTY_LIST);
	}

	public ListResultSetSupport(ListResultSetMetaData<T> meta, List<?> data) {
		super();
		this.meta = meta;
		setData(data);
	}

	@Override
	public boolean absolute(int arg0) throws SQLException {
		if (arg0 < 0) {
			cursor = length + arg0;
		} else {
			cursor = arg0;
		}

		return cursor >= 0 || cursor < length;
	}

	@Override
	public boolean absoluteById(int id) throws SQLException {
		// TODO 需要优化
		for (int i = 0; i < length; i++) {
			if (data.get(i).getId() == id) {
				cursor = i + 1;
				return true;
			}
		}
		return false;
	}

	// @Override
	// public void addListener(Object listener) {
	// if (eventHolder == null) {
	// eventHolder = EventUtils.createEventMulticaster();
	// }
	// eventHolder.addListener(listener);
	// }

	@Override
	public void beforeFirst() throws SQLException {
		cursor = 0;

	}

	@Override
	public void commit() {
		// TODO Auto-generated method stub

	}

	@Override
	public ResultSetQuery createQuery() throws SQLException {
		return new ResultSetQueryImpl(this);
	}

	protected Record<T> createRecord(int id) throws SQLException {
		return meta.newRecord(id);
	}

	@Override
	public void deleteRow() throws SQLException {
		Record<T> rec = getRecord();
		data.remove(--cursor);
		length--;
	}

	// @Override
	// public Boolean fireEvent(EventObject eventObject) {
	// if (eventHolder == null) {
	// // eventHolder = EventUtils.createEventHolder();
	// return true;
	// }
	// return eventHolder.fireEvent(eventObject);
	// }

	@Override
	public boolean first() throws SQLException {
		cursor = 1;
		return true;
	}

	@Override
	public PropertyHolder getColumns() {
		return getExtension();
	}

	public int getCount() {
		return length;
	}

	// @Override
	// public <K> K getListener(String name, Class<K> cls) {
	// if (eventHolder != null)
	// return eventHolder.getListener(name, cls);
	// return null;
	// }

	public ResultSetExtension getExtension() {
		if (extension == null) {
			try {
				extension = new ResultSetExtension(this);
			} catch (SQLException e) {
				throw RX.throwB(e);
			}
		}
		return extension;
	}

	@Override
	public Record<T> getFirstRecord() throws SQLException {
		if (this.length < 1) {
			return null;
		}
		return getXObject(1);
	}

	@Override
	public ListResultSetMetaData<T> getMetaData() throws SQLException {
		return meta;
	}

	/**
	 * 这里是返回Xml的节点还是直接返回String 例如xpath：@value,是返回一个Att对象还是String？ 当前返回Att
	 */

	@Override
	public Object getObject(int index) throws SQLException {
		// return meta.getColumnDataAccessObject().getColumnValue(
		// getXObject(cursor), index);
		return getXObject(cursor).getObject(index);
	}

	// @Override
	// public SelectColumn getProperty(String name) {
	// return getExtension().getSelectColumn(name);
	// }

	@Override
	public Object getObject(String columnName) throws SQLException {

		return getXObject(cursor).getObject(columnName);
	}

	@Override
	public final Record<T> getRecord() {
		return getXObject(cursor);
	}

	@Override
	public Record<T> getRecordById(int id) throws SQLException {
		Record<T> rec = null;
		try {
			rec = data.get(id - 1);
			if (rec.getId() == id) {
				return rec;
			}
		} catch (RuntimeException e) {

		}

		// TODO 需要优化
		for (Record<T> r : data) {
			if (r.getId() == id) {
				return r;
			}
		}
		return null;
	}

	@Override
	public int getRecordId() throws SQLException {
		return getRecord().getId();
	}

	@Override
	public List<Record<T>> getRecords() {
		return data;
	}

	@Override
	public int getRow() throws SQLException {
		return cursor;
	}

	@Override
	public T getRowValue() throws SQLException {

		return getRowValue(cursor);
	}

	private final T getRowValue(int c) throws SQLException {

		return getXObject(c).getValue();
	}

	@Override
	public SelectColumn getSelectColumn(String string) {
		return getExtension().getSelectColumn(string);
	}

	// @Override
	// public boolean hasListener(Class<?> eventCls) {
	// if (eventHolder != null)
	// return eventHolder.hasListener(eventCls);
	// return false;
	// }

	@Override
	public List<SelectColumn> getSelectColumns() {
		return getExtension().selectColumns;
	}

	protected final Record<T> getXObject(int c) {
		try {
			return data.get(c - 1);
		} catch (ArrayIndexOutOfBoundsException e) {
			throw e;
		} catch (IndexOutOfBoundsException e) {
			throw e;
		}

	}

	@Override
	public ResultSetIndex index(boolean unique, int indexType, Object... columns) throws SQLException {
		return getExtension().index(this, columns, unique, indexType);
	}

	@Override
	public ResultSetIndex index(Object columns) throws SQLException {
		if (columns instanceof Object[]) {
			return index(false, 0, (Object[]) columns);
		} else if (columns instanceof List) {
			return index(false, 0, ((List) columns).toArray());
		}
		return index(false, 0, columns);
	}

	@Override
	public ResultSetIndex indexByArray(Object... columns) throws SQLException {
		return index(false, 0, columns);
	}

	@Override
	public Record<T> insertRecord(Record<T> rec, int pos) throws SQLException {
		++length;
		if (data == Collections.EMPTY_LIST) {
			data = new ArrayList<Record<T>>();
		}
		switch (pos) {
		case AFTER:
			data.add(cursor, rec);
			break;
		case BEFORE:
			// data.add(++cursor, rec);
			throw new UnsupportedOperationException();
		}
		// 定位到新记录
		cursor++;

		return rec;
	}

	public Record<T> insertRecord(Record<T> rec) throws SQLException {
		++length;
		if (data == Collections.EMPTY_LIST) {
			data = new ArrayList<Record<T>>();
		}
		data.add(rec);
		return rec;
	}

	@Override
	public final void insertRow() throws SQLException {
		Record rec = newRecord();
		insertRecord(rec, AFTER);

	}

	@Override
	public boolean isEmpty() {
		return length == 0;
	}

	@Override
	public boolean isLast() throws SQLException {
		return cursor == length;
	}

	@Override
	public boolean isUnique(String column) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean last() throws SQLException {
		return absolute(length);
	}

	/**
	 * newRecord产生的Record<T>的独立的，并不包含于当前结果集中
	 */
	@Override
	public Record<T> newRecord() throws SQLException {
		if (cursor > length) {
			cursor = length;
		}

		Record<T> rec = createRecord(++increase);

		return rec;
	}

	@Override
	public boolean next() throws SQLException {
		return nextItem();
	}

	protected final boolean nextItem() throws SQLException {
		cursor++;
		return cursor <= length;
	}

	@Override
	public boolean previous() throws SQLException {
		cursor--;
		return cursor <= 0 ? false : true;
	}

	// @Override
	// public void removeListener(ListenerMatcher matcher) {
	// if (eventHolder != null)
	// eventHolder.removeListener(matcher);
	// }
	//
	// @Override
	// public void removeListener(Object listener) {
	// if (eventHolder != null)
	// eventHolder.removeListener(listener);
	// }

	protected Record<T> previousRecord() {
		if (cursor <= 1) {
			return null;
		}
		return getXObject(cursor - 1);
	}

	@Override
	public ListResultSet<T> query(Map<String, Object> args) throws SQLException {

		Object selectObj = args.get("select");
		Object groupByObj = args.get("groupBy");
		ResultSetQuery query = createQuery();
		if (selectObj != null) {
			query.select(selectObj);
		}
		if (groupByObj != null) {
			query.groupBy(groupByObj);
		}

		return (ListResultSet<T>) query.query();
	}

	@Override
	public Record<T> queryFirstByColumn(String name, Object value) throws SQLException {
		if (value == null) {
			for (Record<T> r : data) {
				Object v = r.getObject(name);
				if (v == null) {
					return r;
				}
			}
		} else {
			for (Record<T> r : data) {
				Object v = r.getObject(name);
				if (v != null && v.equals(value)) {
					return r;
				}
			}
		}

		return null;
	}

	public void setData(List<?> data) {
		// if (data.size() == 0) {
		// this.data = new ArrayList<Record<T>>(0);
		//
		// } else {
		if (data == null) {
			data = Collections.EMPTY_LIST;
		}
		if (!data.isEmpty()) {
			if (data.get(0) instanceof Record) {
				this.data = (List<Record<T>>) data;
			} else {
				this.data = toRecordList(data);
			}
		}
		// }
		length = data.size();
		increase = length;
	}

	protected List<Record<T>> toRecordList(List<?> data) {
		List<Record<T>> result = new ArrayList<Record<T>>();
		for (Object d : data) {
			try {
				Record<T> rec = meta.newRecord(++increase);// createRecord();
				rec.setValue((T) d);
				result.add(rec);
			} catch (SQLException e) {
				throw RX.throwB(e);
			}
		}
		return result;
	}

	/**
	 * 把结果集转成列表，取columnName列，最多 limit条
	 * 
	 * @param columnName
	 * @param limit
	 * @return
	 * @throws SQLException
	 */
	public List<Object> toList(String columnName, int limit) throws SQLException {
		List<Object> ll = new ArrayList<Object>();
		if (this.data != null) {

			int i = 0;
			for (Record r : data) {
				if (i > limit) {
					break;
				}
				ll.add(r.getObject(columnName));

				i++;
			}
		}
		return ll;
	}

//	public BasicDBList toMongoList(String columnName, int limit) throws SQLException {
//		BasicDBList ll = new BasicDBList();
//		if (this.data != null) {
//
//			int i = 0;
//			for (Record r : data) {
//				if (i > limit) {
//					break;
//				}
//				ll.add(r.getObject(columnName));
//
//				i++;
//			}
//		}
//		return ll;
//	}

	@Override
	public void updateObject(int columnIndex, Object x) throws SQLException {
		getRecord().set(columnIndex, x);
	}

	@Override
	public void updateObject(String columnName, Object x) throws SQLException {
		getRecord().set(columnName, x);
	}

}
