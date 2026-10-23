package com.foggyframework.dataset.db.dialect;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared scalar and total-stage lowering. DATE_DIFF deliberately keeps its legacy contract. */
public final class DateDifferenceSql {
    private DateDifferenceSql() {}

    public static final class InvalidCall extends IllegalArgumentException {
        private final String code;
        public InvalidCall(String code, String message) { super(message); this.code = code; }
        public String getCode() { return code; }
    }

    public static List<String> supportedUnits(FDialect dialect) {
        List<String> units = List.of("MICROSECOND", "SECOND", "MINUTE", "HOUR", "DAY", "WEEK", "MONTH", "QUARTER", "YEAR");
        return dialect != null && "SQLITE".equalsIgnoreCase(dialect.getProductName()) ? units.subList(1, units.size()) : units;
    }

    public static boolean supports(String function) {
        return "DATEDIFF".equalsIgnoreCase(function) || "TIMESTAMPDIFF".equalsIgnoreCase(function);
    }

    public static String render(FDialect dialect, String function, List<String> args) {
        boolean calendar = "DATEDIFF".equalsIgnoreCase(function);
        int count = calendar ? 2 : 3;
        if (args.size() != count) throw new InvalidCall("DATE_FUNCTION_ARITY_INVALID",
                function + " requires " + count + " arguments");
        String product = dialect == null ? "" : dialect.getProductName().toUpperCase(Locale.ROOT);
        String family = switch (product) {
            case "MYSQL", "MARIADB" -> "mysql";
            case "POSTGRESQL", "POSTGRES" -> "postgres";
            case "SQLSERVER", "SQL_SERVER", "MICROSOFT SQL SERVER" -> "sqlserver";
            case "SQLITE" -> "sqlite";
            default -> throw new InvalidCall("DATE_FUNCTION_DIALECT_UNSUPPORTED", "Unsupported date function dialect: " + product);
        };
        if (calendar) {
            String end = args.get(0), start = args.get(1);
            return switch (family) {
                case "mysql" -> "DATEDIFF(" + end + ", " + start + ")";
                case "postgres" -> "(CAST(" + end + " AS date) - CAST(" + start + " AS date))";
                case "sqlserver" -> "DATEDIFF(day, " + start + ", " + end + ")";
                default -> "CAST((julianday(date(" + end + ")) - julianday(date(" + start + "))) AS INTEGER)";
            };
        }
        String unit = args.get(0).toUpperCase(Locale.ROOT);
        if (unit.startsWith("'") && unit.endsWith("'")) unit = unit.substring(1, unit.length() - 1);
        if (!Set.of("MICROSECOND", "SECOND", "MINUTE", "HOUR", "DAY", "WEEK", "MONTH", "QUARTER", "YEAR").contains(unit))
            throw new InvalidCall("DATE_FUNCTION_UNIT_INVALID", "TIMESTAMPDIFF requires a literal unit: MICROSECOND, SECOND, MINUTE, HOUR, DAY, WEEK, MONTH, QUARTER or YEAR");
        String start = args.get(1), end = args.get(2);
        if (family.equals("mysql")) return "TIMESTAMPDIFF(" + unit + ", " + start + ", " + end + ")";
        if (Set.of("MONTH", "QUARTER", "YEAR").contains(unit)) {
            String months = months(family, start, end);
            return truncate(family, "(" + months + ") / " + (unit.equals("YEAR") ? "12.0" : unit.equals("QUARTER") ? "3.0" : "1.0"));
        }
        if (family.equals("sqlite") && unit.equals("MICROSECOND")) throw new InvalidCall(
                "DATE_FUNCTION_UNIT_UNSUPPORTED", "SQLite date functions provide millisecond precision; MICROSECOND is unsupported");
        long micros = switch (unit) {
            case "MICROSECOND" -> 1L;
            case "SECOND" -> 1000000L;
            case "MINUTE" -> 60000000L;
            case "HOUR" -> 3600000000L;
            case "DAY" -> 86400000000L;
            default -> 604800000000L;
        };
        String elapsed = switch (family) {
            // Extract from each typed operand: casting timestamptz to timestamp loses DST offsets.
            case "postgres" -> "((" + postgresEpoch(end) + " - " + postgresEpoch(start) + ") * 1000000.0)";
            // DATEDIFF counts boundaries. Restore the sub-microsecond remainder before truncation;
            // using nanoseconds for the entire interval would overflow for long date ranges.
            case "sqlserver" -> "(CAST(DATEDIFF_BIG(microsecond, " + start + ", " + end + ") AS decimal(20,1)) + "
                    + "(DATEPART(nanosecond, CAST(" + end + " AS datetime2(7))) % 1000 - "
                    + "DATEPART(nanosecond, CAST(" + start + " AS datetime2(7))) % 1000) / 1000.0)";
            // Round to SQLite's millisecond resolution BEFORE truncating whole units.
            default -> "(ROUND((julianday(" + end + ") - julianday(" + start + ")) * 86400000.0) * 1000.0)";
        };
        return truncate(family, "(" + elapsed + ") / " + micros + ".0");
    }

    private static String postgresEpoch(String expression) {
        // Keep the historical timestamp conversion for text-valued date formulas and literals,
        // while preserving the instant when the operand already carries a timezone.
        return "(CASE WHEN pg_typeof(" + expression + ") = 'timestamp with time zone'::regtype "
                + "THEN EXTRACT(EPOCH FROM CAST(" + expression + " AS timestamptz)) "
                + "ELSE EXTRACT(EPOCH FROM CAST(" + expression + " AS timestamp)) END)";
    }

    private static String truncate(String family, String value) {
        return family.equals("postgres") ? "CAST(TRUNC(" + value + ") AS BIGINT)"
                : "CAST((" + value + ") AS " + (family.equals("sqlite") ? "INTEGER" : "BIGINT") + ")";
    }

    /** Whole calendar months, excluding a final incomplete day/time portion; symmetric for negatives. */
    private static String months(String family, String start, String end) {
        String sy, ey, sm, em, st, et;
        if (family.equals("sqlite")) {
            sy = "strftime('%Y', " + start + ")"; ey = "strftime('%Y', " + end + ")";
            sm = "strftime('%m', " + start + ")"; em = "strftime('%m', " + end + ")";
            st = "strftime('%d %H:%M:%f', " + start + ")"; et = "strftime('%d %H:%M:%f', " + end + ")";
        } else if (family.equals("postgres")) {
            start = "CAST(" + start + " AS timestamp)"; end = "CAST(" + end + " AS timestamp)";
            sy = "EXTRACT(YEAR FROM " + start + ")"; ey = "EXTRACT(YEAR FROM " + end + ")";
            sm = "EXTRACT(MONTH FROM " + start + ")"; em = "EXTRACT(MONTH FROM " + end + ")";
            st = "TO_CHAR(" + start + ", 'DD HH24:MI:SS.US')"; et = "TO_CHAR(" + end + ", 'DD HH24:MI:SS.US')";
        } else {
            start = "CAST(" + start + " AS datetime2(7))";
            end = "CAST(" + end + " AS datetime2(7))";
            sy = "YEAR(" + start + ")"; ey = "YEAR(" + end + ")";
            sm = "MONTH(" + start + ")"; em = "MONTH(" + end + ")";
            st = "SUBSTRING(CONVERT(varchar(30), " + start + ", 121), 9, 22)";
            et = "SUBSTRING(CONVERT(varchar(30), " + end + ", 121), 9, 22)";
        }
        String delta = "((" + ey + " - " + sy + ") * 12 + " + em + " - " + sm + ")";
        return delta + " + CASE WHEN " + delta + " > 0 AND " + et + " < " + st + " THEN -1 WHEN "
                + delta + " < 0 AND " + et + " > " + st + " THEN 1 ELSE 0 END";
    }
}
