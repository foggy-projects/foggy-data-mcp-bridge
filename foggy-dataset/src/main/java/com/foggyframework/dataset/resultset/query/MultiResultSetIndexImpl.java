package com.foggyframework.dataset.resultset.query;


import com.foggyframework.dataset.resultset.ListResultSet;
import com.foggyframework.dataset.resultset.Record;
import com.foggyframework.dataset.resultset.RecordList;
import com.foggyframework.dataset.resultset.support.RecordListImpl;

import java.sql.SQLException;
import java.util.*;

/**
 * 简单的索引实现类
 * 
 */
public class MultiResultSetIndexImpl implements ResultSetIndex {

	public static class MultiColumnKey {
		Object[] values;

		public MultiColumnKey(Object... values) {
			super();
			this.values = values;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			MultiColumnKey other = (MultiColumnKey) obj;
			if (!Arrays.equals(values, other.values))
				return false;
			return true;
		}

		public Object[] getValues() {
			return values;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + Arrays.hashCode(values);
			return result;
		}

		public void setValues(Object[] values) {
			this.values = values;
		}

	}

	public static void main(String[] args) {
		MultiColumnKey key1 = new MultiColumnKey("1", "2");
		MultiColumnKey key2 = new MultiColumnKey("1", "2");

		Set<MultiColumnKey> set = new HashSet<MultiColumnKey>();
		set.add(key1);

		System.out.println(set.contains(key2));
	}

	ListResultSet<?> rs;
	Map<Object, Object> key2Record = new HashMap<Object, Object>();

	String[] indexColumns;

	public MultiResultSetIndexImpl(ListResultSet<?> rs, Object... columns) throws SQLException {
		super();
		this.rs = rs;
		this.indexColumns = new String[columns.length];
		for (int i = 0; i < indexColumns.length; i++) {
			indexColumns[i] = (String) columns[i];
		}
		// this.indexColumns = columns;
		refresh();
	}

	@Override
	public boolean match(Object... columns) {
		if (indexColumns.length == columns.length) {
			// TODO columns不需要顺序
			for (int i = 0; i < indexColumns.length; i++) {
				if (!indexColumns[i].equalsIgnoreCase((String) columns[i])) {
					return false;
				}
			}
			return true;
		} else {
			return false;
		}

	}

	// public Record<Object> queryFrist(Object key) {
	// Object o = key2Record.get(key);
	// if (o instanceof List) {
	// return (Record<Object>) ((List) o).get(0);
	// }
	// return (Record<Object>) o;
	// }

	/**
	 * 
	 * @param key
	 * @param pos start 0
	 * @return
	 */
	@Override
	public Record<Object> next(Object key, int pos) {
		Object x = key2Record.get(key);
		if (x instanceof List) {
			if (((List) x).size() > pos) {
				return (Record<Object>) ((List) x).get(pos);
			}
		}
		return null;
	}

	@Override
	public Record<Object> queryFrist(Object values) {
		if (values instanceof List) {
			return queryFristByList((List<?>) values);
		} else if (values instanceof Object[]) {
			return queryFristByArray((Object[]) values);
		}
		return queryFristByArray(values);
	}

	@Override
	public Record<Object> queryFristByArray(Object... values) {
		MultiColumnKey key = new MultiColumnKey(values);
		Object obj = key2Record.get(key);
		if (obj instanceof RecordList) {
			return ((RecordList<Object>) obj).get(0);
		}
		if (obj == null) {
			return (Record<Object>) Record.EMPTY;
		}
		return (Record<Object>) obj;
	}

	@Override
	public Record<Object> queryFristByList(List<?> values) {
		return queryFrist(values.toArray());
	}

	@Override
	public RecordList<Object> queryList(Object value) {
		Object[] values = null;
		if (value instanceof Object[]) {
			values = (Object[]) value;
		}else if(value instanceof  List){
			values = ((List)value).toArray();
		} else {
			values = new Object[] { value };
		}
		MultiColumnKey key = new MultiColumnKey(values);
		Object obj = key2Record.get(key);
		if (obj instanceof RecordList) {
			return (RecordList<Object>) obj;
		}
		if (obj != null) {
			RecordList rl = new RecordListImpl(1);
			rl.add(obj);
			return rl;
		} else {
			// TODO using EMPTY
			return new RecordListImpl(0);
		}
	}

	public void refresh() throws SQLException {
		key2Record.clear();
		/**
		 * 目前仅考虑唯一索引
		 */
		int indexLength = indexColumns.length;
		Object[] values = null;
		MultiColumnKey key = null;
		for (Record<?> rec : rs.getRecords()) {
			values = new Object[indexLength];
			key = new MultiColumnKey(values);

			// create key
			for (int i = 0; i < indexLength; i++) {
				key.values[i] = rec.getObject(indexColumns[i]);
			}

			// cache
			if (key2Record.containsKey(key)) {
				Object x = key2Record.get(key);
				if (x instanceof RecordListImpl) {
					((RecordListImpl) x).add(rec);
				} else {
					RecordListImpl l = new RecordListImpl((ListResultSet) null);
					l.add(x);
					l.add(rec);
					key2Record.put(key, l);
				}
			} else {
				key2Record.put(key, rec);
			}

		}
	}
}
