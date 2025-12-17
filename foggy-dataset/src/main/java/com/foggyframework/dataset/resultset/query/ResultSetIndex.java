package com.foggyframework.dataset.resultset.query;



import com.foggyframework.dataset.resultset.Record;
import com.foggyframework.dataset.resultset.RecordList;

import java.util.List;

/**
 * 注意索引的更新
 * 
 * @author Foggy
 * 
 */
public interface ResultSetIndex {

	boolean match(Object... columns);

	Record<Object> next(Object key, int pos);

	Record<Object> queryFrist(Object values);

	Record<Object> queryFristByArray(Object... values);

	Record<Object> queryFristByList(List<?> values);

	RecordList<Object> queryList(Object values);
}
