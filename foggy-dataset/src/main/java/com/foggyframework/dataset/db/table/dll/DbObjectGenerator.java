package com.foggyframework.dataset.db.table.dll;

import com.foggyframework.dataset.db.DbObject;
import com.foggyframework.dataset.db.dialect.FDialect;

public class DbObjectGenerator<T extends DbObject> {

	protected T dbObject;
	protected FDialect dialect;

	public DbObjectGenerator(T dbObject, FDialect dialect) {
		super();
		this.dbObject = dbObject;
		this.dialect = dialect;
	}

}
