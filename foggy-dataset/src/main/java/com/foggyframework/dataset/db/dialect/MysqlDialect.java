package com.foggyframework.dataset.db.dialect;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;

import java.sql.Types;

public  class MysqlDialect extends FDialect {
    public static final String geometry = "geometry";
    public static final int geometry_type = 4000;
    public MysqlDialect() {
        super();
        registerColumnType(geometry_type, geometry);
        registerColumnType(Types.BIT, "bit");
        registerColumnType(Types.BIGINT, "bigint");
        registerColumnType(Types.SMALLINT, "smallint");
        registerColumnType(Types.TINYINT, "tinyint");
        registerColumnType(Types.INTEGER, "integer");
        registerColumnType(Types.CHAR, "char(1)");
        registerColumnType(Types.FLOAT, "float");
        registerColumnType(Types.DOUBLE, "double precision");
        registerColumnType(Types.BOOLEAN, "bit"); // HHH-6935
        registerColumnType(Types.DATE, "date");
        registerColumnType(Types.TIME, "time");
        registerColumnType(Types.TIMESTAMP, "datetime");
        registerColumnType(Types.VARBINARY, "longblob");
        registerColumnType(Types.VARBINARY, 16777215, "mediumblob");
        registerColumnType(Types.VARBINARY, 65535, "blob");
        registerColumnType(Types.VARBINARY, 255, "tinyblob");
        registerColumnType(Types.BINARY, "binary($l)");
        registerColumnType(Types.LONGVARBINARY, "longblob");
        registerColumnType(Types.LONGVARBINARY, 16777215, "mediumblob");
        registerColumnType(Types.NUMERIC, "decimal($p,$s)");
        registerColumnType(Types.BLOB, "longblob");
//		registerColumnType( Types.BLOB, 16777215, "mediumblob" );
//		registerColumnType( Types.BLOB, 65535, "blob" );
        registerColumnType(Types.CLOB, "longtext");
        registerColumnType(Types.NCLOB, "longtext");
    }
    @Override
    public String generatePagingSql(String sql, int start, int limit) {
        return new StringBuffer(sql.length() + 20).append(sql)
                .append(start > 0 ? (" limit " + start + "," + limit) : " limit " + limit).toString();
    }
    public String getTypeName(int code, int length, int precision, int scale) {
        if(code == Types.LONGVARCHAR){
            return "longtext";
        }
        if(code == Types.OTHER){
            return "json";
        }
        return super.getTypeName(code,length,precision,scale);
    }
    public String getProductName() {
        return "MYSQL";
    }

    public String getQueryTableAndViewsSql() {

        return "SELECT T.TABLE_NAME FROM information_schema.TABLES T where T.TABLE_SCHEMA=DATABASE()";
    }

    @Override
    public DbType getDbType() {
        return DbType.MYSQL;
    }

    protected String getTableByNameSql(String name) {
        return "SELECT  TABLES.TABLE_NAME FROM  information_schema.TABLES WHERE TABLES.TABLE_NAME = '" + name + "'";
    }

    // String schema = null;
    public String getValidationQuery() {
        return "select 1";
    }

    public boolean hasPagingNumColumn() {
        return false;
    }

    @Override
    public char openQuote() {
        return '`';
    }

    @Override
    public char closeQuote() {
        return '`';
    }

    @Override
    public boolean supportsIfExistsBeforeTableName() {
        return true;
    }

    @Override
    public String getAddColumnString() {
        return "add column";
    }
    public String getColumnComment(String comment) {
        return " comment '" + comment + "'";
    }

    @Override
    public String buildNullOrderClause(String columnExpr, boolean nullsFirst) {
        // MySQL 8.0 以下不支持原生 NULLS FIRST/LAST，使用 CASE WHEN 模拟
        if (nullsFirst) {
            return "(" + columnExpr + ") IS NOT NULL, " + columnExpr;
        } else {
            return "(" + columnExpr + ") IS NOT NULL DESC, " + columnExpr;
        }
    }

    @Override
    public String getColumnMetadataSql() {
        return "SELECT column_NAME, CHARACTER_MAXIMUM_LENGTH, column_key, DATA_TYPE " +
               "FROM information_schema.COLUMNS WHERE table_name = ? AND table_schema = ?";
    }

    @Override
    public String getCurrentSchemaFunction() {
        return "DATABASE()";
    }

    @Override
    public String buildStringAggFunction(String column, String separator) {
        return "GROUP_CONCAT(" + column + " SEPARATOR '" + separator + "')";
    }

    @Override
    public String buildDateFormatFunction(String column) {
        return "DATE_FORMAT(" + column + ",'%Y-%m-%d')";
    }

    @Override
    protected void handleSpecialDataType(SqlTable st, String columnName, String dataType) {
        SqlColumn col = st.getSqlColumn(columnName, true);
        if (col == null) {
            return;
        }
        if (StringUtils.equals(dataType, "longtext")) {
            col.setJdbcType(Types.LONGVARCHAR);
        } else if (StringUtils.equalsIgnoreCase(dataType, "json")) {
            col.setJdbcType(Types.OTHER);
        } else if (StringUtils.equals(dataType, geometry)) {
            col.setJdbcType(geometry_type);
        }
    }
}
