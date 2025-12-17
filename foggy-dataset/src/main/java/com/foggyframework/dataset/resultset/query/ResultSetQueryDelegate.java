package com.foggyframework.dataset.resultset.query;


import com.foggyframework.dataset.resultset.ListResultSet;
import com.foggyframework.fsscript.parser.spi.Exp;

import java.sql.SQLException;

public class ResultSetQueryDelegate implements ResultSetQuery {
	ResultSetQuery delegate;

	public ResultSetQueryDelegate(ResultSetQuery delegate) {
		super();
		this.delegate = delegate;
	}

	@Override
	public Exp eq(SelectColumn sc, Object v) {
		return delegate.eq(sc, v);
	}

	@Override
	public ResultSetQuery groupBy(Object groupByObj) {
		return delegate.groupBy(groupByObj);
	}

	@Override
	public ResultSetQueryImpl.LeftJoin leftJoin(ListResultSet<?> rs) {
		return delegate.leftJoin(rs);
	}

	@Override
	public Exp like(SelectColumn sc, String v) {
		return delegate.like(sc, v);
	}

	@Override
	public ListResultSet<?> query() throws SQLException {
		return delegate.query();
	}

	@Override
	public ResultSetQuery select(Object selectObj) {
		return delegate.select(selectObj);
	}
}
