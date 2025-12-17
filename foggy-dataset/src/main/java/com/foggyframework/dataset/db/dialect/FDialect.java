/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.dataset.db.dialect;


import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;
import com.foggyframework.dataset.utils.DbUtils;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public abstract class FDialect {
    public static MysqlDialect MYSQL_DIALECT;
    public static PostgresDialect POSTGRES_DIALECT;
    public static SqlServerDialect SQLSERVER_DIALECT;
    public static SqliteDialect SQLITE_DIALECT;

    static {
        try {
            MYSQL_DIALECT = new MysqlDialect();
            POSTGRES_DIALECT = new PostgresDialect();
            SQLSERVER_DIALECT = new SqlServerDialect();
            SQLITE_DIALECT = new SqliteDialect();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public FDialect() {
        registerColumnType(Types.BIT, "bit");
        registerColumnType(Types.BOOLEAN, "boolean");
        registerColumnType(Types.TINYINT, "tinyint");
        registerColumnType(Types.SMALLINT, "smallint");
        registerColumnType(Types.INTEGER, "integer");
        registerColumnType(Types.BIGINT, "bigint");
        registerColumnType(Types.FLOAT, "float($p)");
        registerColumnType(Types.DOUBLE, "double precision");
        registerColumnType(Types.NUMERIC, "numeric($p,$s)");
        registerColumnType(Types.DECIMAL, "decimal($p,$s)");
        registerColumnType(Types.REAL, "real");

        registerColumnType(Types.DATE, "date");
        registerColumnType(Types.TIME, "time");
        registerColumnType(Types.TIMESTAMP, "timestamp");

        registerColumnType(Types.VARBINARY, "bit varying($l)");
        registerColumnType(Types.LONGVARBINARY, "bit varying($l)");
        registerColumnType(Types.BLOB, "blob");

        registerColumnType(Types.CHAR, "char($l)");
        registerColumnType(Types.VARCHAR, "varchar($l)");
        registerColumnType(Types.LONGVARCHAR, "varchar($l)");
        registerColumnType(Types.CLOB, "clob");

        registerColumnType(Types.NCHAR, "nchar($l)");
        registerColumnType(Types.NVARCHAR, "nvarchar($l)");
        registerColumnType(Types.LONGNVARCHAR, "nvarchar($l)");
        registerColumnType(Types.NCLOB, "nclob");
    }

    private final TypeNames typeNames = new TypeNames();
    private final TypeNames hibernateTypeNames = new TypeNames();

    protected void registerColumnType(int code, String name) {
        typeNames.put(code, name);
    }

    public abstract char openQuote();

    public abstract char closeQuote();

    public abstract String generatePagingSql(String sql, int start, int limit);

    /**
     * 获取数据库产品名称
     * @return 产品名称，如 MYSQL, POSTGRESQL, SQLSERVER, SQLITE
     */
    public abstract String getProductName();

    public SqlTable getTableByName(DataSource ds, String name) {
        try {
            return getTableByName(ds, name, true);
        } catch (Throwable t) {
            System.err.println(t.getMessage());
            return null;
        }
    }
    public SqlTable getTableByName(DataSource ds, String name, boolean loadIdColumn){

        return getTableByNameWithSchema(ds,name,loadIdColumn,null);
    }
    public SqlTable getTableByNameWithSchema(DataSource ds, String name, boolean loadIdColumn,String schema) {
        Assert.notNull(ds,"dataSource不能为空");
        try {
            SqlTable st = new SqlTable(name, name, getColumnsByTableName(ds, name,schema), null);
            if (loadIdColumn) {
                //去数据库加载id列
                if(schema == null) {
                    try (Connection connection = ds.getConnection()) {
                        schema = connection.getCatalog();
                        if (StringUtils.isEmpty(schema)) {
                            schema = connection.getSchema();
                        }
                    }
                }

                // 使用方言提供的元数据查询 SQL
                String sql = getColumnMetadataSql();
                final String finalSchema = schema;

                DbUtils.query(ds, rs -> {
                    ResultSetMetaData meta = rs.getMetaData();
                    while (rs.next()) {
                        String columnName = rs.getString(1);
                        Number length = (Number) rs.getObject(2);
                        String column_key = rs.getString(3);
                        String data_type = rs.getString(4);
                        if (StringUtils.equals(column_key, "PRI")) {
                            st.setIdColumn(st.getSqlColumn(columnName, true));
                        }
                        if (length != null) {
                            SqlColumn col = st.getSqlColumn(columnName, true);
                            if (col != null) {
                                col.setLength(length.intValue());
                            }
                        }
                        // 处理特殊数据类型（不同数据库可能不同）
                        handleSpecialDataType(st, columnName, data_type);
                    }
                }, sql, name, finalSchema);

            }
            return st;
        } catch (Throwable t) {
            System.err.println(t.getMessage());
            return null;
        }
    }

    /**
     * 处理特殊数据类型，子类可重写
     */
    protected void handleSpecialDataType(SqlTable st, String columnName, String dataType) {
        // 默认实现为空，子类根据需要重写
    }

    public List<SqlColumn> getColumnsByTableName(DataSource ds, String tableName) {
        return getColumnsBySql(ds, "select * from " + tableName);
    }

    public List<SqlColumn> getColumnsByTableName(DataSource ds, String tableName,String schema) {
        // 使用方言的引号
        return getColumnsBySql(ds, "select * from " + (StringUtils.isEmpty(schema)?"":(quoteIdentifier(schema)+"."))+tableName);
    }

    public List<SqlColumn> getColumnsBySql(DataSource ds, String sql) {

        sql = "select FX.* from (" + sql + ") FX  where 1=2";
        final List<SqlColumn> x = new ArrayList<SqlColumn>();

        DbUtils.query(ds, new DbUtils.ResultSetVistor() {

            @Override
            public void visit(ResultSet rs) throws SQLException {
                ResultSetMetaData meta = rs.getMetaData();// .getColumnCount()
                for (int i = 1; i <= meta.getColumnCount(); i++) {
//                    meta.get
                    SqlColumn sc = new SqlColumn(meta.getColumnName(i), meta.getColumnName(i), meta.getColumnType(i));
                    sc.setNullable(meta.isNullable(i) == ResultSetMetaData.columnNullable);
                    x.add(sc);
                }
            }
        }, sql);
        return x;
    }

    public String getTypeName(int code, int length, int precision, int scale) {
        final String result = typeNames.get(code, length, precision, scale);
        if (result == null) {
            throw RX.throwB(
                    String.format("No type mapping for java.sql.Types code: %s, length: %s", code, length)
            );
        }
        return result;
    }

    protected void registerColumnType(int code, long capacity, String name) {
        typeNames.put(code, capacity, name);
    }

    protected void registerHibernateType(int code, long capacity, String name) {
        hibernateTypeNames.put(code, capacity, name);
    }

    public String getCreateMultisetTableString() {
        return getCreateTableString();
    }

    public String getCreateTableString() {
        return "create table";
    }

    public String getNullColumnString() {
        return "";
    }

    public String getTableComment(String comment) {
        return "";
    }

    public boolean supportsIfExistsBeforeTableName() {
        return false;
    }

    public String getCascadeConstraintsString() {
        return "";
    }

    /**
     * For dropping a table, can the phrase "if exists" be applied after the table name?
     * <p>
     * NOTE : Only one or the other (or neither) of this and supportsIfExistsBeforeTableName should return true
     * Returns:
     * true if the "if exists" can be applied after the table name
     *
     * @return
     */
    public boolean supportsIfExistsAfterTableName() {
        return false;
    }

    public String getAddColumnString() {
        throw new UnsupportedOperationException("No add column syntax supported by " + getClass().getName());
    }

    public boolean supportsUnique() {
        return true;
    }

    public boolean supportsNotNullUnique() {
        return true;
    }

    public String getColumnComment(String comment) {
        return "";
    }

    public abstract String getQueryTableAndViewsSql();

    public abstract DbType getDbType();

    // ==================== 多数据库适配新增方法 ====================

    /**
     * 引用标识符（表名、列名）
     * @param identifier 标识符
     * @return 带引号的标识符
     */
    public String quoteIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        return openQuote() + identifier + closeQuote();
    }

    /**
     * 获取带 schema 的完整表名
     * @param schema schema名
     * @param table 表名
     * @return 完整表名
     */
    public String getQualifiedTableName(String schema, String table) {
        if (StringUtils.isEmpty(schema)) {
            return quoteIdentifier(table);
        }
        return quoteIdentifier(schema) + "." + quoteIdentifier(table);
    }

    /**
     * 构建 NULL 排序子句
     * @param columnExpr 列表达式
     * @param nullsFirst true=NULLS FIRST, false=NULLS LAST
     * @return 排序子句
     */
    public abstract String buildNullOrderClause(String columnExpr, boolean nullsFirst);

    /**
     * 是否支持原生 NULLS FIRST/LAST 语法
     * @return true 支持原生语法
     */
    public boolean supportsNativeNullsOrdering() {
        return false;
    }

    /**
     * 获取列元数据查询 SQL
     * @return SQL语句，参数为 tableName, schema
     */
    public abstract String getColumnMetadataSql();

    /**
     * 获取数据库验证查询
     * @return 验证SQL
     */
    public String getValidationQuery() {
        return "SELECT 1";
    }

    /**
     * 获取当前 schema/database 的函数
     * @return SQL函数表达式
     */
    public abstract String getCurrentSchemaFunction();

    /**
     * 构建字符串聚合函数
     * @param column 列名
     * @param separator 分隔符
     * @return 聚合函数表达式
     */
    public abstract String buildStringAggFunction(String column, String separator);

    /**
     * 构建日期格式化函数（格式化为 yyyy-MM-dd）
     * @param column 列表达式
     * @return 日期格式化函数表达式
     */
    public abstract String buildDateFormatFunction(String column);
}
