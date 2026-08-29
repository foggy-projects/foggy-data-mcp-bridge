package com.foggyframework.dataset.model.engine.total;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.spi.DbColumnType;

/** Dialect boundary for null-safe, non-integer totalData ratios. */
public final class TotalDataSqlDialect {
    private TotalDataSqlDialect() {
    }

    public static String safeRatio(FDialect dialect, String numerator, String denominator) {
        return renderRatio(dialect, numerator, denominator, DbColumnType.NUMBER);
    }

    public static String renderRatio(FDialect dialect,
                                     String numerator,
                                     String denominator,
                                     DbColumnType resultType) {
        if (dialect == null) {
            dialect = FDialect.MYSQL_DIALECT;
        }
        if (!isNumeric(resultType)) {
            throw new IllegalArgumentException(
                    "totalData ratio requires a numeric result type: " + resultType);
        }
        return switch (dialect.getDbType()) {
            case SQLITE -> "(CAST((" + numerator + ") AS REAL) / NULLIF(("
                    + denominator + "), 0))";
            case POSTGRESQL -> "(CAST((" + numerator + ") AS NUMERIC) / NULLIF(("
                    + denominator + "), 0))";
            case SQLSERVER -> "(CAST((" + numerator + ") AS DECIMAL(38,10)) / NULLIF(CAST(("
                    + denominator + ") AS DECIMAL(38,10)), 0))";
            case MYSQL -> "(CAST((" + numerator + ") AS DECIMAL(65,30)) / NULLIF(("
                    + denominator + "), 0))";
            default -> "(1.0 * (" + numerator + ") / NULLIF((" + denominator + "), 0))";
        };
    }

    private static boolean isNumeric(DbColumnType type) {
        return type == DbColumnType.NUMBER
                || type == DbColumnType.MONEY
                || type == DbColumnType.INTEGER
                || type == DbColumnType.BIGINT;
    }
}
