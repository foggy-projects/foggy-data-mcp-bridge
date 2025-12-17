package com.foggyframework.dataset.resultset.support;


import com.foggyframework.dataset.resultset.ListResultSet;
import com.foggyframework.dataset.resultset.query.MultiResultSetIndexImpl;
import com.foggyframework.dataset.resultset.query.ResultSetIndex;
import com.foggyframework.dataset.resultset.query.ResultSetIndexImpl;
import com.foggyframework.dataset.resultset.query.SelectColumn;
import com.foggyframework.fsscript.parser.spi.PropertyHolder;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResultSetExtension implements PropertyHolder {

	List<SelectColumn> selectColumns = new ArrayList<SelectColumn>();

	List<ResultSetIndex> indexs = Collections.EMPTY_LIST;

	public ResultSetExtension(ListResultSet<?> rs) throws SQLException {
		int i = 0;
		for (String c : rs.getMetaData().getColumnNames()) {
			selectColumns.add(new SelectColumn(c, i, null));
			i++;
		}
	}

	@Override
	public Object getProperty(String name) {
		return getSelectColumn(name);
	}

	public SelectColumn getSelectColumn(String name) {
		for (SelectColumn sc : selectColumns) {
			if (sc.as.equalsIgnoreCase(name)) {
				return sc;
			}
		}
		return null;
	}

	public ResultSetIndex index(ListResultSet<?> resultSet, Object[] columns, boolean unique, int indexType)
			throws SQLException {

		if (indexs == Collections.EMPTY_LIST) {
			indexs = new ArrayList<ResultSetIndex>();
		}

		// check index exist...
		for (ResultSetIndex index : indexs) {
			if (index.match(columns)) {
				return index;
			}
		}
		if (columns.length == 1) {
			ResultSetIndexImpl xx = new ResultSetIndexImpl(resultSet.getRecords(), (String) columns[0]);
			indexs.add(xx);
			return xx;
		} else {
			MultiResultSetIndexImpl xx = new MultiResultSetIndexImpl(resultSet, columns);
			indexs.add(xx);
			return xx;
		}
	}

}
