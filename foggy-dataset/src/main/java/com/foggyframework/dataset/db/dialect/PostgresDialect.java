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
 * PostgreSQL 12+ 方言实现
 */
public class PostgresDialect extends FDialect {

    public PostgresDialect() {
        super();
        // PostgreSQL 特有类型映射
        registerColumnType(Types.BIT, "boolean");
        registerColumnType(Types.BOOLEAN, "boolean");
        registerColumnType(Types.BIGINT, "bigint");
        registerColumnType(Types.SMALLINT, "smallint");
        registerColumnType(Types.TINYINT, "smallint");  // PostgreSQL 无 tinyint
        registerColumnType(Types.INTEGER, "integer");
        registerColumnType(Types.CHAR, "char(1)");
        registerColumnType(Types.FLOAT, "real");
        registerColumnType(Types.DOUBLE, "double precision");
        registerColumnType(Types.DATE, "date");
        registerColumnType(Types.TIME, "time");
        registerColumnType(Types.TIMESTAMP, "timestamp");
        registerColumnType(Types.VARBINARY, "bytea");
        registerColumnType(Types.LONGVARBINARY, "bytea");
        registerColumnType(Types.BINARY, "bytea");
        registerColumnType(Types.BLOB, "bytea");
        registerColumnType(Types.CLOB, "text");
        registerColumnType(Types.NCLOB, "text");
        registerColumnType(Types.NUMERIC, "numeric($p,$s)");
        registerColumnType(Types.OTHER, "jsonb");  // JSON 类型
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
        return "POSTGRESQL";
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
        return "SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema()";
    }

    @Override
    public DbType getDbType() {
        return DbType.POSTGRESQL;
    }

    @Override
    public String buildNullOrderClause(String columnExpr, boolean nullsFirst) {
        // PostgreSQL 原生支持 NULLS FIRST/LAST
        return columnExpr + (nullsFirst ? " NULLS FIRST" : " NULLS LAST");
    }

    @Override
    public boolean supportsNativeNullsOrdering() {
        return true;
    }

    @Override
    public String getColumnMetadataSql() {
        return "SELECT c.column_name, c.character_maximum_length, " +
               "CASE WHEN pk.constraint_type = 'PRIMARY KEY' THEN 'PRI' ELSE '' END AS column_key, " +
               "c.data_type " +
               "FROM information_schema.columns c " +
               "LEFT JOIN information_schema.key_column_usage kcu " +
               "  ON c.table_name = kcu.table_name AND c.column_name = kcu.column_name AND c.table_schema = kcu.table_schema " +
               "LEFT JOIN information_schema.table_constraints pk " +
               "  ON kcu.constraint_name = pk.constraint_name AND pk.constraint_type = 'PRIMARY KEY' " +
               "WHERE c.table_name = ? AND c.table_schema = ?";
    }

    @Override
    public String getCurrentSchemaFunction() {
        return "current_schema()";
    }

    @Override
    public String buildStringAggFunction(String column, String separator) {
        return "STRING_AGG(" + column + "::text, '" + separator + "')";
    }

    @Override
    public String buildDateFormatFunction(String column) {
        return "TO_CHAR(" + column + ", 'YYYY-MM-DD')";
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
        // PostgreSQL 使用 COMMENT ON COLUMN 语法，这里返回空，需要单独处理
        return "";
    }
}
