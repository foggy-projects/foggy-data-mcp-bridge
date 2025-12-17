/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved.
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.dataset.db.dialect;

import java.sql.Types;

/**
 * SQL Server 2012+ 方言实现
 */
public class SqlServerDialect extends FDialect {

    public SqlServerDialect() {
        super();
        // SQL Server 特有类型映射
        registerColumnType(Types.BIT, "bit");
        registerColumnType(Types.BOOLEAN, "bit");
        registerColumnType(Types.BIGINT, "bigint");
        registerColumnType(Types.SMALLINT, "smallint");
        registerColumnType(Types.TINYINT, "tinyint");
        registerColumnType(Types.INTEGER, "int");
        registerColumnType(Types.CHAR, "nchar(1)");
        registerColumnType(Types.FLOAT, "float");
        registerColumnType(Types.DOUBLE, "float");
        registerColumnType(Types.DATE, "date");
        registerColumnType(Types.TIME, "time");
        registerColumnType(Types.TIMESTAMP, "datetime2");
        registerColumnType(Types.VARBINARY, "varbinary(max)");
        registerColumnType(Types.LONGVARBINARY, "varbinary(max)");
        registerColumnType(Types.BINARY, "varbinary($l)");
        registerColumnType(Types.BLOB, "varbinary(max)");
        registerColumnType(Types.CLOB, "nvarchar(max)");
        registerColumnType(Types.NCLOB, "nvarchar(max)");
        registerColumnType(Types.VARCHAR, "nvarchar($l)");
        registerColumnType(Types.NVARCHAR, "nvarchar($l)");
        registerColumnType(Types.NUMERIC, "numeric($p,$s)");
        registerColumnType(Types.OTHER, "nvarchar(max)");  // JSON 存储为字符串
    }

    @Override
    public char openQuote() {
        return '[';
    }

    @Override
    public char closeQuote() {
        return ']';
    }

    @Override
    public String getProductName() {
        return "SQLSERVER";
    }

    @Override
    public String generatePagingSql(String sql, int start, int limit) {
        // SQL Server 2012+ 使用 OFFSET...FETCH 语法
        // 注意: 必须有 ORDER BY 子句
        StringBuilder sb = new StringBuilder(sql.length() + 50);
        sb.append(sql);

        // 检查是否有 ORDER BY，没有则添加默认排序
        if (!sql.toUpperCase().contains("ORDER BY")) {
            sb.append(" ORDER BY (SELECT NULL)");
        }

        sb.append(" OFFSET ").append(start).append(" ROWS");
        sb.append(" FETCH NEXT ").append(limit).append(" ROWS ONLY");
        return sb.toString();
    }

    @Override
    public String getQueryTableAndViewsSql() {
        return "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_CATALOG = DB_NAME()";
    }

    @Override
    public DbType getDbType() {
        return DbType.SQLSERVER;
    }

    @Override
    public String buildNullOrderClause(String columnExpr, boolean nullsFirst) {
        // SQL Server 不原生支持 NULLS FIRST/LAST，使用 CASE WHEN 模拟
        if (nullsFirst) {
            return "CASE WHEN " + columnExpr + " IS NULL THEN 0 ELSE 1 END, " + columnExpr;
        } else {
            return "CASE WHEN " + columnExpr + " IS NULL THEN 1 ELSE 0 END, " + columnExpr;
        }
    }

    @Override
    public boolean supportsNativeNullsOrdering() {
        return false;
    }

    @Override
    public String getColumnMetadataSql() {
        return "SELECT c.COLUMN_NAME, c.CHARACTER_MAXIMUM_LENGTH, " +
               "CASE WHEN pk.COLUMN_NAME IS NOT NULL THEN 'PRI' ELSE '' END AS column_key, " +
               "c.DATA_TYPE " +
               "FROM INFORMATION_SCHEMA.COLUMNS c " +
               "LEFT JOIN (" +
               "  SELECT ku.TABLE_NAME, ku.COLUMN_NAME, ku.TABLE_SCHEMA " +
               "  FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc " +
               "  JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE ku " +
               "    ON tc.CONSTRAINT_NAME = ku.CONSTRAINT_NAME AND tc.TABLE_SCHEMA = ku.TABLE_SCHEMA " +
               "  WHERE tc.CONSTRAINT_TYPE = 'PRIMARY KEY'" +
               ") pk ON c.TABLE_NAME = pk.TABLE_NAME AND c.COLUMN_NAME = pk.COLUMN_NAME AND c.TABLE_SCHEMA = pk.TABLE_SCHEMA " +
               "WHERE c.TABLE_NAME = ? AND c.TABLE_SCHEMA = ?";
    }

    @Override
    public String getCurrentSchemaFunction() {
        return "SCHEMA_NAME()";
    }

    @Override
    public String buildStringAggFunction(String column, String separator) {
        // SQL Server 2017+ 支持 STRING_AGG
        return "STRING_AGG(" + column + ", '" + separator + "')";
    }

    @Override
    public String buildDateFormatFunction(String column) {
        // SQL Server 2012+ 使用 CONVERT，格式 23 为 yyyy-mm-dd
        return "CONVERT(VARCHAR(10), " + column + ", 23)";
    }

    @Override
    public String getValidationQuery() {
        return "SELECT 1";
    }

    @Override
    public boolean supportsIfExistsBeforeTableName() {
        return false;
    }

    @Override
    public String getAddColumnString() {
        return "ADD";
    }

    @Override
    public String getColumnComment(String comment) {
        // SQL Server 使用 sp_addextendedproperty，这里返回空，需要单独处理
        return "";
    }
}
