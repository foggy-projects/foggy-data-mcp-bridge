package com.foggyframework.dataset.resultset;

public interface GroupResultSetVisitor<T> {
	/**
	 * 后退
	 * 
	 * @param rs
	 */
	void backGroup(ListResultSet<T> rs, int groupHierarchyIndex);

	/**
	 * 向前进一个组
	 * 
	 * @param rs
	 */
	void nextGroup(ListResultSet<T> rs, int groupHierarchyIndex, String groupName, Object value);

	void visitRow(ListResultSet<T> rs, Record<T> rec);
}
