package com.foggyframework.dataset.db.table.dll;

import com.foggyframework.dataset.db.SqlObject;
import com.foggyframework.dataset.db.dialect.FDialect;

public class DbObjectGenerator<T extends SqlObject> {

	protected T dbObject;
	protected FDialect dialect;

	public DbObjectGenerator(T dbObject, FDialect dialect) {
		super();
		this.dbObject = dbObject;
		this.dialect = dialect;
	}

}
