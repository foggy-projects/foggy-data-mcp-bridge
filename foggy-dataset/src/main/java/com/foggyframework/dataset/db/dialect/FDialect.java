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
import java.util.Map;

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
                        schema = detectSchema(connection);
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
     * 从 JDBC Connection 检测当前 schema 名。
     * 默认实现：优先 getCatalog()，回退 getSchema()。
     * PostgreSQL 等方言需重写，因为 getCatalog() 返回数据库名而非 schema。
     */
    protected String detectSchema(Connection connection) throws SQLException {
        String schema = connection.getCatalog();
        if (StringUtils.isEmpty(schema)) {
            schema = connection.getSchema();
        }
        return schema;
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

    /**
     * 构建统计函数 SQL（STDDEV_POP / STDDEV_SAMP / VAR_POP / VAR_SAMP）
     * <p>
     * MySQL/PostgreSQL 直接使用标准名称。
     * 子类（如 SqlServerDialect、SqliteDialect）可重写以适配方言差异。
     * </p>
     *
     * @param funcName 标准函数名
     * @param column   列表达式
     * @return 方言特定的 SQL 表达式
     */
    public String buildStatFunction(String funcName, String column) {
        return funcName + "(" + column + ")";
    }

    /**
     * 获取统计函数的方言名称（不含参数）
     * <p>
     * 用于 SqlFunctionExp 中仅翻译函数名，参数由调用方拼接。
     * </p>
     *
     * @param funcName 标准函数名
     * @return 方言特定的函数名
     */
    public String buildStatFunction(String funcName) {
        return funcName;
    }

    /**
     * 构建函数调用的 SQL（需要语法重构的情况）
     * <p>
     * 默认返回 null，表示使用标准 {@code funcName(args)} 格式。
     * 子类可重写以处理需要语法重构的函数，例如：
     * <ul>
     *     <li>PostgreSQL: {@code YEAR(col)} → {@code EXTRACT(YEAR FROM col)}</li>
     *     <li>PostgreSQL: {@code DATE_FORMAT(col, '%Y-%m')} → {@code TO_CHAR(col, 'YYYY-MM')}</li>
     *     <li>SQLite: {@code YEAR(col)} → {@code CAST(strftime('%Y', col) AS INTEGER)}</li>
     * </ul>
     * </p>
     *
     * @param funcName 函数名（原始大小写）
     * @param argsSql  参数 SQL 字符串列表
     * @return 重构后的完整 SQL 表达式，或 null 表示使用默认 {@code funcName(args)} 格式
     */
    public String buildFunctionCall(String funcName, List<String> argsSql) {
        return null;
    }

    /**
     * 转换函数名以适配当前方言
     * <p>
     * 将标准或其他方言的函数名转换为当前方言对应的函数名。
     * 例如：MySQL 的 IFNULL 在 PostgreSQL 中应转换为 COALESCE。
     * 仅做函数名映射（参数结构相同的情况），子类按需重写。
     * </p>
     *
     * @param funcName 函数名（可大小写混合）
     * @return 方言特定的函数名
     */
    public String translateFunction(String funcName) {
        if (funcName == null) {
            return null;
        }
        if (funcName.isEmpty()) {
            return funcName;
        }
        // 默认：不转换，直接返回原函数名
        return funcName;
    }

    // ==================== 预聚合 SQL 构建支持 ====================

    /**
     * 构建日期截断表达式
     * <p>
     * 将日期/时间列截断到指定粒度（如 DAY、MONTH、YEAR 等）。
     * 各数据库使用不同语法实现相同效果。
     * </p>
     *
     * @param column      列名或列表达式
     * @param granularity 粒度名称（MINUTE, HOUR, DAY, WEEK, MONTH, QUARTER, YEAR）
     * @return 截断后的 SQL 表达式
     */
    public String buildDateTruncateExpression(String column, String granularity) {
        // 默认：不截断，直接返回列名
        return column;
    }

    /**
     * 获取当前时间戳的 SQL 表达式
     * <p>
     * ANSI SQL 标准为 {@code CURRENT_TIMESTAMP}，但各数据库有不同习惯。
     * </p>
     *
     * @return 当前时间戳表达式
     */
    public String buildCurrentTimestampExpression() {
        return "CURRENT_TIMESTAMP";
    }

    /**
     * 构建 date_diff(a, b) 日期差值 SQL 表达式 · v1.4 Step 3.4
     * <p>
     * Formula Spec v1 的 {@code date_diff(a, b)} 函数返回 {@code a - b} 的天数（整数）。
     * 各方言语法差异很大，子类必须 override：
     * </p>
     * <ul>
     *   <li>MySQL: {@code DATEDIFF(a, b)}</li>
     *   <li>PostgreSQL: {@code (a::date - b::date)}</li>
     *   <li>SQL Server: {@code DATEDIFF(day, b, a)} ⚠ 参数顺序反</li>
     *   <li>SQLite: {@code CAST((julianday(a) - julianday(b)) AS INTEGER)}</li>
     * </ul>
     *
     * @param a 被减数（SQL 片段，可能是列引用 / 字面量 / 子表达式）
     * @param b 减数
     * @return 方言特定的 SQL 表达式；默认返回 null 让调用方走 fallback
     * @since v1.4
     */
    public String buildDateDiffExpression(String a, String b) {
        return null;
    }

    /**
     * 构建 date_add(d, n, unit) 日期加法 SQL 表达式 · v1.4 Step 3.4
     * <p>
     * Formula Spec v1 的 {@code date_add(d, n, unit)} 函数在 {@code d} 上加 {@code n}
     * 个 unit（day / month / year）。参数 {@code n} 由 {@code ?} 绑定（不要拼接字面量，
     * B-4 安全决策防 SQL 注入）。
     * </p>
     * <ul>
     *   <li>MySQL: {@code DATE_ADD(d, INTERVAL ? DAY|MONTH|YEAR)}</li>
     *   <li>PostgreSQL: {@code (d + make_interval(days => ?))} / {@code months => ?} / {@code years => ?}</li>
     *   <li>SQL Server: {@code DATEADD(day, ?, d)} / {@code month, ?, d} / {@code year, ?, d}</li>
     *   <li>SQLite: {@code date(d, '+' || ? || ' day')} / {@code month} / {@code year}</li>
     * </ul>
     *
     * @param d                   日期 SQL 片段
     * @param nParamPlaceholder   参数占位符（通常是 {@code ?}，由 SqlFragment 绑定值）
     * @param unit                单位：{@code day} / {@code month} / {@code year}（小写）
     * @return 方言特定的 SQL 表达式；默认返回 null 让调用方走 fallback
     * @throws IllegalArgumentException 子类可在 unit 非法时抛出
     * @since v1.4
     */
    public String buildDateAddExpression(String d, String nParamPlaceholder, String unit) {
        return null;
    }

    /**
     * 将抽象列类型映射为方言特定的 DDL 类型
     * <p>
     * 用于预聚合建表 DDL 生成。抽象类型如 "DATE", "DATETIME", "INT", "BIGINT",
     * "VARCHAR(255)", "DECIMAL(20,4)", "TIMESTAMP" 等。
     * </p>
     *
     * @param abstractType 抽象类型名称
     * @return 方言特定的 DDL 类型
     */
    public String mapColumnType(String abstractType) {
        // 默认：直通（适用于 MySQL）
        return abstractType;
    }

    /**
     * 转换参数值以适配不同数据库的参数绑定需求
     * <p>
     * 默认实现：直接返回原值（适用于MySQL、PostgreSQL、SQL Server等）
     * </p>
     * <p>
     * 子类可重写此方法处理特殊情况，例如：
     * <ul>
     *   <li>SQLite: Date类型需转换为TEXT格式字符串（'yyyy-MM-dd HH:mm:ss'）</li>
     *   <li>其他数据库的特殊类型转换需求</li>
     * </ul>
     * </p>
     *
     * @param value 原始参数值
     * @return 转换后的参数值
     */
    public Object convertParameterValue(Object value) {
        // 默认实现：直接返回原值
        return value;
    }

    /**
     * 是否支持 CTE（WITH ... AS）语法
     *
     * <p>默认 true（PostgreSQL 12+、SQL Server 2012+、SQLite 3.35+、MySQL 8.0+ 均支持）。
     * MySQL 5.7 需重写为 false，{@link com.foggyframework.dataset.db.model.engine.compose.CteComposer}
     * 会自动回退为子查询方案。</p>
     *
     * @return true 如果支持 CTE 语法
     */
    public boolean supportsCte() {
        return true;
    }
}
