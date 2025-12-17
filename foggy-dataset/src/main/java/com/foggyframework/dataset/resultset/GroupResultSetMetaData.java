package com.foggyframework.dataset.resultset;

public interface GroupResultSetMetaData {

	int getGroupCount();

	int getGroupIndex(int index);

	String getGroupName(int index);
}
