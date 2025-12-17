package com.foggyframework.dataset.resultset.query;

import com.foggyframework.dataset.resultset.ListResultSet;
import com.foggyframework.fsscript.parser.spi.Exp;

import java.sql.SQLException;

/**
 * <pre>
 * var s = rs.createQuery().select({
								as : '_c1',
								column : _rs_sum(rs.columns.value)
							}).query();
	s.firstRecord[1]
 * </pre>
 * 
 * @author fengjianguang
 *
 */
public interface ResultSetQuery {

	Exp eq(SelectColumn sc, Object v);

	ResultSetQuery groupBy(Object groupByObj);

	ResultSetQueryImpl.LeftJoin leftJoin(ListResultSet<?> rs);

	Exp like(SelectColumn sc, String v);

	<T> ListResultSet<T> query() throws SQLException;

	ResultSetQuery select(Object selectObj);
}
