/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.dataset.utils;


import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.SqlObject;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;
import com.foggyframework.dataset.db.table.SqlTableSupport;
import com.foggyframework.dataset.db.table.dll.TableGenerator;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.util.Date;
import java.util.*;

@Slf4j
public final class DbUtils {

	public interface ResultSetVistor {
		 void visit(ResultSet rs) throws SQLException;
	}

	public static SqlTable createSqlTable(String name, String caption, SqlColumn... columns) {

		List<SqlColumn> cc = new ArrayList<>();

		cc.addAll(Arrays.asList(columns));

		SqlTable st = new SqlTable(name, caption, cc, null);

		return st;
	}

	public static void executeSql(DataSource dataSource, List<String> list) throws SQLException {
		if (list == null) {
			return;
		}
		Connection conn = null;
		Statement st = null;
		try {
			conn = dataSource.getConnection();
			st = conn.createStatement();
			conn.setAutoCommit(false);
			long start = System.currentTimeMillis();
			int i = 0;
			for (String str : list) {
				if (str.trim().length() == 0) {
					continue;
				}
				st.addBatch(str);
				i++;
				if (i % 1000 == 0) {
					// 每1000条提交一次
					st.executeBatch();
					conn.commit();
					log.debug("已批量导入[" + i + "]条,cost : " + (System.currentTimeMillis() - start));
				}
			}
			start = System.currentTimeMillis();
			log.debug("开始执行批量更新...");
			st.executeBatch();
			log.debug("批量更新...完成,cost : " + (System.currentTimeMillis() - start) + ",开始提交");
			// if (!conn.getAutoCommit()) {
			conn.commit();
			log.debug("提交完成,cost : " + (System.currentTimeMillis() - start) + "");
			// }

		} finally {
			if (st != null) {
				st.close();
			}
			if (conn != null) {
				conn.close();
			}
		}
	}

	public static String generateCreateSql(FDialect dialect, SqlObject dbObject) {
		switch (dbObject.getDbObjectType()) {
		case TABLE:
			return new TableGenerator((SqlTable) dbObject, dialect).generatorCreate();
		case FUNCTION:
			break;
		case LOCALVIEW:
			break;
		case VIEW:
			break;
		default:
			break;
		}
		return "";
	}

	public static String generateCreateIndexSql(FDialect dialect, SqlTable st, SqlColumn column) {

		// CREATE INDEX "idx_B_ALY_SMS_TPL_create_time2" ON "B_ALY_SMS_TPL" (d_state)
		StringBuilder sb = new StringBuilder("CREATE INDEX ");
		sb.append(dialect.quoteIdentifier("idx_" + st.getName().toUpperCase() + "_" + column.getName()));
		sb.append(" ON ").append(dialect.quoteIdentifier(st.getName().toUpperCase()));
		sb.append(" (").append(column.getName()).append(")");
		return sb.toString();
	}

	public static List<String> generateAlertSql(FDialect dialect, SqlObject dbObject, SqlTable tableFromDb) {
		switch (dbObject.getDbObjectType()) {
		case TABLE:
			return new TableGenerator((SqlTable) dbObject, dialect).sqlAlterStrings(tableFromDb);
		case FUNCTION:
			break;
		case LOCALVIEW:
			break;
		case VIEW:
			break;
		default:
			break;
		}
		return Collections.EMPTY_LIST;
	}

	public static String generateDropSql(FDialect dialect, SqlTable dbObject) {
		switch (dbObject.getDbObjectType()) {
		case TABLE:
			return new TableGenerator(dbObject, dialect).generatorDrop();
		case FUNCTION:
			break;
		case LOCALVIEW:
			break;
		case VIEW:
			break;
		default:
			break;
		}
		return "";
	}

//	public static List<DbObjectEntry> getAllDbDbObjectEntries(DataSource delegateDataSource, FDialect dialect) {
//		List<DbObjectEntry> xx = dialect.getTableEntities(delegateDataSource);
//		return xx;
//	}

	public static  List<SqlColumn> getColumnsBySql(DataSource ds, String sql) {
		if (StringUtils.isEmpty(sql)) {
			return Collections.EMPTY_LIST;
		}
		FDialect d = getDialect(ds);
		return d.getColumnsBySql(ds, sql);
	}

	public static  List<SqlColumn> getColumnsByTableName(DataSource ds, String tableName) {
		if (StringUtils.isEmpty(tableName))
			return Collections.EMPTY_LIST;
		if (ds == null)
			return Collections.EMPTY_LIST;
		FDialect d = getDialect(ds);
		// String sql = d.getQueryTablesSql();
		return d.getColumnsByTableName(ds, tableName);
	}

//	public static final List<SqlColumn> getColumnsByUniqueName(String uniqueName) {
//		String[] xx = uniqueName.split("/");
//		String dsName = xx[1];
//		String tableName = xx[2];
//		FoggyJdbcDataSource ds = dataSourceManager.getActivedFoggyDataSource(dsName)
//				.getDecorate(FoggyJdbcDataSource.class);
//		return ds.getDataSet(tableName).getSqlColumns();
//	}

	public static  FDialect getDialect(DataSource ds) {
		Connection con = null;
		try {
			con = ds.getConnection();
			String productName = con.getMetaData().getDatabaseProductName().toUpperCase();
			if (isMysql(productName)) {
				return FDialect.MYSQL_DIALECT;
			} else if (isPostgres(productName)) {
				return FDialect.POSTGRES_DIALECT;
			} else if (isSqlServer(productName)) {
				return FDialect.SQLSERVER_DIALECT;
			} else if (isSqlite(productName)) {
				return FDialect.SQLITE_DIALECT;
			} else if (isOracle(productName)) {
				throw new UnsupportedOperationException("Oracle 支持尚未实现");
			} else if (isApacheDerby(productName)) {
				throw new UnsupportedOperationException("Apache Derby 支持尚未实现");
			}
			throw new UnsupportedOperationException("不支持数据库 : " + productName);
		} catch (Throwable e) {
			throw RX.throwB(e);
		} finally {
			if (con != null) {
				try {
					con.close();
				} catch (SQLException e) {
					throw RX.throwB(e);
				}
			}
		}

	}

	public List<SqlTableSupport> getTableAndViews(final DataSource ds) {
		final List<SqlTableSupport> x = new ArrayList<SqlTableSupport>();

		FDialect d = getDialect(ds);
		DbUtils.query(ds, new ResultSetVistor() {

			@Override
			public void visit(ResultSet rs) throws SQLException {
				while (rs.next()) {
					SqlTable st = new SqlTable(rs.getString("TABLE_NAME"), rs.getString("TABLE_NAME"),
							getColumnsByTableName(ds, rs.getString("TABLE_NAME")), null);

					x.add(st);
				}
			}
		}, d.getQueryTableAndViewsSql());
		return x;
	}
	public static  SqlTable getTableByName(DataSource ds, String name,boolean loadIdColumn) {
		if (ds == null)
			return null;
//		if (ds instanceof FoggyDataSource) {
//			return ((FoggyDataSource) ds).getDecorate(FoggyJdbcDataSource.class).getTable(name);
//		}
		FDialect d = getDialect(ds);
		return d.getTableByName(ds, name,loadIdColumn);
	}
	public static  SqlTable getTableByName(DataSource ds, String name) {
		if (ds == null)
			return null;
//		if (ds instanceof FoggyDataSource) {
//			return ((FoggyDataSource) ds).getDecorate(FoggyJdbcDataSource.class).getTable(name);
//		}
		FDialect d = getDialect(ds);
		return d.getTableByName(ds, name);
	}

	private static boolean isApacheDerby(String productName) {
		return productName.startsWith("APACHE DERBY");
	}

	public static  boolean isMysql(String productName) {
		return productName.startsWith("MYSQL") || productName.contains("MARIADB");
	}

	private static boolean isOracle(String productName) {
		return productName.startsWith("ORACLE");
	}

	public static  boolean isSqlite(String productName) {
		return productName.startsWith("SQLITE");
	}

	public static boolean isPostgres(String productName) {
		return productName.startsWith("POSTGRESQL");
	}

	public static boolean isSqlServer(String productName) {
		return productName.contains("SQL SERVER") || productName.contains("MICROSOFT");
	}

	public static  void query(DataSource ds, ResultSetVistor v, String sql, Object... args) {
		Connection con = null;
		PreparedStatement state = null;
		ResultSet rs = null;
		try {
			con = ds.getConnection();
			state = con.prepareStatement(sql);

			int i = 1;
			for (Object x : args) {
				state.setObject(i, x);
				i++;
			}
			rs = state.executeQuery();
			v.visit(rs);
		} catch (SQLException e) {
			throw RX.throwB(e);
		} finally {
			if (con != null) {
				try {
					con.close();
				} catch (SQLException e) {
					throw RX.throwB(e);
				}
			}
			if (state != null) {
				try {
					state.close();
				} catch (SQLException e) {
					throw RX.throwB(e);
				}
			}
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException e) {
					throw RX.throwB(e);
				}
			}
		}
	}

	public static  String[] splitUniqueName(String uniqueName) {
		String[] xx = uniqueName.split("/");
		return new String[] { xx[1], xx[2] };
	}

	public static String getInExp(int size) {
		switch (size) {
		case 0:
			return "()";
		case 1:
			return "(?)";
		case 2:
			return "(?,?)";
		case 3:
			return "(?,?,?)";
		case 4:
			return "(?,?,?,?)";
		case 5:
			return "(?,?,?,?,?)";
		default:
			StringBuilder sb = new StringBuilder("(?,?,?,?,?");
			for (int i = 5; i < size; i++) {
				sb.append(",?");
			}
			sb.append(")");
			return sb.toString();
		}
	}

	public static  void test(DataSource dataSource) {
		// Connection conn = null;
		// ResultSet rs = null;
		// try {
		// conn = dataSource.getConnection();
		// FDialect dialect = getDialect(dataSource);
		// String sql = dialect.getValidationQuery();
		// rs = conn.prepareStatement(sql).executeQuery();
		// } catch (Throwable t) {
		// t.printStackTrace();
		// throw ErrorUtils.toRuntimeException(t);
		// } finally {
		// if (conn != null) {
		// try {
		// conn.close();
		// } catch (SQLException e) {
		// throw ErrorUtils.toRuntimeException(e);
		// }
		// }
		// }
	}

	public static  int getJdbcType(Class javaClass) {
		if (javaClass == String.class) {
			return Types.VARCHAR;
		} else if (javaClass == Integer.class) {
			return Types.INTEGER;
		} else if (javaClass == Double.class) {
			return Types.DOUBLE;
		} else if (javaClass == Long.class || javaClass == long.class) {
			return Types.BIGINT;
		} else if (javaClass == Date.class) {
			return Types.TIMESTAMP;
		} else if (javaClass == BigDecimal.class) {
			return Types.DECIMAL;
		} else if (javaClass.getName().equals("com.vividsolutions.jts.geom.Point")) {
			return Types.STRUCT;
		} else {
			throw new UnsupportedOperationException();
		}
	}

}
