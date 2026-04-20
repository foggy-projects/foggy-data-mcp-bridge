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

import javax.sql.DataSource;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * SQLite 3.30+ 方言实现
 */
public class SqliteDialect extends FDialect {

    /**
     * SQLite日期时间格式化器（线程安全）
     */
    private static final ThreadLocal<SimpleDateFormat> DATETIME_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    public SqliteDialect() {
        super();
        // SQLite 类型映射（SQLite 是动态类型，但我们使用亲和类型）
        registerColumnType(Types.BIT, "INTEGER");
        registerColumnType(Types.BOOLEAN, "INTEGER");
        registerColumnType(Types.BIGINT, "INTEGER");
        registerColumnType(Types.SMALLINT, "INTEGER");
        registerColumnType(Types.TINYINT, "INTEGER");
        registerColumnType(Types.INTEGER, "INTEGER");
        registerColumnType(Types.CHAR, "TEXT");
        registerColumnType(Types.FLOAT, "REAL");
        registerColumnType(Types.DOUBLE, "REAL");
        registerColumnType(Types.DATE, "TEXT");
        registerColumnType(Types.TIME, "TEXT");
        registerColumnType(Types.TIMESTAMP, "TEXT");
        registerColumnType(Types.VARBINARY, "BLOB");
        registerColumnType(Types.LONGVARBINARY, "BLOB");
        registerColumnType(Types.BINARY, "BLOB");
        registerColumnType(Types.BLOB, "BLOB");
        registerColumnType(Types.CLOB, "TEXT");
        registerColumnType(Types.NCLOB, "TEXT");
        registerColumnType(Types.VARCHAR, "TEXT");
        registerColumnType(Types.NVARCHAR, "TEXT");
        registerColumnType(Types.NUMERIC, "NUMERIC");
        registerColumnType(Types.OTHER, "TEXT");  // JSON 存储为 TEXT
    }

    @Override
    public char openQuote() {
        return '"';
    }

    @Override
    public char closeQuote() {
        return '"';
    }

    @Override
    public String getProductName() {
        return "SQLITE";
    }

    @Override
    public String generatePagingSql(String sql, int start, int limit) {
        StringBuilder sb = new StringBuilder(sql.length() + 30);
        sb.append(sql);
        sb.append(" LIMIT ").append(limit);
        if (start > 0) {
            sb.append(" OFFSET ").append(start);
        }
        return sb.toString();
    }

    @Override
    public String getQueryTableAndViewsSql() {
        return "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'";
    }

    @Override
    public DbType getDbType() {
        return DbType.SQLITE;
    }

    @Override
    public String buildNullOrderClause(String columnExpr, boolean nullsFirst) {
        // SQLite 3.30+ 支持 NULLS FIRST/LAST
        return columnExpr + (nullsFirst ? " NULLS FIRST" : " NULLS LAST");
    }

    @Override
    public boolean supportsNativeNullsOrdering() {
        return true;  // SQLite 3.30+
    }

    @Override
    public String getColumnMetadataSql() {
        // SQLite 使用 PRAGMA，需要特殊处理
        // 返回格式: cid | name | type | notnull | dflt_value | pk
        return "PRAGMA table_info(?)";
    }

    @Override
    public String getCurrentSchemaFunction() {
        return "'main'";  // SQLite 默认 schema
    }

    @Override
    public String buildStringAggFunction(String column, String separator) {
        return "GROUP_CONCAT(" + column + ", '" + separator + "')";
    }

    @Override
    public String buildDateFormatFunction(String column) {
        // SQLite 使用 strftime，日期已经是 TEXT 格式时可直接使用 DATE() 或 substr
        return "strftime('%Y-%m-%d', " + column + ")";
    }

    @Override
    public String getValidationQuery() {
        return "SELECT 1";
    }

    @Override
    public boolean supportsIfExistsBeforeTableName() {
        return true;
    }

    @Override
    public String getAddColumnString() {
        return "ADD COLUMN";
    }

    @Override
    public String getColumnComment(String comment) {
        // SQLite 不支持列注释
        return "";
    }

    /**
     * SQLite 需要重写元数据查询，因为使用 PRAGMA
     */
    @Override
    public SqlTable getTableByNameWithSchema(DataSource ds, String name, boolean loadIdColumn, String schema) {
        try {
            SqlTable st = new SqlTable(name, name, getColumnsByTableName(ds, name, schema), null);
            if (loadIdColumn) {
                // SQLite 使用 PRAGMA table_info
                String sql = "PRAGMA table_info(" + name + ")";
                DbUtils.query(ds, rs -> {
                    while (rs.next()) {
                        String columnName = rs.getString("name");
                        String type = rs.getString("type");
                        int pk = rs.getInt("pk");

                        if (pk > 0) {
                            SqlColumn sqlColumn = st.getSqlColumn(columnName, true);
                            if (sqlColumn != null) {
                                st.setIdColumn(sqlColumn);
                            }
                        }

                        SqlColumn col = st.getSqlColumn(columnName, true);
                        if (col != null && StringUtils.equalsIgnoreCase(type, "TEXT")) {
                            col.setJdbcType(Types.VARCHAR);
                        }
                    }
                }, sql);
            }
            return st;
        } catch (Throwable t) {
            System.err.println(t.getMessage());
            return null;
        }
    }

    @Override
    public String buildFunctionCall(String funcName, List<String> argsSql) {
        if (funcName == null || argsSql == null) return null;
        switch (funcName.toUpperCase()) {
            case "YEAR":
                return argsSql.size() == 1 ? "CAST(strftime('%Y', " + argsSql.get(0) + ") AS INTEGER)" : null;
            case "MONTH":
                return argsSql.size() == 1 ? "CAST(strftime('%m', " + argsSql.get(0) + ") AS INTEGER)" : null;
            case "DAY":
                return argsSql.size() == 1 ? "CAST(strftime('%d', " + argsSql.get(0) + ") AS INTEGER)" : null;
            case "HOUR":
                return argsSql.size() == 1 ? "CAST(strftime('%H', " + argsSql.get(0) + ") AS INTEGER)" : null;
            case "MINUTE":
                return argsSql.size() == 1 ? "CAST(strftime('%M', " + argsSql.get(0) + ") AS INTEGER)" : null;
            case "SECOND":
                return argsSql.size() == 1 ? "CAST(strftime('%S', " + argsSql.get(0) + ") AS INTEGER)" : null;
            case "DATE_FORMAT":
                // SQLite strftime 与 MySQL 使用相同的 % 格式符，参数顺序需调换
                return argsSql.size() == 2 ? "strftime(" + argsSql.get(1) + ", " + argsSql.get(0) + ")" : null;
            default:
                return null;
        }
    }

    @Override
    public String translateFunction(String funcName) {
        if (funcName == null) {
            return null;
        }
        if (funcName.isEmpty()) {
            return funcName;
        }
        switch (funcName.toUpperCase()) {
            case "NVL":         // Oracle → SQLite
            case "ISNULL":      // SQL Server → SQLite
                return "IFNULL";
            default:
                return funcName;
        }
    }

    @Override
    public String buildStatFunction(String funcName, String column) {
        throw RX.throwAUserTip(String.format(
                "SQLite 不支持统计函数 %s。请切换到 MySQL、PostgreSQL 或 SQL Server 数据源以使用此功能。", funcName));
    }

    @Override
    public String buildStatFunction(String funcName) {
        throw RX.throwAUserTip(String.format(
                "SQLite 不支持统计函数 %s。请切换到 MySQL、PostgreSQL 或 SQL Server 数据源以使用此功能。", funcName));
    }

    /**
     * SQLite 特殊处理：将 Date 类型转换为 TEXT 格式字符串
     * <p>
     * SQLite 没有原生的 DATE/DATETIME 类型，日期存储为 TEXT。
     * 当使用参数化查询时，必须将 Java Date 对象转换为 SQLite 能识别的文本格式。
     * </p>
     *
     * @param value 原始参数值
     * @return 转换后的参数值（Date -> "yyyy-MM-dd HH:mm:ss" 字符串）
     */
    @Override
    public Object convertParameterValue(Object value) {
        if (value instanceof Date) {
            // 将 Date 对象转换为 SQLite TEXT 格式
            return DATETIME_FORMAT.get().format((Date) value);
        }
        return value;
    }

    // ==================== 预聚合 SQL 构建支持 ====================

    @Override
    public String buildDateTruncateExpression(String column, String granularity) {
        if (granularity == null) return column;
        switch (granularity.toUpperCase()) {
            case "YEAR":
                return "strftime('%Y-01-01', " + column + ")";
            case "QUARTER":
                // SQLite 无 QUARTER 函数，用 strftime 模拟
                return "strftime('%Y-', " + column + ") || CASE " +
                       "WHEN CAST(strftime('%m', " + column + ") AS INTEGER) <= 3 THEN '01-01' " +
                       "WHEN CAST(strftime('%m', " + column + ") AS INTEGER) <= 6 THEN '04-01' " +
                       "WHEN CAST(strftime('%m', " + column + ") AS INTEGER) <= 9 THEN '07-01' " +
                       "ELSE '10-01' END";
            case "MONTH":
                return "strftime('%Y-%m-01', " + column + ")";
            case "WEEK":
                return "DATE(" + column + ", 'weekday 0', '-6 days')";
            case "DAY":
                return "DATE(" + column + ")";
            case "HOUR":
                return "strftime('%Y-%m-%d %H:00:00', " + column + ")";
            case "MINUTE":
                return "strftime('%Y-%m-%d %H:%M:00', " + column + ")";
            default:
                return column;
        }
    }

    @Override
    public String buildCurrentTimestampExpression() {
        return "datetime('now')";
    }

    /**
     * SQLite: {@code CAST((julianday(a) - julianday(b)) AS INTEGER)}
     * · julianday 返回浮点天数，转整数对齐 formula 语义
     */
    @Override
    public String buildDateDiffExpression(String a, String b) {
        return "CAST((julianday(" + a + ") - julianday(" + b + ")) AS INTEGER)";
    }

    /**
     * SQLite: {@code date(d, '+' || ? || ' day')}
     * · modifier 字符串通过 || 拼接参数绑定
     */
    @Override
    public String buildDateAddExpression(String d, String nParamPlaceholder, String unit) {
        String sqliteUnit = toSqliteUnit(unit);
        return "date(" + d + ", '+' || " + nParamPlaceholder + " || ' " + sqliteUnit + "')";
    }

    private static String toSqliteUnit(String unit) {
        if (unit == null) {
            throw new IllegalArgumentException("date_add unit must not be null");
        }
        switch (unit.toLowerCase()) {
            case "day":
                return "day";
            case "month":
                return "month";
            case "year":
                return "year";
            default:
                throw new IllegalArgumentException(
                        "date_add unit must be one of {day, month, year}, got: " + unit);
        }
    }

    @Override
    public String mapColumnType(String abstractType) {
        if (abstractType == null) return null;
        String upper = abstractType.toUpperCase();
        // SQLite 使用亲和类型
        if (upper.startsWith("VARCHAR") || upper.equals("DATETIME") || upper.equals("DATE")
                || upper.equals("TIMESTAMP")) {
            return "TEXT";
        }
        if (upper.equals("INT") || upper.equals("BIGINT") || upper.equals("TINYINT")
                || upper.equals("SMALLINT")) {
            return "INTEGER";
        }
        if (upper.startsWith("DECIMAL") || upper.equals("NUMERIC")) {
            return "REAL";
        }
        return abstractType;
    }
}
