package com.foggyframework.dataset.resultset.support;


import com.foggyframework.core.utils.JsonUtils;
import com.foggyframework.dataset.resultset.ListResultSetMetaData;
import com.foggyframework.dataset.resultset.Record;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author foggy
 * @version 1.0
 * @since v1.1
 */
public final class ArrayRecord<T> extends BaseRecord<T> implements Record<T> {

    public Object[] values;

    public Object[] getValues() {
        return values;
    }

    ListResultSetMetaData<T> meta;

    public ArrayRecord(Object[] values, ListResultSetMetaData meta, int id) {
        super(id);
        this.values = values;
        this.meta = meta;
    }

    @Override
    public boolean canSet(String name) {
        return meta.getColumnIndex(name) > 0;
    }

    @Override
    public ListResultSetMetaData<T> getMetaData() {
        return meta;
    }

    @Override
    public Object getObject(int index) {
        return values[index - 1];
    }

    @Override
    public Object getObject(String columnName) {
        int c = meta.getColumnIndex(columnName);
        if (c <= 0) {
            throw new RuntimeException("column [" + columnName + "] not found!");
        }
        return getObject(c);
    }

    @Override
    public T getValue() {
//		try {
//			if (meta.getBeanInfoHelper() == null) {
        // TODO may be map
        return (T) this;
//			} else {
//				T v = (T) meta.getBeanInfoHelper().newInstance();
//				return meta.apply(v, this);
//			}
//		} catch (InstantiationException e) {
//			throw ErrorUtils.toRuntimeException(e);
//		} catch (IllegalAccessException e) {
//			throw ErrorUtils.toRuntimeException(e);
//		}
    }

    @Override
    public void set(int index, Object v) {
        try {
            values[index - 1] = v;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw e;
        }
    }

    @Override
    public void set(String columnName, Object v) throws SQLException {
        set(columnName, v, true);
    }

    @Override
    public void set(String columnName, Object v, boolean errorIfNotFound) throws SQLException {
        int c = meta.getColumnIndex(columnName);
        if (c == 0) {
            if (errorIfNotFound)
                throw new RuntimeException("column [" + columnName + "] not found!");
        } else {
            set(c, v);
        }

    }

    @Override
    public void setValue(T t) {
        meta.apply(this, t);
    }

    @Override
    public String toJson() {
        if (values == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<String, Object>();
        for (String c : getMetaData().getColumnNames()) {
            map.put(c, getObject(c));
        }
        return JsonUtils.toJson(map);
    }


}
