package com.foggyframework.dataset.resultset;

import com.foggyframework.dataset.resultset.query.SelectColumn;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;

public interface RecordList<T> extends List<Record<T>> {

	public void commit() throws SQLException;

	public void delete();

	/**
	 * args : Record
	 * 
	 * @param command
	 */
	public void each(Function command);

	public List<T> getValues();

	public double sum(SelectColumn sc) throws SQLException;

	public double sum(String str) throws SQLException;
}
