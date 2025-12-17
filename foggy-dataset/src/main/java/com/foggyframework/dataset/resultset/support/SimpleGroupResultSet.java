package com.foggyframework.dataset.resultset.support;


import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.resultset.*;

import java.sql.SQLException;
import java.util.List;
import com.foggyframework.dataset.resultset.Record;

public class SimpleGroupResultSet<T> extends ListResultSetSupport<T>
		implements GroupResultSet<T>, GroupResultSetMetaData {

	List<String> groupColumns;

	public SimpleGroupResultSet(ListResultSetMetaData<T> meta) {
		super(meta);
	}

	public SimpleGroupResultSet(ListResultSetMetaData<T> meta, List<T> data) {
		super(meta, data);
	}

	public SimpleGroupResultSet(ListResultSetMetaData<T> meta, List<T> data, List<String> groupColumns) {
		super(meta, data);
		this.groupColumns = groupColumns;
	}

//	public Object getHData() {
//
//	}

	@Override
	public void accept(GroupResultSetVisitor visitor) throws SQLException {
		GroupResultSetMetaData gmeta = getGroupMetaData();

		/**
		 * 有多少个分组
		 */
		int groupCount = gmeta.getGroupCount();
		String[] groupIndex = new String[groupCount];
		for (int i = 0; i < groupCount; i++) {
			groupIndex[i] = gmeta.getGroupName(i);
		}
		/**
		 * 存放组的值
		 */
		Object[] groups = new Object[groupCount];
		int groupHierarchy = 0;
		if (next()) {
			// 第一次
			Record<T> rec = this.getRecord();
			for (int i = 0; i < groupCount; i++) {
				Object gv = getObject(groupIndex[i]);
				visitor.nextGroup(this, i, groupIndex[i], gv);
				groupHierarchy++;
				groups[i] = gv;
			}
			visitor.visitRow(this, rec);
		}
		while (next()) {
			Record<T> rec = this.getRecord();
			for (int i = groupCount - 1; i >= 0; i--) {
				Object prevGv = groups[i];
				Object gv = getObject(groupIndex[i]);

				if (!StringUtils.equals(gv, prevGv)) {
					groupHierarchy--;
					visitor.backGroup(this, groupHierarchy);
				}
				groups[i] = gv;
			}
			for (int i = groupHierarchy; i < groupCount; i++) {
				visitor.nextGroup(this, groupHierarchy, groupIndex[i], groups[i]);
				groupHierarchy++;
			}

			visitor.visitRow(this, rec);

		}
	}

	public List<String> getGroupColumns() {
		return groupColumns;
	}

	@Override
	public int getGroupCount() {
		return groupColumns.size();
	}

	@Override
	public int getGroupIndex(int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	public GroupResultSetMetaData getGroupMetaData() throws SQLException {
		return this;
	}

	@Override
	public String getGroupName(int index) {
		return groupColumns.get(index);
	}

	public void setGroupColumns(List<String> groupColumns) {
		this.groupColumns = groupColumns;
	}

}
