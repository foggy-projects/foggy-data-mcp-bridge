package com.foggyframework.dataset.model.dialect;

import com.foggyframework.dataset.db.dialect.FDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 方言函数调用构建测试（#Issue1 — YEAR/MONTH/DATE_FORMAT 跨方言适配）
 *
 * <p>验证 FDialect.buildFunctionCall() 在各方言下能正确重写需要语法重构的函数。</p>
 * <p>
 * 核心场景：
 * <ul>
 *     <li>YEAR/MONTH/DAY/HOUR/MINUTE/SECOND — 日期部件提取</li>
 *     <li>DATE_FORMAT — 日期格式化（MySQL 专有语法，需跨方言适配）</li>
 * </ul>
 * </p>
 *
 * @see com.foggyframework.dataset.db.dialect.FDialect#buildFunctionCall(String, java.util.List)
 */
@DisplayName("方言函数调用构建测试（#Issue1）")
public class DialectBuildFunctionCallTest {

    // ==========================================
    // MySQL — YEAR/MONTH/DATE_FORMAT 原生支持，不需要重写
    // ==========================================
    @Nested
    @DisplayName("MySQL — 不需要重写")
    class MysqlTest {
        private final FDialect dialect = FDialect.MYSQL_DIALECT;

        @Test
        @DisplayName("YEAR() 在 MySQL 中不重写（返回 null）")
        void testYearReturnsNull() {
            assertNull(dialect.buildFunctionCall("YEAR", Collections.singletonList("t1.date_order")));
        }

        @Test
        @DisplayName("MONTH() 在 MySQL 中不重写")
        void testMonthReturnsNull() {
            assertNull(dialect.buildFunctionCall("MONTH", Collections.singletonList("t1.date_order")));
        }

        @Test
        @DisplayName("DATE_FORMAT() 在 MySQL 中不重写")
        void testDateFormatReturnsNull() {
            assertNull(dialect.buildFunctionCall("DATE_FORMAT",
                    Arrays.asList("t1.date_order", "'%Y-%m'")));
        }
    }

    // ==========================================
    // PostgreSQL — 核心修复目标
    // ==========================================
    @Nested
    @DisplayName("PostgreSQL — EXTRACT + TO_CHAR")
    class PostgresTest {
        private final FDialect dialect = FDialect.POSTGRES_DIALECT;

        @Test
        @DisplayName("YEAR(col) → EXTRACT(YEAR FROM col)")
        void testYear() {
            String result = dialect.buildFunctionCall("YEAR", Collections.singletonList("t1.date_order"));
            assertEquals("EXTRACT(YEAR FROM t1.date_order)", result);
        }

        @Test
        @DisplayName("MONTH(col) → EXTRACT(MONTH FROM col)")
        void testMonth() {
            String result = dialect.buildFunctionCall("MONTH", Collections.singletonList("t1.date_order"));
            assertEquals("EXTRACT(MONTH FROM t1.date_order)", result);
        }

        @Test
        @DisplayName("DAY(col) → EXTRACT(DAY FROM col)")
        void testDay() {
            String result = dialect.buildFunctionCall("DAY", Collections.singletonList("t1.date_order"));
            assertEquals("EXTRACT(DAY FROM t1.date_order)", result);
        }

        @Test
        @DisplayName("HOUR(col) → EXTRACT(HOUR FROM col)")
        void testHour() {
            String result = dialect.buildFunctionCall("HOUR", Collections.singletonList("t1.created_at"));
            assertEquals("EXTRACT(HOUR FROM t1.created_at)", result);
        }

        @Test
        @DisplayName("MINUTE(col) → EXTRACT(MINUTE FROM col)")
        void testMinute() {
            String result = dialect.buildFunctionCall("MINUTE", Collections.singletonList("t1.created_at"));
            assertEquals("EXTRACT(MINUTE FROM t1.created_at)", result);
        }

        @Test
        @DisplayName("SECOND(col) → EXTRACT(SECOND FROM col)")
        void testSecond() {
            String result = dialect.buildFunctionCall("SECOND", Collections.singletonList("t1.created_at"));
            assertEquals("EXTRACT(SECOND FROM t1.created_at)", result);
        }

        @Test
        @DisplayName("DATE_FORMAT(col, '%Y-%m') → TO_CHAR(col, 'YYYY-MM')")
        void testDateFormat_YearMonth() {
            String result = dialect.buildFunctionCall("DATE_FORMAT",
                    Arrays.asList("t1.date_order", "'%Y-%m'"));
            assertEquals("TO_CHAR(t1.date_order, 'YYYY-MM')", result);
        }

        @Test
        @DisplayName("DATE_FORMAT(col, '%Y-%m-%d') → TO_CHAR(col, 'YYYY-MM-DD')")
        void testDateFormat_FullDate() {
            String result = dialect.buildFunctionCall("DATE_FORMAT",
                    Arrays.asList("t1.date_order", "'%Y-%m-%d'"));
            assertEquals("TO_CHAR(t1.date_order, 'YYYY-MM-DD')", result);
        }

        @Test
        @DisplayName("DATE_FORMAT(col, '%Y-%m-%d %H:%i:%s') → TO_CHAR(col, 'YYYY-MM-DD HH24:MI:SS')")
        void testDateFormat_DateTime() {
            String result = dialect.buildFunctionCall("DATE_FORMAT",
                    Arrays.asList("t1.created_at", "'%Y-%m-%d %H:%i:%s'"));
            assertEquals("TO_CHAR(t1.created_at, 'YYYY-MM-DD HH24:MI:SS')", result);
        }

        @Test
        @DisplayName("小写函数名也能正确匹配")
        void testCaseInsensitive() {
            String result = dialect.buildFunctionCall("year", Collections.singletonList("t1.date_order"));
            assertEquals("EXTRACT(YEAR FROM t1.date_order)", result);
        }

        @Test
        @DisplayName("非日期函数返回 null（不重写）")
        void testNonDateFunctionReturnsNull() {
            assertNull(dialect.buildFunctionCall("ABS", Collections.singletonList("t1.amount")));
            assertNull(dialect.buildFunctionCall("ROUND", Arrays.asList("t1.amount", "2")));
        }
    }

    // ==========================================
    // SQLite — strftime 适配
    // ==========================================
    @Nested
    @DisplayName("SQLite — strftime 适配")
    class SqliteTest {
        private final FDialect dialect = FDialect.SQLITE_DIALECT;

        @Test
        @DisplayName("YEAR(col) → CAST(strftime('%Y', col) AS INTEGER)")
        void testYear() {
            String result = dialect.buildFunctionCall("YEAR", Collections.singletonList("t1.order_date"));
            assertEquals("CAST(strftime('%Y', t1.order_date) AS INTEGER)", result);
        }

        @Test
        @DisplayName("MONTH(col) → CAST(strftime('%m', col) AS INTEGER)")
        void testMonth() {
            String result = dialect.buildFunctionCall("MONTH", Collections.singletonList("t1.order_date"));
            assertEquals("CAST(strftime('%m', t1.order_date) AS INTEGER)", result);
        }

        @Test
        @DisplayName("DAY(col) → CAST(strftime('%d', col) AS INTEGER)")
        void testDay() {
            String result = dialect.buildFunctionCall("DAY", Collections.singletonList("t1.order_date"));
            assertEquals("CAST(strftime('%d', t1.order_date) AS INTEGER)", result);
        }

        @Test
        @DisplayName("DATE_FORMAT(col, fmt) → strftime(fmt, col) — 参数顺序调换")
        void testDateFormat() {
            String result = dialect.buildFunctionCall("DATE_FORMAT",
                    Arrays.asList("t1.order_date", "'%Y-%m'"));
            assertEquals("strftime('%Y-%m', t1.order_date)", result);
        }
    }

    // ==========================================
    // SQL Server — DATEPART 适配
    // ==========================================
    @Nested
    @DisplayName("SQL Server — DATEPART 适配")
    class SqlServerTest {
        private final FDialect dialect = FDialect.SQLSERVER_DIALECT;

        @Test
        @DisplayName("YEAR() 在 SQL Server 中不重写（原生支持）")
        void testYearReturnsNull() {
            assertNull(dialect.buildFunctionCall("YEAR", Collections.singletonList("t1.date_order")));
        }

        @Test
        @DisplayName("HOUR(col) → DATEPART(HOUR, col)")
        void testHour() {
            String result = dialect.buildFunctionCall("HOUR", Collections.singletonList("t1.created_at"));
            assertEquals("DATEPART(HOUR, t1.created_at)", result);
        }

        @Test
        @DisplayName("MINUTE(col) → DATEPART(MINUTE, col)")
        void testMinute() {
            String result = dialect.buildFunctionCall("MINUTE", Collections.singletonList("t1.created_at"));
            assertEquals("DATEPART(MINUTE, t1.created_at)", result);
        }

        @Test
        @DisplayName("DATE_FORMAT(col, '%Y-%m') → FORMAT(col, 'yyyy-MM')")
        void testDateFormat() {
            String result = dialect.buildFunctionCall("DATE_FORMAT",
                    Arrays.asList("t1.date_order", "'%Y-%m'"));
            assertEquals("FORMAT(t1.date_order, 'yyyy-MM')", result);
        }
    }

    // ==========================================
    // 通用边界条件
    // ==========================================
    @Nested
    @DisplayName("通用边界条件")
    class EdgeCaseTest {

        @Test
        @DisplayName("null 函数名 → 返回 null")
        void testNullFuncName() {
            assertNull(FDialect.POSTGRES_DIALECT.buildFunctionCall(null, Collections.singletonList("col")));
        }

        @Test
        @DisplayName("null 参数列表 → 返回 null")
        void testNullArgsList() {
            assertNull(FDialect.POSTGRES_DIALECT.buildFunctionCall("YEAR", null));
        }

        @Test
        @DisplayName("空参数列表 → 返回 null")
        void testEmptyArgsList() {
            assertNull(FDialect.POSTGRES_DIALECT.buildFunctionCall("YEAR", Collections.emptyList()));
        }

        @Test
        @DisplayName("FDialect 基类默认返回 null")
        void testBaseClassReturnsNull() {
            // MySQL 对 YEAR 不需要重写
            assertNull(FDialect.MYSQL_DIALECT.buildFunctionCall("YEAR", Collections.singletonList("col")));
            assertNull(FDialect.MYSQL_DIALECT.buildFunctionCall("MONTH", Collections.singletonList("col")));
            assertNull(FDialect.MYSQL_DIALECT.buildFunctionCall("DATE_FORMAT",
                    Arrays.asList("col", "'%Y-%m'")));
        }
    }
}
