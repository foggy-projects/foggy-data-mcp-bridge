package com.foggyframework.dataset.model.engine.total;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.spi.DbColumnType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("totalData safe ratio 方言契约")
class TotalDataSqlDialectTest {

    @Test
    void sqliteUsesRealWideningAndNullif() {
        String sql = TotalDataSqlDialect.renderRatio(
                FDialect.SQLITE_DIALECT, "sum_value", "count_value", DbColumnType.NUMBER);
        assertTrue(sql.contains("CAST((sum_value) AS REAL)"), sql);
        assertTrue(sql.contains("NULLIF((count_value), 0)"), sql);
    }

    @Test
    void mysqlUsesDecimalWideningAndNullif() {
        String sql = TotalDataSqlDialect.renderRatio(
                FDialect.MYSQL8_DIALECT, "sum_value", "count_value", DbColumnType.MONEY);
        assertTrue(sql.contains("DECIMAL(65,30)"), sql);
        assertTrue(sql.contains("NULLIF((count_value), 0)"), sql);
    }

    @Test
    void postgresUsesNumericWideningAndNullif() {
        String sql = TotalDataSqlDialect.renderRatio(
                FDialect.POSTGRES_DIALECT, "sum_value", "count_value", DbColumnType.NUMBER);
        assertTrue(sql.contains("CAST((sum_value) AS NUMERIC)"), sql);
        assertTrue(sql.contains("NULLIF((count_value), 0)"), sql);
    }

    @Test
    void sqlServerUsesDecimalWideningOnBothOperands() {
        String sql = TotalDataSqlDialect.renderRatio(
                FDialect.SQLSERVER_DIALECT, "sum_value", "count_value", DbColumnType.NUMBER);
        assertTrue(sql.contains("DECIMAL(38,10)"), sql);
        assertTrue(sql.contains("NULLIF(CAST((count_value) AS DECIMAL(38,10)), 0)"), sql);
    }

    @Test
    void nonNumericResultTypeFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> TotalDataSqlDialect.renderRatio(
                FDialect.SQLITE_DIALECT, "sum_value", "count_value", DbColumnType.TEXT));
    }
}
