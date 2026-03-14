package com.foggyframework.dataset.db.dialect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 方言日期截断、当前时间戳、列类型映射 单元测试
 * <p>
 * 覆盖 4 种方言 × 7 种粒度 = 28 个日期截断断言，
 * 加上 currentTimestamp 和 mapColumnType 测试。
 * </p>
 * <p>
 * 纯单元测试，不需要 Spring 上下文或数据库连接。
 * </p>
 */
@DisplayName("Dialect Date Truncate / Timestamp / ColumnType Tests")
class DateTruncateTest {

    private static final String COL = "order_date";

    // ==================== MySQL ====================

    @Nested
    @DisplayName("MySQL Dialect")
    class MysqlTests {

        private final FDialect dialect = FDialect.MYSQL_DIALECT;

        @Test
        @DisplayName("DAY → DATE(column)")
        void day() {
            String expr = dialect.buildDateTruncateExpression(COL, "DAY");
            assertEquals("DATE(" + COL + ")", expr);
        }

        @Test
        @DisplayName("MONTH → DATE_FORMAT(column, '%Y-%m-01')")
        void month() {
            String expr = dialect.buildDateTruncateExpression(COL, "MONTH");
            assertTrue(expr.contains("DATE_FORMAT"), "Should use DATE_FORMAT");
            assertTrue(expr.contains("%Y-%m-01"), "Should truncate to month start");
        }

        @Test
        @DisplayName("YEAR → DATE_FORMAT(column, '%Y-01-01')")
        void year() {
            String expr = dialect.buildDateTruncateExpression(COL, "YEAR");
            assertTrue(expr.contains("DATE_FORMAT"), "Should use DATE_FORMAT");
            assertTrue(expr.contains("%Y-01-01"), "Should truncate to year start");
        }

        @Test
        @DisplayName("QUARTER → MAKEDATE+QUARTER")
        void quarter() {
            String expr = dialect.buildDateTruncateExpression(COL, "QUARTER");
            assertTrue(expr.contains("QUARTER"), "Should reference QUARTER function");
            assertTrue(expr.contains("MAKEDATE"), "Should use MAKEDATE");
        }

        @Test
        @DisplayName("WEEK → DATE_SUB + WEEKDAY")
        void week() {
            String expr = dialect.buildDateTruncateExpression(COL, "WEEK");
            assertTrue(expr.contains("DATE_SUB") || expr.contains("WEEKDAY"),
                    "Should use DATE_SUB/WEEKDAY");
        }

        @Test
        @DisplayName("HOUR → DATE_FORMAT(column, '%Y-%m-%d %H:00:00')")
        void hour() {
            String expr = dialect.buildDateTruncateExpression(COL, "HOUR");
            assertTrue(expr.contains("DATE_FORMAT"), "Should use DATE_FORMAT");
            assertTrue(expr.contains("%H:00:00"), "Should truncate to hour");
        }

        @Test
        @DisplayName("MINUTE → returns column unchanged (MySQL no sub-minute)")
        void minute() {
            String expr = dialect.buildDateTruncateExpression(COL, "MINUTE");
            assertEquals(COL, expr, "MINUTE should return column as-is for MySQL");
        }

        @Test
        @DisplayName("currentTimestamp → NOW()")
        void currentTimestamp() {
            assertEquals("NOW()", dialect.buildCurrentTimestampExpression());
        }

        @Test
        @DisplayName("mapColumnType → passthrough (MySQL is default)")
        void mapColumnType() {
            assertEquals("DATE", dialect.mapColumnType("DATE"));
            assertEquals("BIGINT", dialect.mapColumnType("BIGINT"));
            assertEquals("DATETIME", dialect.mapColumnType("DATETIME"));
        }

        @Test
        @DisplayName("null granularity → returns column unchanged")
        void nullGranularity() {
            assertEquals(COL, dialect.buildDateTruncateExpression(COL, null));
        }
    }

    // ==================== PostgreSQL ====================

    @Nested
    @DisplayName("PostgreSQL Dialect")
    class PostgresTests {

        private final FDialect dialect = FDialect.POSTGRES_DIALECT;

        @ParameterizedTest
        @ValueSource(strings = {"DAY", "MONTH", "YEAR", "QUARTER", "WEEK", "HOUR", "MINUTE"})
        @DisplayName("All granularities → DATE_TRUNC('granularity', column)")
        void allGranularities(String granularity) {
            String expr = dialect.buildDateTruncateExpression(COL, granularity);
            assertTrue(expr.contains("DATE_TRUNC"),
                    granularity + " should use DATE_TRUNC");
            assertTrue(expr.contains(COL),
                    granularity + " should reference original column");
        }

        @Test
        @DisplayName("DAY → DATE_TRUNC('day', column)")
        void dayExact() {
            assertEquals("DATE_TRUNC('day', " + COL + ")",
                    dialect.buildDateTruncateExpression(COL, "DAY"));
        }

        @Test
        @DisplayName("currentTimestamp → NOW()")
        void currentTimestamp() {
            assertEquals("NOW()", dialect.buildCurrentTimestampExpression());
        }

        @Test
        @DisplayName("mapColumnType: DATETIME→TIMESTAMP, INT→INTEGER")
        void mapColumnType() {
            assertEquals("TIMESTAMP", dialect.mapColumnType("DATETIME"));
            assertEquals("INTEGER", dialect.mapColumnType("INT"));
            assertEquals("BIGINT", dialect.mapColumnType("BIGINT"));
        }
    }

    // ==================== SQL Server ====================

    @Nested
    @DisplayName("SQL Server Dialect")
    class SqlServerTests {

        private final FDialect dialect = FDialect.SQLSERVER_DIALECT;

        @Test
        @DisplayName("DAY → CAST(column AS DATE)")
        void day() {
            String expr = dialect.buildDateTruncateExpression(COL, "DAY");
            assertTrue(expr.contains("CAST") && expr.contains("DATE"),
                    "DAY should use CAST AS DATE");
        }

        @Test
        @DisplayName("MONTH → DATEFROMPARTS(YEAR, MONTH, 1)")
        void month() {
            String expr = dialect.buildDateTruncateExpression(COL, "MONTH");
            assertTrue(expr.contains("DATEFROMPARTS"), "Should use DATEFROMPARTS");
            assertTrue(expr.contains("MONTH"), "Should reference MONTH function");
        }

        @Test
        @DisplayName("YEAR → DATEFROMPARTS(YEAR, 1, 1)")
        void year() {
            String expr = dialect.buildDateTruncateExpression(COL, "YEAR");
            assertTrue(expr.contains("DATEFROMPARTS"), "Should use DATEFROMPARTS");
            assertTrue(expr.contains("YEAR"), "Should reference YEAR function");
        }

        @Test
        @DisplayName("QUARTER → DATEFROMPARTS + DATEPART(QUARTER)")
        void quarter() {
            String expr = dialect.buildDateTruncateExpression(COL, "QUARTER");
            assertTrue(expr.contains("DATEFROMPARTS"), "Should use DATEFROMPARTS");
            assertTrue(expr.contains("DATEPART") && expr.contains("QUARTER"),
                    "Should use DATEPART(QUARTER)");
        }

        @Test
        @DisplayName("WEEK → DATEADD + WEEKDAY")
        void week() {
            String expr = dialect.buildDateTruncateExpression(COL, "WEEK");
            assertTrue(expr.contains("DATEADD"), "Should use DATEADD");
            assertTrue(expr.contains("WEEKDAY"), "Should reference WEEKDAY");
        }

        @Test
        @DisplayName("HOUR → DATEADD(HOUR, DATEDIFF(HOUR, 0, col), 0)")
        void hour() {
            String expr = dialect.buildDateTruncateExpression(COL, "HOUR");
            assertTrue(expr.contains("DATEADD") && expr.contains("DATEDIFF"),
                    "Should use DATEADD + DATEDIFF for hour truncation");
        }

        @Test
        @DisplayName("MINUTE → DATEADD(MINUTE, DATEDIFF(MINUTE, 0, col), 0)")
        void minute() {
            String expr = dialect.buildDateTruncateExpression(COL, "MINUTE");
            assertTrue(expr.contains("DATEADD") && expr.contains("MINUTE"),
                    "Should use DATEADD MINUTE for minute truncation");
        }

        @Test
        @DisplayName("currentTimestamp → GETDATE()")
        void currentTimestamp() {
            assertEquals("GETDATE()", dialect.buildCurrentTimestampExpression());
        }

        @Test
        @DisplayName("mapColumnType: DATETIME→DATETIME2")
        void mapColumnType() {
            assertEquals("DATETIME2", dialect.mapColumnType("DATETIME"));
            assertEquals("BIGINT", dialect.mapColumnType("BIGINT"));
        }
    }

    // ==================== SQLite ====================

    @Nested
    @DisplayName("SQLite Dialect")
    class SqliteTests {

        private final FDialect dialect = FDialect.SQLITE_DIALECT;

        @Test
        @DisplayName("DAY → DATE(column)")
        void day() {
            String expr = dialect.buildDateTruncateExpression(COL, "DAY");
            assertEquals("DATE(" + COL + ")", expr);
        }

        @Test
        @DisplayName("MONTH → strftime('%Y-%m-01', column)")
        void month() {
            String expr = dialect.buildDateTruncateExpression(COL, "MONTH");
            assertTrue(expr.contains("strftime"), "Should use strftime");
            assertTrue(expr.contains("%Y-%m-01"), "Should truncate to month start");
        }

        @Test
        @DisplayName("YEAR → strftime('%Y-01-01', column)")
        void year() {
            String expr = dialect.buildDateTruncateExpression(COL, "YEAR");
            assertTrue(expr.contains("strftime"), "Should use strftime");
            assertTrue(expr.contains("%Y-01-01"), "Should truncate to year start");
        }

        @Test
        @DisplayName("QUARTER → strftime + CASE WHEN")
        void quarter() {
            String expr = dialect.buildDateTruncateExpression(COL, "QUARTER");
            assertTrue(expr.contains("strftime"), "Should use strftime");
            assertTrue(expr.contains("CASE"), "Should use CASE for quarter logic");
        }

        @Test
        @DisplayName("WEEK → DATE(column, 'weekday 0', '-6 days')")
        void week() {
            String expr = dialect.buildDateTruncateExpression(COL, "WEEK");
            assertTrue(expr.contains("DATE"), "Should use DATE function");
            assertTrue(expr.contains("weekday"), "Should reference weekday modifier");
        }

        @Test
        @DisplayName("HOUR → strftime('%Y-%m-%d %H:00:00', column)")
        void hour() {
            String expr = dialect.buildDateTruncateExpression(COL, "HOUR");
            assertTrue(expr.contains("strftime"), "Should use strftime");
            assertTrue(expr.contains("%H:00:00"), "Should truncate to hour");
        }

        @Test
        @DisplayName("MINUTE → strftime('%Y-%m-%d %H:%M:00', column)")
        void minute() {
            String expr = dialect.buildDateTruncateExpression(COL, "MINUTE");
            assertTrue(expr.contains("strftime"), "Should use strftime");
            assertTrue(expr.contains("%H:%M:00"), "Should truncate to minute");
        }

        @Test
        @DisplayName("currentTimestamp → datetime('now')")
        void currentTimestamp() {
            assertEquals("datetime('now')", dialect.buildCurrentTimestampExpression());
        }

        @Test
        @DisplayName("mapColumnType: VARCHAR/DATETIME/DATE→TEXT, INT/BIGINT→INTEGER, DECIMAL→REAL")
        void mapColumnType() {
            assertEquals("TEXT", dialect.mapColumnType("VARCHAR(255)"));
            assertEquals("TEXT", dialect.mapColumnType("DATETIME"));
            assertEquals("TEXT", dialect.mapColumnType("DATE"));
            assertEquals("TEXT", dialect.mapColumnType("TIMESTAMP"));
            assertEquals("INTEGER", dialect.mapColumnType("INT"));
            assertEquals("INTEGER", dialect.mapColumnType("BIGINT"));
            assertEquals("REAL", dialect.mapColumnType("DECIMAL(20,4)"));
        }
    }

    // ==================== Cross-Dialect Consistency ====================

    @Nested
    @DisplayName("Cross-Dialect Consistency")
    class CrossDialectTests {

        private final FDialect[] ALL_DIALECTS = {
                FDialect.MYSQL_DIALECT,
                FDialect.POSTGRES_DIALECT,
                FDialect.SQLSERVER_DIALECT,
                FDialect.SQLITE_DIALECT
        };

        @Test
        @DisplayName("All dialects return non-null for DAY granularity")
        void allDialectsHandleDay() {
            for (FDialect dialect : ALL_DIALECTS) {
                String expr = dialect.buildDateTruncateExpression(COL, "DAY");
                assertNotNull(expr, dialect.getProductName() + " returned null for DAY");
                assertFalse(expr.isEmpty(), dialect.getProductName() + " returned empty for DAY");
            }
        }

        @Test
        @DisplayName("All dialects return non-null for currentTimestamp")
        void allDialectsHaveTimestamp() {
            for (FDialect dialect : ALL_DIALECTS) {
                String expr = dialect.buildCurrentTimestampExpression();
                assertNotNull(expr, dialect.getProductName() + " returned null for currentTimestamp");
                assertFalse(expr.isEmpty(), dialect.getProductName() + " returned empty for currentTimestamp");
            }
        }

        @Test
        @DisplayName("All dialects handle null granularity gracefully")
        void allDialectsHandleNullGranularity() {
            for (FDialect dialect : ALL_DIALECTS) {
                String expr = dialect.buildDateTruncateExpression(COL, null);
                assertEquals(COL, expr,
                        dialect.getProductName() + " should return column unchanged for null granularity");
            }
        }

        @Test
        @DisplayName("All dialects handle mapColumnType(null) gracefully")
        void allDialectsHandleNullType() {
            for (FDialect dialect : ALL_DIALECTS) {
                // mapColumnType(null) should return null, not throw
                assertDoesNotThrow(() -> dialect.mapColumnType(null),
                        dialect.getProductName() + " should not throw on null type");
            }
        }
    }
}
