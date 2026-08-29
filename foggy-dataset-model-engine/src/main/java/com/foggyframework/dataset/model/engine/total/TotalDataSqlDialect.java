package com.foggyframework.dataset.model.engine.total;

import com.foggyframework.dataset.db.dialect.FDialect;

/** Dialect boundary for null-safe, non-integer totalData ratios. */
public final class TotalDataSqlDialect {
    private TotalDataSqlDialect() {
    }

    public static String safeRatio(FDialect dialect, String numerator, String denominator) {
        // All currently supported JDBC dialects accept a decimal literal and
        // NULLIF. Keeping this behind a dialect boundary makes per-dialect
        // casting available without leaking arithmetic policy into the plan.
        return "(1.0 * (" + numerator + ") / NULLIF((" + denominator + "), 0))";
    }
}
