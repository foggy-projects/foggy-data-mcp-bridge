package com.foggyframework.dataset.model.support;


import com.foggyframework.dataset.utils.DatasetTemplate;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import javax.sql.DataSource;
import java.util.Arrays;

public class SQLKey {
	public String sql;
	public final Object[] args;
	public final int start;
	public final int limit;
	public final SQL srcSQL;

	public SQLKey(String sq, Object[] args, int start, int limit, SQL srcSQL) {
		super();
		this.sql = sq;
		this.args = args;
		this.start = start;
		this.limit = limit;
		this.srcSQL = srcSQL;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SQLKey other = (SQLKey) obj;
		if (!Arrays.equals(args, other.args))
			return false;
		if (limit != other.limit)
			return false;
		if (sql == null) {
			if (other.sql != null)
				return false;
		} else if (!sql.equals(other.sql))
			return false;
		if (start != other.start)
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(args);
		result = prime * result + limit;
		result = prime * result + ((sql == null) ? 0 : sql.hashCode());
		result = prime * result + start;
		return result;
	}

	@Override
	public String toString() {
		return sql + "\nstart : " + start + ",limit : " + limit;
	}

	public DataSource getDataSource(ExpEvaluator ee) {

		try {
			return (DataSource) srcSQL.getDsExp().evalResult(ee);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		}
	}
	public DatasetTemplate getDatasetTemplate(ExpEvaluator ee) {

		try {
			return DatasetTemplateProvider.getDatasetTemplate(getDataSource(ee));
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		}
	}
}
