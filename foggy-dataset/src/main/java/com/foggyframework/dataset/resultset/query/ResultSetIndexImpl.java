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
public class ResultSetIndexImpl implements ResultSetIndex {

	// ListResultSet<?> rs;
	List<Record<?>> data;
	Map<Object, Object> key2Record = new HashMap<Object, Object>();
	String columnName;

	public ResultSetIndexImpl(List list, String column) throws SQLException {
		super();
		this.data = list;
		this.columnName = column;
		refresh();
	}

	public List<String> getStringKeys(int order) {
		List<String> ll = new ArrayList(key2Record.keySet());

		List<String> xx = new ArrayList<>();
		for (String l : ll) {
			if (l == null) {
				continue;
			}
			xx.add(l);
		}
		Collections.sort(xx);
		return xx;
	}

	@Override
	public boolean match(Object... columns) {
		if (columns.length == 1) {
			return ((String) columns[0]).equalsIgnoreCase(columnName);
		}
		return false;
	}

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
	public Record<Object> queryFristByArray(Object... key) {
		Object o = key2Record.get(key[0]);
		if (o instanceof List) {
			return (Record<Object>) ((List) o).get(0);
		}
		return (Record<Object>) o;
	}

	@Override
	public Record<Object> queryFristByList(List<?> values) {
		return queryFrist(values.toArray());
	}

	@Override
	public RecordList<Object> queryList(Object key) {
		if (key instanceof Object[]) {
			key = ((Object[]) key)[0];
		}
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
		for (Record<?> rec : data) {
			Object v = rec.getObject(columnName);
			if (key2Record.containsKey(v)) {
				Object x = key2Record.get(v);
				if (x instanceof RecordListImpl) {
					((RecordListImpl) x).add(rec);
				} else {
					RecordListImpl l = new RecordListImpl((ListResultSet) null);
					l.add(x);
					l.add(rec);
					key2Record.put(v, l);
				}
			} else {
				key2Record.put(v, rec);
			}

		}
	}

}
