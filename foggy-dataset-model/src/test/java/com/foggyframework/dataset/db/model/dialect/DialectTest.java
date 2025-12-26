package com.foggyframework.dataset.db.model.dialect;

import com.foggyframework.dataset.db.dialect.DbType;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.dialect.MysqlDialect;
import com.foggyframework.dataset.db.dialect.PostgresDialect;
import com.foggyframework.dataset.db.dialect.SqlServerDialect;
import com.foggyframework.dataset.db.dialect.SqliteDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据库方言单元测试
 * 测试各数据库方言的 SQL 生成功能，不依赖实际数据库连接
 */
@DisplayName("数据库方言测试")
public class DialectTest {

    // ==========================================
    // MySQL 方言测试
    // ==========================================
    @Nested
    @DisplayName("MySQL 方言测试")
    class MysqlDialectTest {
        private final FDialect dialect = FDialect.MYSQL_DIALECT;

        @Test
        @DisplayName("测试标识符引用 - 使用反引号")
        void testQuoteIdentifier() {
            assertEquals("`table_name`", dialect.quoteIdentifier("table_name"));
            assertEquals("`column`", dialect.quoteIdentifier("column"));
        }

        @Test
        @DisplayName("测试分页SQL生成")
        void testGeneratePagingSql() {
            String sql = "SELECT * FROM users";

            // 只有 limit
            assertEquals("SELECT * FROM users limit 10", dialect.generatePagingSql(sql, 0, 10));

            // offset + limit
            assertEquals("SELECT * FROM users limit 20,10", dialect.generatePagingSql(sql, 20, 10));
        }

        @Test
        @DisplayName("测试NULL排序子句 - CASE WHEN模拟")
        void testBuildNullOrderClause() {
            // NULLS FIRST
            String nullsFirst = dialect.buildNullOrderClause("price", true);
            assertTrue(nullsFirst.contains("IS NOT NULL"));
            assertTrue(nullsFirst.contains("price"));

            // NULLS LAST
            String nullsLast = dialect.buildNullOrderClause("price", false);
            assertTrue(nullsLast.contains("IS NOT NULL"));
            assertTrue(nullsLast.contains("DESC"));
        }

        @Test
        @DisplayName("测试不支持原生NULLS排序")
        void testSupportsNativeNullsOrdering() {
            assertFalse(dialect.supportsNativeNullsOrdering());
        }

        @Test
        @DisplayName("测试字符串聚合函数 - GROUP_CONCAT")
        void testBuildStringAggFunction() {
            String result = dialect.buildStringAggFunction("name", ",");
            assertEquals("GROUP_CONCAT(name SEPARATOR ',')", result);
        }

        @Test
        @DisplayName("测试获取当前Schema函数")
        void testGetCurrentSchemaFunction() {
            assertEquals("DATABASE()", dialect.getCurrentSchemaFunction());
        }

        @Test
        @DisplayName("测试列元数据查询SQL")
        void testGetColumnMetadataSql() {
            String sql = dialect.getColumnMetadataSql();
            assertTrue(sql.contains("information_schema.COLUMNS"));
            assertTrue(sql.contains("table_name"));
            assertTrue(sql.contains("table_schema"));
        }

        @Test
        @DisplayName("测试数据库类型")
        void testGetDbType() {
            assertEquals(DbType.MYSQL, dialect.getDbType());
        }
    }

    // ==========================================
    // PostgreSQL 方言测试
    // ==========================================
    @Nested
    @DisplayName("PostgreSQL 方言测试")
    class PostgresDialectTest {
        private final FDialect dialect = FDialect.POSTGRES_DIALECT;

        @Test
        @DisplayName("测试标识符引用 - 使用双引号")
        void testQuoteIdentifier() {
            assertEquals("\"table_name\"", dialect.quoteIdentifier("table_name"));
            assertEquals("\"column\"", dialect.quoteIdentifier("column"));
        }

        @Test
        @DisplayName("测试分页SQL生成 - LIMIT OFFSET语法")
        void testGeneratePagingSql() {
            String sql = "SELECT * FROM users";

            // 只有 limit
            assertEquals("SELECT * FROM users LIMIT 10", dialect.generatePagingSql(sql, 0, 10));

            // offset + limit
            assertEquals("SELECT * FROM users LIMIT 10 OFFSET 20", dialect.generatePagingSql(sql, 20, 10));
        }

        @Test
        @DisplayName("测试NULL排序子句 - 原生NULLS FIRST/LAST")
        void testBuildNullOrderClause() {
            // NULLS FIRST
            assertEquals("price NULLS FIRST", dialect.buildNullOrderClause("price", true));

            // NULLS LAST
            assertEquals("price NULLS LAST", dialect.buildNullOrderClause("price", false));
        }

        @Test
        @DisplayName("测试支持原生NULLS排序")
        void testSupportsNativeNullsOrdering() {
            assertTrue(dialect.supportsNativeNullsOrdering());
        }

        @Test
        @DisplayName("测试字符串聚合函数 - STRING_AGG")
        void testBuildStringAggFunction() {
            String result = dialect.buildStringAggFunction("name", ",");
            // PostgreSQL 需要显式类型转换 ::text
            assertEquals("STRING_AGG(name::text, ',')", result);
        }

        @Test
        @DisplayName("测试获取当前Schema函数")
        void testGetCurrentSchemaFunction() {
            // PostgreSQL 使用小写函数名
            assertEquals("current_schema()", dialect.getCurrentSchemaFunction());
        }

        @Test
        @DisplayName("测试列元数据查询SQL")
        void testGetColumnMetadataSql() {
            String sql = dialect.getColumnMetadataSql();
            assertTrue(sql.contains("information_schema.columns"));
            assertTrue(sql.contains("table_name"));
            assertTrue(sql.contains("table_schema"));
        }

        @Test
        @DisplayName("测试数据库类型")
        void testGetDbType() {
            assertEquals(DbType.POSTGRESQL, dialect.getDbType());
        }
    }

    // ==========================================
    // SQL Server 方言测试
    // ==========================================
    @Nested
    @DisplayName("SQL Server 方言测试")
    class SqlServerDialectTest {
        private final FDialect dialect = FDialect.SQLSERVER_DIALECT;

        @Test
        @DisplayName("测试标识符引用 - 使用方括号")
        void testQuoteIdentifier() {
            assertEquals("[table_name]", dialect.quoteIdentifier("table_name"));
            assertEquals("[column]", dialect.quoteIdentifier("column"));
        }

        @Test
        @DisplayName("测试分页SQL生成 - OFFSET FETCH语法")
        void testGeneratePagingSql() {
            String sql = "SELECT * FROM users";

            // 只有 limit (需要添加默认 ORDER BY)
            String result1 = dialect.generatePagingSql(sql, 0, 10);
            assertTrue(result1.contains("ORDER BY"));
            assertTrue(result1.contains("OFFSET 0 ROWS FETCH NEXT 10 ROWS ONLY"));

            // offset + limit
            String result2 = dialect.generatePagingSql(sql, 20, 10);
            assertTrue(result2.contains("OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY"));
        }

        @Test
        @DisplayName("测试带ORDER BY的分页SQL不重复添加ORDER BY")
        void testGeneratePagingSqlWithExistingOrderBy() {
            String sql = "SELECT * FROM users ORDER BY name";
            String result = dialect.generatePagingSql(sql, 0, 10);

            // 只应有一个 ORDER BY
            int count = result.split("ORDER BY").length - 1;
            assertEquals(1, count, "应该只有一个ORDER BY子句");
            assertTrue(result.contains("OFFSET 0 ROWS FETCH NEXT 10 ROWS ONLY"));
        }

        @Test
        @DisplayName("测试NULL排序子句 - CASE WHEN模拟")
        void testBuildNullOrderClause() {
            // NULLS FIRST
            String nullsFirst = dialect.buildNullOrderClause("price", true);
            assertTrue(nullsFirst.contains("CASE WHEN"));
            assertTrue(nullsFirst.contains("IS NULL"));

            // NULLS LAST
            String nullsLast = dialect.buildNullOrderClause("price", false);
            assertTrue(nullsLast.contains("CASE WHEN"));
            assertTrue(nullsLast.contains("IS NULL"));
        }

        @Test
        @DisplayName("测试不支持原生NULLS排序")
        void testSupportsNativeNullsOrdering() {
            assertFalse(dialect.supportsNativeNullsOrdering());
        }

        @Test
        @DisplayName("测试字符串聚合函数 - STRING_AGG")
        void testBuildStringAggFunction() {
            String result = dialect.buildStringAggFunction("name", ",");
            assertEquals("STRING_AGG(name, ',')", result);
        }

        @Test
        @DisplayName("测试获取当前Schema函数")
        void testGetCurrentSchemaFunction() {
            assertEquals("SCHEMA_NAME()", dialect.getCurrentSchemaFunction());
        }

        @Test
        @DisplayName("测试列元数据查询SQL")
        void testGetColumnMetadataSql() {
            String sql = dialect.getColumnMetadataSql();
            assertTrue(sql.contains("INFORMATION_SCHEMA.COLUMNS"));
            assertTrue(sql.contains("TABLE_NAME"));
            assertTrue(sql.contains("TABLE_SCHEMA") || sql.contains("TABLE_CATALOG"));
        }

        @Test
        @DisplayName("测试数据库类型")
        void testGetDbType() {
            assertEquals(DbType.SQLSERVER, dialect.getDbType());
        }
    }

    // ==========================================
    // SQLite 方言测试
    // ==========================================
    @Nested
    @DisplayName("SQLite 方言测试")
    class SqliteDialectTest {
        private final FDialect dialect = FDialect.SQLITE_DIALECT;

        @Test
        @DisplayName("测试标识符引用 - 使用双引号")
        void testQuoteIdentifier() {
            assertEquals("\"table_name\"", dialect.quoteIdentifier("table_name"));
            assertEquals("\"column\"", dialect.quoteIdentifier("column"));
        }

        @Test
        @DisplayName("测试分页SQL生成 - LIMIT OFFSET语法")
        void testGeneratePagingSql() {
            String sql = "SELECT * FROM users";

            // 只有 limit
            assertEquals("SELECT * FROM users LIMIT 10", dialect.generatePagingSql(sql, 0, 10));

            // offset + limit
            assertEquals("SELECT * FROM users LIMIT 10 OFFSET 20", dialect.generatePagingSql(sql, 20, 10));
        }

        @Test
        @DisplayName("测试NULL排序子句 - 原生NULLS FIRST/LAST")
        void testBuildNullOrderClause() {
            // NULLS FIRST (SQLite 3.30+支持)
            assertEquals("price NULLS FIRST", dialect.buildNullOrderClause("price", true));

            // NULLS LAST
            assertEquals("price NULLS LAST", dialect.buildNullOrderClause("price", false));
        }

        @Test
        @DisplayName("测试支持原生NULLS排序 (SQLite 3.30+)")
        void testSupportsNativeNullsOrdering() {
            assertTrue(dialect.supportsNativeNullsOrdering());
        }

        @Test
        @DisplayName("测试字符串聚合函数 - GROUP_CONCAT")
        void testBuildStringAggFunction() {
            String result = dialect.buildStringAggFunction("name", ",");
            assertEquals("GROUP_CONCAT(name, ',')", result);
        }

        @Test
        @DisplayName("测试获取当前Schema函数 - SQLite无schema概念")
        void testGetCurrentSchemaFunction() {
            assertEquals("'main'", dialect.getCurrentSchemaFunction());
        }

        @Test
        @DisplayName("测试列元数据查询SQL - 使用PRAGMA")
        void testGetColumnMetadataSql() {
            String sql = dialect.getColumnMetadataSql();
            assertTrue(sql.contains("PRAGMA") || sql.isEmpty());
        }

        @Test
        @DisplayName("测试数据库类型")
        void testGetDbType() {
            assertEquals(DbType.SQLITE, dialect.getDbType());
        }
    }

    // ==========================================
    // 通用方言功能测试
    // ==========================================
    @Nested
    @DisplayName("通用方言功能测试")
    class CommonDialectTest {

        @Test
        @DisplayName("测试所有方言都已正确初始化")
        void testAllDialectsInitialized() {
            assertNotNull(FDialect.MYSQL_DIALECT, "MySQL dialect should be initialized");
            assertNotNull(FDialect.POSTGRES_DIALECT, "PostgreSQL dialect should be initialized");
            assertNotNull(FDialect.SQLSERVER_DIALECT, "SQL Server dialect should be initialized");
            assertNotNull(FDialect.SQLITE_DIALECT, "SQLite dialect should be initialized");
        }

        @Test
        @DisplayName("测试完整表名构建")
        void testGetQualifiedTableName() {
            FDialect mysql = FDialect.MYSQL_DIALECT;
            FDialect postgres = FDialect.POSTGRES_DIALECT;
            FDialect sqlserver = FDialect.SQLSERVER_DIALECT;

            // 无schema
            assertEquals("`users`", mysql.getQualifiedTableName(null, "users"));
            assertEquals("\"users\"", postgres.getQualifiedTableName(null, "users"));
            assertEquals("[users]", sqlserver.getQualifiedTableName(null, "users"));

            // 有schema
            assertEquals("`mydb`.`users`", mysql.getQualifiedTableName("mydb", "users"));
            assertEquals("\"public\".\"users\"", postgres.getQualifiedTableName("public", "users"));
            assertEquals("[dbo].[users]", sqlserver.getQualifiedTableName("dbo", "users"));
        }

        @Test
        @DisplayName("测试NULL标识符处理")
        void testQuoteNullIdentifier() {
            assertNull(FDialect.MYSQL_DIALECT.quoteIdentifier(null));
            assertNull(FDialect.POSTGRES_DIALECT.quoteIdentifier(null));
            assertNull(FDialect.SQLSERVER_DIALECT.quoteIdentifier(null));
            assertNull(FDialect.SQLITE_DIALECT.quoteIdentifier(null));
        }

        @Test
        @DisplayName("测试验证查询")
        void testValidationQuery() {
            // 所有数据库都应该支持 SELECT 1
            assertTrue(FDialect.MYSQL_DIALECT.getValidationQuery().toLowerCase().contains("select"));
            assertTrue(FDialect.POSTGRES_DIALECT.getValidationQuery().toLowerCase().contains("select"));
            assertTrue(FDialect.SQLSERVER_DIALECT.getValidationQuery().toLowerCase().contains("select"));
            assertTrue(FDialect.SQLITE_DIALECT.getValidationQuery().toLowerCase().contains("select"));
        }
    }
}
