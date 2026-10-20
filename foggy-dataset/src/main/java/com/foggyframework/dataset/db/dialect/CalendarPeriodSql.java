package com.foggyframework.dataset.db.dialect;

import java.util.Locale;
import java.util.Set;

/** Calendar keys deliberately independent of session week numbering and SQL date formatting. */
public final class CalendarPeriodSql {
    private CalendarPeriodSql() {}

    public static String start(FDialect dialect, String date, String grain) {
        if (!Set.of("day", "week", "month", "quarter", "year").contains(grain)) {
            throw new IllegalArgumentException("TIMEWINDOW_GRAIN_INCOMPATIBLE: " + grain);
        }
        String result = dialect.buildDateTruncateExpression(date, grain);
        if (result == null || result.equals(date)) {
            throw new IllegalArgumentException("TIMEWINDOW_DIALECT_UNSUPPORTED: calendar " + grain);
        }
        return "(" + result + ")";
    }

    public static String key(FDialect dialect, String date, String grain, boolean isoYear) {
        String year = extract(dialect, "YEAR", date);
        String month = extract(dialect, "MONTH", date);
        return switch (grain) {
            case "year" -> year;
            case "month" -> "(" + year + " * 12 + " + month + " - 1)";
            case "quarter" -> "(" + year + " * 4 + FLOOR((" + month + " - 1) / 3.0))";
            case "day" -> dialect.buildDateDiffExpression(start(dialect, date, "day"), "'1970-01-01'");
            case "week" -> isoYear ? isoKey(dialect, date)
                    : dialect.buildDateDiffExpression(start(dialect, date, "week"), "'1970-01-01'");
            default -> throw new IllegalArgumentException("TIMEWINDOW_GRAIN_INCOMPATIBLE: " + grain);
        };
    }

    private static String extract(FDialect dialect, String part, String date) {
        String result = dialect.buildFunctionCall(part, java.util.List.of(date));
        return result == null ? part + "(" + date + ")" : result;
    }

    private static String isoKey(FDialect dialect, String date) {
        String product = dialect.getProductName().toUpperCase(Locale.ROOT);
        String thursday = dialect.buildDateAddExpression(start(dialect, date, "week"), "3", "day");
        return switch (product) {
            case "MYSQL", "MARIADB" -> "YEARWEEK(" + date + ", 3)";
            case "POSTGRESQL", "POSTGRES" -> "(EXTRACT(ISOYEAR FROM " + date + ") * 100 + EXTRACT(WEEK FROM " + date + "))";
            case "SQLSERVER", "SQL_SERVER", "MICROSOFT SQL SERVER" -> "(YEAR(" + thursday + ") * 100 + DATEPART(ISO_WEEK, " + date + "))";
            case "SQLITE" -> "(CAST(strftime('%Y', " + thursday + ") AS INTEGER) * 100 + "
                    + "((CAST(strftime('%j', " + thursday + ") AS INTEGER) - 1) / 7) + 1)";
            default -> throw new IllegalArgumentException("TIMEWINDOW_DIALECT_UNSUPPORTED: ISO week-year");
        };
    }
}
