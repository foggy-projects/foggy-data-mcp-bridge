package com.foggyframework.dataset.db.data.dll;


import com.foggyframework.dataset.db.table.SqlColumn;

public class IdxSqlColumn {

	public final SqlColumn sqlColumn;
	/**
	 * start 1
	 */
	public final int idx;

	public IdxSqlColumn(SqlColumn sqlColumn, int idx) {
		super();
		this.sqlColumn = sqlColumn;
		this.idx = idx;
	}

}
