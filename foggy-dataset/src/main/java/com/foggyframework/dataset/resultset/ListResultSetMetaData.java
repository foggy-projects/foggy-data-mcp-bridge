package com.foggyframework.dataset.resultset;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

public interface ListResultSetMetaData<T> extends ResultSetMetaData {

	/**
	 * @return
	 * @throws SQLException
	 * @since v1.1
	 */
	T apply(Record<T> rec, T result);

	T apply(T result, Record<T> rec);

	/**
	 * 如果找不到，返回０ columnName不区分大小写!
	 * 
	 * @return
	 * @throws SQLException
	 * @since v1.1
	 */
	int getColumnIndex(String columnName);

	List<String> getColumnNames();

	/**
	 * 
	 * start with
	 * 
	 * @return
	 * @throws SQLException
	 * @since v1.1
	 */
	Record<T> newRecord(int index) throws SQLException;

}