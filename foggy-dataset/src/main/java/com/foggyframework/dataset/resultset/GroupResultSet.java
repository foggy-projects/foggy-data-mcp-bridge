package com.foggyframework.dataset.resultset;

import java.sql.SQLException;

/**
 * 
 * 
 * @author Foggy
 * 
 */
public interface GroupResultSet<T> extends ListResultSet<T> {

	void accept(GroupResultSetVisitor<T> visitor) throws SQLException;

	GroupResultSetMetaData getGroupMetaData() throws SQLException;
}
