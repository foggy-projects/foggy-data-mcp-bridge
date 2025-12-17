package com.foggyframework.dataset.resultset.support;

import com.foggyframework.core.AbstractDecorate;
import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.resultset.Record;
import com.foggyframework.dataset.resultset.query.SelectColumn;
import com.foggyframework.fsscript.parser.spi.PropertyHolder;

import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;

public abstract class BaseRecord<T> extends AbstractDecorate implements Record<T>, PropertyHolder {
	public final int id;

	public BaseRecord(int id) {
		this.id = id;
	}
	@Override
	public Object getProperty(String name) {
		try {
			return getObject(name);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}
	@Override
	public void apply(Object obj) throws SQLException {
		if (obj instanceof Map) {
			beginEdit();
			for (Entry<String, Object> e : ((Map<String, Object>) obj).entrySet()) {
				set(e.getKey(), e.getValue());
			}
			endEdit();
		} else if (obj instanceof Object[]) {
			int i = 1;
			for (Object o : (Object[]) obj) {
				set(i, o);
				i++;
			}

		} else {
			throw new UnsupportedOperationException("Only java.util.Map support now");
		}
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

	public Object get(Object key) {
		try {
			if (key instanceof Number) {

				return getObject(((Number) key).intValue());

			} else {
				return getObject(key.toString());
			}
		} catch (SQLException e) {
			throw RX.throwB(e);// .printStackTrace();
		}
	}

	@Override
	public final int getId() {
		return id;
	}

	@Override
	public Object getObject(SelectColumn sc) throws SQLException {
		return getObject(sc.columnIndex + 1);
	}

	@Override
	public Object getSubObject(int name) {
		try {
			return getObject(name);
		} catch (SQLException e) {
			throw RX.throwB(e);
		}
	}

	@Override
	public Object getSubObject(String name) {
		try {
			return getObject(name);
		} catch (SQLException e) {
			throw RX.throwB(e);
		}
	}

	@Override
	public final boolean isCollapsed() {
		// return !hasMask(Record.EXPANDED);
		return false;
	}

	@Override
	public boolean isDeleted() {
		return false;
	}

	@Override
	public final boolean isLeaf() {
		// return hasMask(LEAF);
		return false;
	}

	@Override
	public boolean isModified(int i) {
		return false;
	}

	@Override
	public final boolean isNew() {
		// return hasMask(LEAF);
		return false;
	}

	@Override
	public boolean isUpdated() {
		return false;
	}

	@Override
	public boolean readOnly() {
		return true;
	}

	@Override
	public void set(SelectColumn sc, Object object) throws SQLException {
		set(sc.columnIndex + 1, object);
	}

}
