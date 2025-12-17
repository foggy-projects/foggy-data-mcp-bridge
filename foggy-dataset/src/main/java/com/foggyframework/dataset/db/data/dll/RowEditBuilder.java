package com.foggyframework.dataset.db.data.dll;


import com.foggyframework.dataset.db.table.SqlTable;

public abstract class RowEditBuilder {
	/**
	 * 
	 */
	
	protected SqlTable sqlTable;

	public RowEditBuilder(SqlTable sqlTable) {
		this.sqlTable = sqlTable;
	}
	

}
