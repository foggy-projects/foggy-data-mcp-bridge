package com.foggyframework.dataset.db.table;


import com.foggyframework.dataset.db.DbObjectType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;

import java.util.Collection;
import java.util.List;
import java.util.Map;


public class SqlTable extends SqlTableSupport {

	public SqlTable() {
		super();
	}
	public SqlTable(String name) {
		this(name, null);
	}
	public SqlTable(String name, String caption) {
		super(name, caption);
	}
	public SqlTable(String name, String caption, List<SqlColumn> sqlColumn, SqlColumn idColumn) {
		super(name, caption, sqlColumn, idColumn);
	}

	@Override
	public DbObjectType getDbObjectType() {
		return DbObjectType.TABLE;
	}

	@Override
	protected String getSearchColumnSql() {
		return "select * from " + name + " where 1=2";
	}

	public boolean isIdColumn(SqlColumn col) {
		if (idColumn == null) {
			return false;
		}
		return idColumn.getName().equalsIgnoreCase(col.getName());
	}


	public List<Map<String, Object>> queryForList(JdbcTemplate jdbcTemplate,Map<String, Object> config) {

		Object params = config.get("params");

		Object[] xx = null;

		if (params != null && params.getClass().isArray()) {
			xx = (Object[]) params;
		} else if (params instanceof Collection) {
			xx = ((Collection<?>) params).toArray();
		}
		String sql = (String) config.get("sql");

		Assert.notNull(sql, "必须定义sql语句");

		return jdbcTemplate.queryForList(sql, xx);
	}

	public Map<String, Object> queryForMap(JdbcTemplate jdbcTemplate,Map<String, Object> config) {

		Object params = config.get("params");

		Object[] xx = null;

		if (params != null && params.getClass().isArray()) {
			xx = (Object[]) params;
		} else if (params instanceof Collection) {
			xx = ((Collection<?>) params).toArray();
		}
		String sql = (String) config.get("sql");

		Assert.notNull(sql, "必须定义sql语句");

		return jdbcTemplate.queryForMap(sql, xx);
	}
}
