package com.foggyframework.dataset.model.engine.expression.sql;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.dialect.MysqlDialect;
import com.foggyframework.dataset.db.dialect.PostgresDialect;
import com.foggyframework.dataset.db.dialect.SqlServerDialect;
import com.foggyframework.dataset.db.dialect.SqliteDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DialectAwareFunctionExp} + {@link FDialect} date 函数分派的方言特定 SQL 字符串断言 · v1.4 Step 3.4
 * <p>
 * 覆盖 Formula Spec v1 方言感知函数 {@code date_diff} / {@code date_add(day|month|year)} / {@code now}
 * 在四方言（MySQL / PostgreSQL / SQL Server / SQLite）下的 SQL 输出。
 * </p>
 * <p>
 * 此测试只验证**方言 SQL 字符串拼装形态**，不跑真实数据库。真数据执行在 Step 3.3 多方言 lane
 * 里通过 Docker profile 完成。
 * </p>
 *
 * @since v1.4
 */
@DisplayName("DialectAwareFunctionExp · 四方言 SQL 拼装")
class DialectAwareFunctionExpTest {

    // ==========================================
    // date_diff(a, b)
    // ==========================================

    @Test
    @DisplayName("MySQL: date_diff → DATEDIFF(a, b)")
    void mysqlDateDiff() {
        assertEquals("DATEDIFF(t.end_date, t.start_date)",
                new MysqlDialect().buildDateDiffExpression("t.end_date", "t.start_date"));
    }

    @Test
    @DisplayName("PostgreSQL: date_diff → (a::date - b::date)")
    void postgresDateDiff() {
        assertEquals("(t.end_date::date - t.start_date::date)",
                new PostgresDialect().buildDateDiffExpression("t.end_date", "t.start_date"));
    }

    @Test
    @DisplayName("SQL Server: date_diff → DATEDIFF(day, b, a) · 参数顺序反")
    void sqlServerDateDiff() {
        // MSSQL 是 DATEDIFF(unit, start, end) — 参数顺序与 MySQL 相反
        assertEquals("DATEDIFF(day, t.start_date, t.end_date)",
                new SqlServerDialect().buildDateDiffExpression("t.end_date", "t.start_date"));
    }

    @Test
    @DisplayName("SQLite: date_diff → CAST((julianday(a) - julianday(b)) AS INTEGER)")
    void sqliteDateDiff() {
        assertEquals("CAST((julianday(t.end_date) - julianday(t.start_date)) AS INTEGER)",
                new SqliteDialect().buildDateDiffExpression("t.end_date", "t.start_date"));
    }

    // ==========================================
    // date_add(d, ?, 'day' / 'month' / 'year')
    // ==========================================

    @Test
    @DisplayName("MySQL: date_add day/month/year → DATE_ADD(d, INTERVAL ? DAY|MONTH|YEAR)")
    void mysqlDateAdd() {
        MysqlDialect mysql = new MysqlDialect();
        assertEquals("DATE_ADD(t.d, INTERVAL ? DAY)",
                mysql.buildDateAddExpression("t.d", "?", "day"));
        assertEquals("DATE_ADD(t.d, INTERVAL ? MONTH)",
                mysql.buildDateAddExpression("t.d", "?", "month"));
        assertEquals("DATE_ADD(t.d, INTERVAL ? YEAR)",
                mysql.buildDateAddExpression("t.d", "?", "year"));
    }

    @Test
    @DisplayName("PostgreSQL: date_add → (d + make_interval(days|months|years => ?))")
    void postgresDateAdd() {
        PostgresDialect pg = new PostgresDialect();
        assertEquals("(t.d + make_interval(days => ?))",
                pg.buildDateAddExpression("t.d", "?", "day"));
        assertEquals("(t.d + make_interval(months => ?))",
                pg.buildDateAddExpression("t.d", "?", "month"));
        assertEquals("(t.d + make_interval(years => ?))",
                pg.buildDateAddExpression("t.d", "?", "year"));
    }

    @Test
    @DisplayName("SQL Server: date_add → DATEADD(day|month|year, ?, d)")
    void sqlServerDateAdd() {
        SqlServerDialect mssql = new SqlServerDialect();
        assertEquals("DATEADD(day, ?, t.d)",
                mssql.buildDateAddExpression("t.d", "?", "day"));
        assertEquals("DATEADD(month, ?, t.d)",
                mssql.buildDateAddExpression("t.d", "?", "month"));
        assertEquals("DATEADD(year, ?, t.d)",
                mssql.buildDateAddExpression("t.d", "?", "year"));
    }

    @Test
    @DisplayName("SQLite: date_add → date(d, '+' || ? || ' day|month|year')")
    void sqliteDateAdd() {
        SqliteDialect sqlite = new SqliteDialect();
        assertEquals("date(t.d, '+' || ? || ' day')",
                sqlite.buildDateAddExpression("t.d", "?", "day"));
        assertEquals("date(t.d, '+' || ? || ' month')",
                sqlite.buildDateAddExpression("t.d", "?", "month"));
        assertEquals("date(t.d, '+' || ? || ' year')",
                sqlite.buildDateAddExpression("t.d", "?", "year"));
    }

    // ==========================================
    // unit 校验（非 day/month/year 拒绝）
    // ==========================================

    @Test
    @DisplayName("unit 非法值 - 四方言一致抛 IllegalArgumentException")
    void illegalUnitRejected() {
        FDialect[] dialects = {
                new MysqlDialect(), new PostgresDialect(),
                new SqlServerDialect(), new SqliteDialect()
        };
        String[] illegalUnits = {"week", "hour", "minute", "WEEK", "day ", null};
        for (FDialect d : dialects) {
            for (String u : illegalUnits) {
                assertThrows(IllegalArgumentException.class,
                        () -> d.buildDateAddExpression("t.d", "?", u),
                        d.getClass().getSimpleName() + " should reject unit=" + u);
            }
        }
    }

    // ==========================================
    // now() - buildCurrentTimestampExpression
    // ==========================================

    @Test
    @DisplayName("now() 四方言输出")
    void fourDialectsNow() {
        assertEquals("NOW()", new MysqlDialect().buildCurrentTimestampExpression());
        assertEquals("NOW()", new PostgresDialect().buildCurrentTimestampExpression());
        assertEquals("GETDATE()", new SqlServerDialect().buildCurrentTimestampExpression());
        assertEquals("datetime('now')", new SqliteDialect().buildCurrentTimestampExpression());
    }

    // ==========================================
    // DialectAwareFunctionExp 的参数数量校验
    // ==========================================

    @Test
    @DisplayName("renderDateDiff: 参数数量错误拒绝")
    void renderDateDiffRejectsBadArity() {
        assertThrows(IllegalArgumentException.class,
                () -> DialectAwareFunctionExp.renderDateDiff(null, java.util.Collections.emptyList()));
        assertThrows(IllegalArgumentException.class,
                () -> DialectAwareFunctionExp.renderDateDiff(null,
                        java.util.Collections.singletonList(com.foggyframework.dataset.model.engine.expression.SqlFragment.ofLiteral("a"))));
    }

    @Test
    @DisplayName("renderDateAdd: 参数数量错误拒绝")
    void renderDateAddRejectsBadArity() {
        assertThrows(IllegalArgumentException.class,
                () -> DialectAwareFunctionExp.renderDateAdd(null, java.util.Collections.emptyList()));
    }

    @Test
    @DisplayName("renderNow: 非空参数拒绝")
    void renderNowRejectsNonEmptyArgs() {
        assertThrows(IllegalArgumentException.class,
                () -> DialectAwareFunctionExp.renderNow(null,
                        java.util.Collections.singletonList(com.foggyframework.dataset.model.engine.expression.SqlFragment.ofLiteral("x"))));
    }

    @Test
    @DisplayName("renderDateAdd: unit 非字符串字面量拒绝")
    void renderDateAddRejectsNonLiteralUnit() {
        // unit 参数必须是带引号的字面量（'day'），否则抛错
        assertThrows(IllegalArgumentException.class,
                () -> DialectAwareFunctionExp.renderDateAdd(null, java.util.Arrays.asList(
                        com.foggyframework.dataset.model.engine.expression.SqlFragment.ofLiteral("t.d"),
                        com.foggyframework.dataset.model.engine.expression.SqlFragment.ofLiteral("?"),
                        com.foggyframework.dataset.model.engine.expression.SqlFragment.ofLiteral("t.unit_col")
                )));
    }
}
