package com.foggyframework.dataset.db.model.engine.compose.plan;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses relative and absolute date expressions used in {@link TimeWindowDef#value()}.
 * <p>
 * Supported formats:
 * <ul>
 *   <li>{@code now} — current date</li>
 *   <li>{@code -1Y}, {@code -7D}, {@code -1M}, {@code -1W}, {@code -1Q} — relative offsets</li>
 *   <li>{@code 2024-01-01}, {@code 20240101} — absolute dates</li>
 * </ul>
 * <p>
 * This parser outputs a dialect-neutral intermediate representation ({@link DateExpr}).
 * SQL lowering is done by {@code FDialect.buildDateAddExpression()}.
 *
 * @since 8.3.0.beta
 */
public final class RelativeDateParser {

    private RelativeDateParser() {}

    // ---- Pattern for relative expressions like "-1Y", "-7D", "+1M" ----
    private static final Pattern RELATIVE_PATTERN = Pattern.compile("^([+-]?)(\\d+)([YDMWQ])$", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;       // 2024-01-01
    private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd"); // 20240101

    /**
     * Check if a string is a valid date expression (either relative or absolute).
     */
    public static boolean isValid(String expr) {
        if (expr == null || expr.isBlank()) return false;
        if ("now".equalsIgnoreCase(expr.trim())) return true;
        if (RELATIVE_PATTERN.matcher(expr.trim()).matches()) return true;
        return isAbsoluteDate(expr.trim());
    }

    /**
     * Parse a date expression into a dialect-neutral {@link DateExpr}.
     *
     * @param expr the expression string
     * @return parsed result
     * @throws IllegalArgumentException if the expression cannot be parsed
     */
    public static DateExpr parse(String expr) {
        if (expr == null || expr.isBlank()) {
            throw new IllegalArgumentException("Empty date expression");
        }
        String trimmed = expr.trim();

        // "now"
        if ("now".equalsIgnoreCase(trimmed)) {
            return new DateExpr(DateExpr.Type.NOW, null, null, 0);
        }

        // Relative: -1Y, -7D, +1M, etc.
        Matcher m = RELATIVE_PATTERN.matcher(trimmed);
        if (m.matches()) {
            String sign = m.group(1);
            int amount = Integer.parseInt(m.group(2));
            String unitChar = m.group(3).toUpperCase();
            if ("-".equals(sign)) {
                amount = -amount;
            }
            OffsetUnit unit;
            if ("Y".equals(unitChar)) unit = OffsetUnit.YEAR;
            else if ("M".equals(unitChar)) unit = OffsetUnit.MONTH;
            else if ("Q".equals(unitChar)) unit = OffsetUnit.QUARTER;
            else if ("W".equals(unitChar)) unit = OffsetUnit.WEEK;
            else if ("D".equals(unitChar)) unit = OffsetUnit.DAY;
            else throw new IllegalArgumentException("Unknown unit: " + unitChar);
            return new DateExpr(DateExpr.Type.RELATIVE, null, unit, amount);
        }

        // Absolute date
        LocalDate date = parseAbsoluteDate(trimmed);
        if (date != null) {
            return new DateExpr(DateExpr.Type.ABSOLUTE, date, null, 0);
        }

        throw new IllegalArgumentException("Cannot parse date expression: " + expr);
    }

    // ---- Helpers ----

    private static boolean isAbsoluteDate(String s) {
        return parseAbsoluteDate(s) != null;
    }

    private static LocalDate parseAbsoluteDate(String s) {
        // Try ISO format first: 2024-01-01
        try {
            return LocalDate.parse(s, ISO_DATE);
        } catch (DateTimeParseException ignored) {}
        // Try compact format: 20240101
        try {
            return LocalDate.parse(s, COMPACT_DATE);
        } catch (DateTimeParseException ignored) {}
        return null;
    }

    // ---- Intermediate representation ----

    public enum OffsetUnit {
        YEAR, QUARTER, MONTH, WEEK, DAY
    }

    /**
     * Dialect-neutral date expression.
     * <p>
     * For {@code NOW}: represents CURRENT_DATE.
     * For {@code RELATIVE}: represents CURRENT_DATE + offset.
     * For {@code ABSOLUTE}: represents a literal date.
     */
    public record DateExpr(
            Type type,
            LocalDate absoluteDate,   // only for ABSOLUTE
            OffsetUnit offsetUnit,    // only for RELATIVE
            int offsetAmount          // only for RELATIVE (negative = past)
    ) {
        public enum Type {
            NOW, RELATIVE, ABSOLUTE
        }

        /**
         * Check if this expression is effectively "now" (current date).
         */
        public boolean isNow() {
            return type == Type.NOW;
        }
    }
}
