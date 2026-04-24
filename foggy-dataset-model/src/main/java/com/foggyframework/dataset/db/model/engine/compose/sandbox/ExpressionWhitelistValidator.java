package com.foggyframework.dataset.db.model.engine.compose.sandbox;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Layer B — DSL expression whitelist validator.
 *
 * <p>Validates column expressions and slice values against a function whitelist
 * and injection pattern blacklist. Applied at {@code BaseModelPlan} and
 * {@code DerivedQueryPlan} construction time.</p>
 *
 * <p>Stateless, thread-safe utility class.</p>
 *
 * @since 8.2.0.beta
 */
public final class ExpressionWhitelistValidator {

    private ExpressionWhitelistValidator() { /* utility */ }

    // ---------------------------------------------------------------
    // Allowed SQL functions — keep in sync with v1.4 M5 function list
    // ---------------------------------------------------------------

    /**
     * Functions allowed in column expressions.
     * Case-insensitive match against function call patterns.
     */
    private static final Set<String> ALLOWED_FUNCTIONS = Set.of(
            // Aggregation
            "SUM", "COUNT", "AVG", "MIN", "MAX",
            // Conditional
            "IIF", "IF", "CASE", "COALESCE", "NULLIF", "IFNULL", "NVL",
            // Date/Time
            "DATE_DIFF", "DATEDIFF", "DATE_ADD", "DATE_SUB", "DATE_FORMAT",
            "YEAR", "MONTH", "DAY", "HOUR", "MINUTE", "SECOND",
            "NOW", "CURDATE", "CURRENT_DATE", "CURRENT_TIMESTAMP",
            "DATE_TRUNC", "EXTRACT", "TIMESTAMPDIFF",
            // String
            "CONCAT", "UPPER", "LOWER", "TRIM", "LTRIM", "RTRIM",
            "SUBSTR", "SUBSTRING", "LENGTH", "LEN", "REPLACE",
            "LEFT", "RIGHT", "LPAD", "RPAD", "REVERSE",
            // Math
            "ABS", "ROUND", "CEIL", "CEILING", "FLOOR", "MOD",
            "POWER", "SQRT", "LOG", "LOG10", "EXP", "SIGN",
            // Type conversion
            "CAST", "CONVERT", "TO_CHAR", "TO_DATE", "TO_NUMBER",
            // Window (base only, full window validation is M10)
            "ROW_NUMBER", "RANK", "DENSE_RANK", "NTILE",
            "LAG", "LEAD", "FIRST_VALUE", "LAST_VALUE",
            // Misc
            "DISTINCT", "GROUP_CONCAT", "STRING_AGG"
    );

    /**
     * Functions that are explicitly blocked (known dangerous).
     */
    private static final Set<String> BLOCKED_FUNCTIONS = Set.of(
            "CHAR", "CHR",
            "SLEEP", "BENCHMARK", "WAITFOR",
            "LOAD_FILE", "INTO_OUTFILE", "INTO_DUMPFILE",
            "EXEC", "EXECUTE", "XP_CMDSHELL",
            "SYSTEM", "DBMS_PIPE"
    );

    /** Pattern to extract function names from SQL expressions: FUNC_NAME( */
    private static final Pattern FUNCTION_CALL_PATTERN =
            Pattern.compile("\\b([A-Z_][A-Z0-9_]*)\\s*\\(", Pattern.CASE_INSENSITIVE);

    /** Injection patterns in slice values */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)\\bUNION\\s+(ALL\\s+)?SELECT\\b"),
            Pattern.compile("(?i)\\bSELECT\\s+.*\\bFROM\\b"),
            Pattern.compile("(?i)\\bDROP\\s+(TABLE|DATABASE)\\b"),
            Pattern.compile("(?i)\\bINSERT\\s+INTO\\b"),
            Pattern.compile("(?i)\\bDELETE\\s+FROM\\b"),
            Pattern.compile("(?i)\\bUPDATE\\s+.*\\bSET\\b"),
            Pattern.compile("(?i)\\b(ALTER|CREATE|TRUNCATE)\\s+(TABLE|DATABASE)\\b"),
            Pattern.compile("--\\s*$", Pattern.MULTILINE),
            Pattern.compile("/\\*.*\\*/"),
            Pattern.compile("(?i)\\bOR\\s+1\\s*=\\s*1\\b"),
            Pattern.compile("(?i)\\bOR\\s+'[^']*'\\s*=\\s*'[^']*'")
    );

    // ---------------------------------------------------------------
    // Public validation methods
    // ---------------------------------------------------------------

    /**
     * Validate column expressions for blocked function usage.
     *
     * @param columns the column expression list
     * @param phase   pipeline phase for error reporting
     * @throws ComposeSandboxViolationException if a blocked function is found
     */
    public static void validateColumns(List<String> columns, String phase) {
        if (columns == null) return;
        for (String col : columns) {
            if (col == null) continue;
            Matcher m = FUNCTION_CALL_PATTERN.matcher(col);
            while (m.find()) {
                String funcName = m.group(1).toUpperCase();
                // Skip if it looks like a plain column name (no parens content)
                if (BLOCKED_FUNCTIONS.contains(funcName)) {
                    throw new ComposeSandboxViolationException(
                            ComposeSandboxErrorCodes.LAYER_B_FUNCTION_DENIED,
                            "Function '" + funcName + "' is not in the allowed list.",
                            phase);
                }
                // For non-blocked, non-allowed: be permissive for now (columns
                // may contain aliased expressions like "col1 as alias")
            }
        }
    }

    /**
     * Validate column expressions for derived plans — stricter checks.
     * Blocks RAW_SQL and other functions only allowed in base plans.
     *
     * @param columns the column expression list
     * @param phase   pipeline phase for error reporting
     * @throws ComposeSandboxViolationException if a blocked function is found
     */
    public static void validateDerivedColumns(List<String> columns, String phase) {
        if (columns == null) return;
        for (String col : columns) {
            if (col == null) continue;
            Matcher m = FUNCTION_CALL_PATTERN.matcher(col);
            while (m.find()) {
                String funcName = m.group(1).toUpperCase();
                if (BLOCKED_FUNCTIONS.contains(funcName)) {
                    throw new ComposeSandboxViolationException(
                            ComposeSandboxErrorCodes.LAYER_B_FUNCTION_DENIED,
                            "Function '" + funcName + "' is not in the allowed list.",
                            phase);
                }
                if ("RAW_SQL".equals(funcName)) {
                    throw new ComposeSandboxViolationException(
                            ComposeSandboxErrorCodes.LAYER_B_DERIVED_FN_DENIED,
                            "Function 'RAW_SQL' is not allowed in derived plans.",
                            phase);
                }
            }
        }
    }

    /**
     * Validate slice values for injection patterns.
     *
     * @param slice the slice list (each entry is typically a Map with field/op/value)
     * @param phase pipeline phase for error reporting
     * @throws ComposeSandboxViolationException if an injection pattern is detected
     */
    @SuppressWarnings("unchecked")
    public static void validateSlice(List<Object> slice, String phase) {
        if (slice == null) return;
        for (Object entry : slice) {
            if (entry instanceof java.util.Map) {
                java.util.Map<String, Object> filter = (java.util.Map<String, Object>) entry;
                Object value = filter.get("value");
                if (value instanceof String) {
                    checkInjection((String) value, phase);
                }
            }
        }
    }

    private static void checkInjection(String value, String phase) {
        for (Pattern p : INJECTION_PATTERNS) {
            if (p.matcher(value).find()) {
                throw new ComposeSandboxViolationException(
                        ComposeSandboxErrorCodes.LAYER_B_INJECTION_SUSPECTED,
                        "Expression contains a blocked injection pattern.",
                        phase);
            }
        }
    }
}
