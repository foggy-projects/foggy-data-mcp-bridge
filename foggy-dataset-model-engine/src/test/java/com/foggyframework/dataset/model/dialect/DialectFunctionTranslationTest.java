package com.foggyframework.dataset.model.dialect;

import com.foggyframework.dataset.db.dialect.FDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 方言函数名转换测试（#007 复现 + 验证）
 *
 * <p>验证 FDialect.translateFunction() 在各方言下正确转换函数名。</p>
 * <p>
 * 核心映射规则：
 * <ul>
 *     <li>IFNULL — MySQL/SQLite 原生，PostgreSQL → COALESCE，SQL Server → ISNULL</li>
 *     <li>NVL — Oracle 原生，其他方言各自适配</li>
 *     <li>ISNULL — SQL Server 原生，PostgreSQL → COALESCE，MySQL → IFNULL</li>
 *     <li>POW — MySQL/PG 原生，SQL Server → POWER</li>
 *     <li>CEIL — MySQL/PG 原生，SQL Server → CEILING</li>
 *     <li>TRUNCATE — MySQL 原生，PostgreSQL → TRUNC</li>
 *     <li>SUBSTR — MySQL/SQLite/PG 原生，SQL Server → SUBSTRING</li>
 *     <li>LENGTH — MySQL/SQLite/PG 原生，SQL Server → LEN</li>
 * </ul>
 * </p>
 *
 * @see com.foggyframework.dataset.db.dialect.FDialect#translateFunction(String)
 */
@DisplayName("方言函数名转换测试（#007）")
public class DialectFunctionTranslationTest {

    // ==========================================
    // MySQL 方言函数转换
    // ==========================================
    @Nested
    @DisplayName("MySQL 函数转换")
    class MysqlFunctionTest {
        private final FDialect dialect = FDialect.MYSQL_DIALECT;

        @Test
        @DisplayName("IFNULL 在 MySQL 中保持不变")
        void testIfnullKeepsOriginal() {
            assertEquals("IFNULL", dialect.translateFunction("IFNULL"));
        }

        @Test
        @DisplayName("NVL 在 MySQL 中转为 IFNULL")
        void testNvlToIfnull() {
            assertEquals("IFNULL", dialect.translateFunction("NVL"));
        }

        @Test
        @DisplayName("ISNULL 在 MySQL 中转为 IFNULL")
        void testIsnullToIfnull() {
            assertEquals("IFNULL", dialect.translateFunction("ISNULL"));
        }

        @Test
        @DisplayName("COALESCE 在 MySQL 中保持不变")
        void testCoalesceKeepsOriginal() {
            assertEquals("COALESCE", dialect.translateFunction("COALESCE"));
        }

        @Test
        @DisplayName("TRUNC 在 MySQL 中转为 TRUNCATE")
        void testTruncToTruncate() {
            assertEquals("TRUNCATE", dialect.translateFunction("TRUNC"));
        }

        @Test
        @DisplayName("SUBSTR 在 MySQL 中保持不变")
        void testSubstrKeepsOriginal() {
            assertEquals("SUBSTR", dialect.translateFunction("SUBSTR"));
        }

        @Test
        @DisplayName("LENGTH 在 MySQL 中保持不变")
        void testLengthKeepsOriginal() {
            assertEquals("LENGTH", dialect.translateFunction("LENGTH"));
        }

        @Test
        @DisplayName("无需转换的函数原样返回")
        void testPassthroughFunctions() {
            assertEquals("ABS", dialect.translateFunction("ABS"));
            assertEquals("ROUND", dialect.translateFunction("ROUND"));
            assertEquals("UPPER", dialect.translateFunction("UPPER"));
            assertEquals("CONCAT", dialect.translateFunction("CONCAT"));
            assertEquals("SUM", dialect.translateFunction("SUM"));
        }

        @Test
        @DisplayName("小写输入也能正确转换")
        void testCaseInsensitive() {
            assertEquals("IFNULL", dialect.translateFunction("nvl"));
            assertEquals("IFNULL", dialect.translateFunction("isnull"));
        }
    }

    // ==========================================
    // PostgreSQL 方言函数转换
    // ==========================================
    @Nested
    @DisplayName("PostgreSQL 函数转换")
    class PostgresFunctionTest {
        private final FDialect dialect = FDialect.POSTGRES_DIALECT;

        @Test
        @DisplayName("IFNULL 在 PostgreSQL 中转为 COALESCE")
        void testIfnullToCoalesce() {
            assertEquals("COALESCE", dialect.translateFunction("IFNULL"));
        }

        @Test
        @DisplayName("NVL 在 PostgreSQL 中转为 COALESCE")
        void testNvlToCoalesce() {
            assertEquals("COALESCE", dialect.translateFunction("NVL"));
        }

        @Test
        @DisplayName("ISNULL 在 PostgreSQL 中转为 COALESCE")
        void testIsnullToCoalesce() {
            assertEquals("COALESCE", dialect.translateFunction("ISNULL"));
        }

        @Test
        @DisplayName("COALESCE 在 PostgreSQL 中保持不变")
        void testCoalesceKeepsOriginal() {
            assertEquals("COALESCE", dialect.translateFunction("COALESCE"));
        }

        @Test
        @DisplayName("TRUNCATE 在 PostgreSQL 中转为 TRUNC")
        void testTruncateToTrunc() {
            assertEquals("TRUNC", dialect.translateFunction("TRUNCATE"));
        }

        @Test
        @DisplayName("POW 在 PostgreSQL 中转为 POWER")
        void testPowToPower() {
            assertEquals("POWER", dialect.translateFunction("POW"));
        }

        @Test
        @DisplayName("SUBSTR 在 PostgreSQL 中保持不变")
        void testSubstrKeepsOriginal() {
            assertEquals("SUBSTR", dialect.translateFunction("SUBSTR"));
        }

        @Test
        @DisplayName("LENGTH 在 PostgreSQL 中保持不变")
        void testLengthKeepsOriginal() {
            assertEquals("LENGTH", dialect.translateFunction("LENGTH"));
        }

        @Test
        @DisplayName("无需转换的函数原样返回")
        void testPassthroughFunctions() {
            assertEquals("ABS", dialect.translateFunction("ABS"));
            assertEquals("ROUND", dialect.translateFunction("ROUND"));
            assertEquals("UPPER", dialect.translateFunction("UPPER"));
        }

        @Test
        @DisplayName("小写输入也能正确转换")
        void testCaseInsensitive() {
            assertEquals("COALESCE", dialect.translateFunction("ifnull"));
            assertEquals("COALESCE", dialect.translateFunction("nvl"));
        }
    }

    // ==========================================
    // SQL Server 方言函数转换
    // ==========================================
    @Nested
    @DisplayName("SQL Server 函数转换")
    class SqlServerFunctionTest {
        private final FDialect dialect = FDialect.SQLSERVER_DIALECT;

        @Test
        @DisplayName("IFNULL 在 SQL Server 中转为 ISNULL")
        void testIfnullToIsnull() {
            assertEquals("ISNULL", dialect.translateFunction("IFNULL"));
        }

        @Test
        @DisplayName("NVL 在 SQL Server 中转为 ISNULL")
        void testNvlToIsnull() {
            assertEquals("ISNULL", dialect.translateFunction("NVL"));
        }

        @Test
        @DisplayName("ISNULL 在 SQL Server 中保持不变")
        void testIsnullKeepsOriginal() {
            assertEquals("ISNULL", dialect.translateFunction("ISNULL"));
        }

        @Test
        @DisplayName("COALESCE 在 SQL Server 中保持不变")
        void testCoalesceKeepsOriginal() {
            assertEquals("COALESCE", dialect.translateFunction("COALESCE"));
        }

        @Test
        @DisplayName("POW 在 SQL Server 中转为 POWER")
        void testPowToPower() {
            assertEquals("POWER", dialect.translateFunction("POW"));
        }

        @Test
        @DisplayName("CEIL 在 SQL Server 中转为 CEILING")
        void testCeilToCeiling() {
            assertEquals("CEILING", dialect.translateFunction("CEIL"));
        }

        @Test
        @DisplayName("SUBSTR 在 SQL Server 中转为 SUBSTRING")
        void testSubstrToSubstring() {
            assertEquals("SUBSTRING", dialect.translateFunction("SUBSTR"));
        }

        @Test
        @DisplayName("LENGTH 在 SQL Server 中转为 LEN")
        void testLengthToLen() {
            assertEquals("LEN", dialect.translateFunction("LENGTH"));
        }

        @Test
        @DisplayName("CHAR_LENGTH 在 SQL Server 中转为 LEN")
        void testCharLengthToLen() {
            assertEquals("LEN", dialect.translateFunction("CHAR_LENGTH"));
        }

        @Test
        @DisplayName("无需转换的函数原样返回")
        void testPassthroughFunctions() {
            assertEquals("ABS", dialect.translateFunction("ABS"));
            assertEquals("ROUND", dialect.translateFunction("ROUND"));
            assertEquals("UPPER", dialect.translateFunction("UPPER"));
        }

        @Test
        @DisplayName("小写输入也能正确转换")
        void testCaseInsensitive() {
            assertEquals("ISNULL", dialect.translateFunction("ifnull"));
            assertEquals("ISNULL", dialect.translateFunction("nvl"));
        }
    }

    // ==========================================
    // SQLite 方言函数转换
    // ==========================================
    @Nested
    @DisplayName("SQLite 函数转换")
    class SqliteFunctionTest {
        private final FDialect dialect = FDialect.SQLITE_DIALECT;

        @Test
        @DisplayName("IFNULL 在 SQLite 中保持不变")
        void testIfnullKeepsOriginal() {
            assertEquals("IFNULL", dialect.translateFunction("IFNULL"));
        }

        @Test
        @DisplayName("NVL 在 SQLite 中转为 IFNULL")
        void testNvlToIfnull() {
            assertEquals("IFNULL", dialect.translateFunction("NVL"));
        }

        @Test
        @DisplayName("ISNULL 在 SQLite 中转为 IFNULL")
        void testIsnullToIfnull() {
            assertEquals("IFNULL", dialect.translateFunction("ISNULL"));
        }

        @Test
        @DisplayName("COALESCE 在 SQLite 中保持不变")
        void testCoalesceKeepsOriginal() {
            assertEquals("COALESCE", dialect.translateFunction("COALESCE"));
        }

        @Test
        @DisplayName("SUBSTR 在 SQLite 中保持不变")
        void testSubstrKeepsOriginal() {
            assertEquals("SUBSTR", dialect.translateFunction("SUBSTR"));
        }

        @Test
        @DisplayName("LENGTH 在 SQLite 中保持不变")
        void testLengthKeepsOriginal() {
            assertEquals("LENGTH", dialect.translateFunction("LENGTH"));
        }

        @Test
        @DisplayName("无需转换的函数原样返回")
        void testPassthroughFunctions() {
            assertEquals("ABS", dialect.translateFunction("ABS"));
            assertEquals("ROUND", dialect.translateFunction("ROUND"));
            assertEquals("UPPER", dialect.translateFunction("UPPER"));
        }

        @Test
        @DisplayName("小写输入也能正确转换")
        void testCaseInsensitive() {
            assertEquals("IFNULL", dialect.translateFunction("nvl"));
            assertEquals("IFNULL", dialect.translateFunction("isnull"));
        }
    }

    // ==========================================
    // 通用 / 边界条件
    // ==========================================
    @Nested
    @DisplayName("通用边界条件")
    class CommonEdgeCaseTest {

        @Test
        @DisplayName("null 输入返回 null")
        void testNullInput() {
            assertNull(FDialect.MYSQL_DIALECT.translateFunction(null));
            assertNull(FDialect.POSTGRES_DIALECT.translateFunction(null));
            assertNull(FDialect.SQLSERVER_DIALECT.translateFunction(null));
            assertNull(FDialect.SQLITE_DIALECT.translateFunction(null));
        }

        @Test
        @DisplayName("空字符串返回空字符串")
        void testEmptyInput() {
            assertEquals("", FDialect.MYSQL_DIALECT.translateFunction(""));
            assertEquals("", FDialect.POSTGRES_DIALECT.translateFunction(""));
        }

        @Test
        @DisplayName("未知函数名原样返回")
        void testUnknownFunction() {
            assertEquals("MY_CUSTOM_FUNC", FDialect.MYSQL_DIALECT.translateFunction("MY_CUSTOM_FUNC"));
            assertEquals("MY_CUSTOM_FUNC", FDialect.POSTGRES_DIALECT.translateFunction("MY_CUSTOM_FUNC"));
        }
    }
}
