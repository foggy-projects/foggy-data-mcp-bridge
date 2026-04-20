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
import java.util.List;

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

        // 检查是否有顶层 ORDER BY（排除 OVER() 子句内的 ORDER BY）
        if (!hasTopLevelOrderBy(sql)) {
            sb.append(" ORDER BY (SELECT NULL)");
        }

        sb.append(" OFFSET ").append(start).append(" ROWS");
        sb.append(" FETCH NEXT ").append(limit).append(" ROWS ONLY");
        return sb.toString();
    }

    /**
     * 检测 SQL 中是否存在顶层 ORDER BY（不在括号内）。
     * <p>
     * 简单的 {@code sql.contains("ORDER BY")} 会误匹配
     * {@code OVER(ORDER BY ...)} 等子句内的 ORDER BY，
     * 导致误判 SQL 已含排序，从而省略 {@code ORDER BY (SELECT NULL)}，
     * 而 SQL Server 的 OFFSET...FETCH 必须紧跟顶层 ORDER BY。
     * </p>
     */
    private boolean hasTopLevelOrderBy(String sql) {
        String upper = sql.toUpperCase();
        int depth = 0;
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0 && c == 'O' && i + 8 <= upper.length()
                    && upper.startsWith("ORDER BY", i)) {
                // 确保不是某个标识符的一部分（如 REORDER_BY）
                if (i == 0 || !Character.isLetterOrDigit(upper.charAt(i - 1))) {
                    return true;
                }
            }
        }
        return false;
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

    @Override
    public String buildFunctionCall(String funcName, List<String> argsSql) {
        if (funcName == null || argsSql == null) return null;
        switch (funcName.toUpperCase()) {
            // SQL Server 原生支持 YEAR(), MONTH(), DAY()
            case "HOUR":
                return argsSql.size() == 1 ? "DATEPART(HOUR, " + argsSql.get(0) + ")" : null;
            case "MINUTE":
                return argsSql.size() == 1 ? "DATEPART(MINUTE, " + argsSql.get(0) + ")" : null;
            case "SECOND":
                return argsSql.size() == 1 ? "DATEPART(SECOND, " + argsSql.get(0) + ")" : null;
            case "DATE_FORMAT":
                if (argsSql.size() == 2) {
                    return "FORMAT(" + argsSql.get(0) + ", " + translateMysqlDateFormatToSqlServer(argsSql.get(1)) + ")";
                }
                return null;
            default:
                return null;
        }
    }

    /**
     * 将 MySQL DATE_FORMAT 格式字符串转换为 SQL Server FORMAT 格式
     */
    private String translateMysqlDateFormatToSqlServer(String mysqlFormat) {
        String fmt = mysqlFormat;
        if (fmt.startsWith("'") && fmt.endsWith("'")) {
            fmt = fmt.substring(1, fmt.length() - 1);
        }
        fmt = fmt.replace("%Y", "yyyy")
                 .replace("%y", "yy")
                 .replace("%m", "MM")
                 .replace("%d", "dd")
                 .replace("%H", "HH")
                 .replace("%i", "mm")
                 .replace("%s", "ss");
        return "'" + fmt + "'";
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
            case "IFNULL":      // MySQL → SQL Server
            case "NVL":         // Oracle → SQL Server
                return "ISNULL";
            case "POW":         // MySQL → SQL Server
                return "POWER";
            case "CEIL":        // MySQL/PG → SQL Server
                return "CEILING";
            case "SUBSTR":      // MySQL/PG/SQLite → SQL Server
                return "SUBSTRING";
            case "LENGTH":      // MySQL/PG/SQLite → SQL Server
            case "CHAR_LENGTH": // MySQL → SQL Server
                return "LEN";
            default:
                return funcName;
        }
    }

    @Override
    public String buildStatFunction(String funcName, String column) {
        switch (funcName) {
            case "STDDEV_POP": return "STDEVP(" + column + ")";
            case "STDDEV_SAMP": return "STDEV(" + column + ")";
            case "VAR_POP": return "VARP(" + column + ")";
            case "VAR_SAMP": return "VAR(" + column + ")";
            default: return funcName + "(" + column + ")";
        }
    }

    @Override
    public String buildStatFunction(String funcName) {
        switch (funcName) {
            case "STDDEV_POP": return "STDEVP";
            case "STDDEV_SAMP": return "STDEV";
            case "VAR_POP": return "VARP";
            case "VAR_SAMP": return "VAR";
            default: return funcName;
        }
    }

    // ==================== 预聚合 SQL 构建支持 ====================

    @Override
    public String buildDateTruncateExpression(String column, String granularity) {
        if (granularity == null) return column;
        switch (granularity.toUpperCase()) {
            case "YEAR":
                return "DATEFROMPARTS(YEAR(" + column + "), 1, 1)";
            case "QUARTER":
                return "DATEFROMPARTS(YEAR(" + column + "), (DATEPART(QUARTER, " + column + ") - 1) * 3 + 1, 1)";
            case "MONTH":
                return "DATEFROMPARTS(YEAR(" + column + "), MONTH(" + column + "), 1)";
            case "WEEK":
                return "DATEADD(DAY, -(DATEPART(WEEKDAY, " + column + ") - 1), CAST(" + column + " AS DATE))";
            case "DAY":
                return "CAST(" + column + " AS DATE)";
            case "HOUR":
                return "DATEADD(HOUR, DATEDIFF(HOUR, 0, " + column + "), 0)";
            case "MINUTE":
                return "DATEADD(MINUTE, DATEDIFF(MINUTE, 0, " + column + "), 0)";
            default:
                return column;
        }
    }

    @Override
    public String buildCurrentTimestampExpression() {
        return "GETDATE()";
    }

    /**
     * SQL Server: {@code DATEDIFF(day, b, a)} · <b>参数顺序反</b> —— unit 在前，小的在前、大的在后
     * <p>与 MySQL 的 {@code DATEDIFF(a, b)} 刚好相反；本接口约定 a 是被减数、b 是减数。</p>
     */
    @Override
    public String buildDateDiffExpression(String a, String b) {
        return "DATEDIFF(day, " + b + ", " + a + ")";
    }

    /**
     * SQL Server: {@code DATEADD(day, ?, d)}
     */
    @Override
    public String buildDateAddExpression(String d, String nParamPlaceholder, String unit) {
        String mssqlUnit = toMssqlUnit(unit);
        return "DATEADD(" + mssqlUnit + ", " + nParamPlaceholder + ", " + d + ")";
    }

    private static String toMssqlUnit(String unit) {
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
        switch (abstractType.toUpperCase()) {
            case "DATETIME":
                return "DATETIME2";
            default:
                return abstractType;
        }
    }
}
