package com.foggyframework.dataset.db.model.engine.compose.sandbox;

import com.foggyframework.dataset.db.model.engine.compose.plan.AggregateColumn;
import com.foggyframework.dataset.db.model.engine.compose.plan.PlanColumnRef;
import com.foggyframework.dataset.db.model.engine.compose.plan.ProjectedColumn;
import com.foggyframework.dataset.db.model.engine.compose.plan.WindowColumn;
import com.foggyframework.dataset.db.model.engine.compose.plan.expr.BinaryExpr;
import com.foggyframework.dataset.db.model.engine.compose.plan.expr.CaseWhenExpr;
import com.foggyframework.dataset.db.model.engine.compose.plan.expr.ColumnExpr;
import com.foggyframework.dataset.db.model.engine.compose.plan.expr.LiteralExpr;
import com.foggyframework.dataset.db.model.engine.compose.plan.expr.PlanExpression;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Layer B — DSL expression whitelist validator.
 *
 * <p>Validates column expressions and slice values against a function whitelist
 * and injection pattern blacklist. Applied at {@code BaseModelPlan} and
 * {@code DerivedQueryPlan} construction time, and at the {@code script-eval}
 * boundary in {@link com.foggyframework.dataset.db.model.engine.compose.runtime.ScriptRuntime}.</p>
 *
 * <p>Heterogeneous columns: each element may be either a raw {@link String}
 * (legacy / SQL-ish source path) or a typed {@link PlanExpression} sub-tree
 * (preferred / AST path). String elements are scanned for blocked function
 * names; AST elements are walked recursively with each node checked
 * fail-closed against the recognised set. Unknown {@code PlanExpression}
 * subtypes raise {@link ComposeSandboxErrorCodes#LAYER_B_FUNCTION_DENIED}
 * with a phase tag — sandboxes never silently accept new node types.</p>
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

    /** Operators allowed inside {@link BinaryExpr}. SQL set operators
     *  (e.g. {@code IS}, {@code LIKE}) are intentionally absent — those
     *  are slice-side concerns, not column-expression operators. */
    private static final Set<String> ALLOWED_BINARY_OPS = Set.of(
            "+", "-", "*", "/", "%",
            "=", "==", "!=", "<>", "<", "<=", ">", ">=",
            "AND", "OR", "&&", "||"
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
     * Validate column expressions for blocked function usage. Each element
     * may be a {@link String} (legacy SQL-ish source) or a {@link PlanExpression}
     * AST sub-tree. Unknown {@code PlanExpression} subtypes are rejected
     * fail-closed.
     *
     * @param columns the heterogeneous column list
     * @param phase   pipeline phase for error reporting
     * @throws ComposeSandboxViolationException if a blocked function or
     *         unrecognised AST node is found
     */
    public static void validateColumns(List<?> columns, String phase) {
        if (columns == null) return;
        for (Object col : columns) {
            if (col == null) continue;
            if (col instanceof String s) {
                validateStringExpression(s, phase, /* derived */ false);
            } else if (col instanceof PlanExpression expr) {
                validatePlanExpression(expr, phase, /* derived */ false);
            } else {
                throw new ComposeSandboxViolationException(
                        ComposeSandboxErrorCodes.LAYER_B_FUNCTION_DENIED,
                        "Unrecognised column element type: " + col.getClass().getName(),
                        phase);
            }
        }
    }

    /**
     * Validate column expressions for derived plans — stricter than
     * {@link #validateColumns(List, String)}: also blocks {@code RAW_SQL}
     * which is reserved for base-model plans.
     *
     * @param columns the heterogeneous column list
     * @param phase   pipeline phase for error reporting
     */
    public static void validateDerivedColumns(List<?> columns, String phase) {
        if (columns == null) return;
        for (Object col : columns) {
            if (col == null) continue;
            if (col instanceof String s) {
                validateStringExpression(s, phase, /* derived */ true);
            } else if (col instanceof PlanExpression expr) {
                validatePlanExpression(expr, phase, /* derived */ true);
            } else {
                throw new ComposeSandboxViolationException(
                        ComposeSandboxErrorCodes.LAYER_B_FUNCTION_DENIED,
                        "Unrecognised column element type: " + col.getClass().getName(),
                        phase);
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

    // ---------------------------------------------------------------
    // String-source validation (legacy path)
    // ---------------------------------------------------------------

    private static void validateStringExpression(String expr, String phase, boolean derived) {
        Matcher m = FUNCTION_CALL_PATTERN.matcher(expr);
        while (m.find()) {
            String funcName = m.group(1).toUpperCase();
            if (BLOCKED_FUNCTIONS.contains(funcName)) {
                throw new ComposeSandboxViolationException(
                        ComposeSandboxErrorCodes.LAYER_B_FUNCTION_DENIED,
                        "Function '" + funcName + "' is not in the allowed list.",
                        phase);
            }
            if (derived && "RAW_SQL".equals(funcName)) {
                throw new ComposeSandboxViolationException(
                        ComposeSandboxErrorCodes.LAYER_B_DERIVED_FN_DENIED,
                        "Function 'RAW_SQL' is not allowed in derived plans.",
                        phase);
            }
            // For non-blocked, non-allowed: be permissive — strings may carry
            // aliased expressions like "col1 as alias" or arithmetic like
            // "a + b". Future tightening can add ALLOWED_FUNCTIONS gate here.
        }
    }

    // ---------------------------------------------------------------
    // PlanExpression AST validation (preferred path)
    // ---------------------------------------------------------------

    private static void validatePlanExpression(PlanExpression expr, String phase, boolean derived) {
        if (expr == null) return;

        if (expr instanceof ColumnExpr || expr instanceof PlanColumnRef
                || expr instanceof LiteralExpr) {
            // Leaf nodes — typed values, no function call surface.
            return;
        }
        if (expr instanceof ProjectedColumn pc) {
            validatePlanExpression(pc.expr(), phase, derived);
            return;
        }
        if (expr instanceof AggregateColumn agg) {
            assertFunctionAllowed(agg.func(), phase, derived);
            // ref is a PlanColumnRef leaf — nothing to recurse.
            return;
        }
        if (expr instanceof WindowColumn win) {
            assertFunctionAllowed(win.func(), phase, derived);
            return;
        }
        if (expr instanceof BinaryExpr bin) {
            String op = bin.op();
            if (op == null || !ALLOWED_BINARY_OPS.contains(op.toUpperCase())) {
                throw new ComposeSandboxViolationException(
                        ComposeSandboxErrorCodes.LAYER_B_FUNCTION_DENIED,
                        "Binary operator '" + op + "' is not in the allowed list.",
                        phase);
            }
            validatePlanExpression(bin.left(), phase, derived);
            validatePlanExpression(bin.right(), phase, derived);
            return;
        }
        if (expr instanceof CaseWhenExpr cw) {
            for (CaseWhenExpr.WhenThen wt : cw.whens()) {
                validatePlanExpression(wt.condition(), phase, derived);
                validatePlanExpression(wt.result(), phase, derived);
            }
            if (cw.elseExpr() != null) {
                validatePlanExpression(cw.elseExpr(), phase, derived);
            }
            return;
        }
        // Fail-closed: unknown PlanExpression subtype must not silently pass.
        throw new ComposeSandboxViolationException(
                ComposeSandboxErrorCodes.LAYER_B_FUNCTION_DENIED,
                "Unrecognised PlanExpression subtype: " + expr.getClass().getName(),
                phase);
    }

    private static void assertFunctionAllowed(String funcName, String phase, boolean derived) {
        if (funcName == null) {
            throw new ComposeSandboxViolationException(
                    ComposeSandboxErrorCodes.LAYER_B_FUNCTION_DENIED,
                    "Function name is null.",
                    phase);
        }
        String upper = funcName.toUpperCase();
        if (BLOCKED_FUNCTIONS.contains(upper)) {
            throw new ComposeSandboxViolationException(
                    ComposeSandboxErrorCodes.LAYER_B_FUNCTION_DENIED,
                    "Function '" + funcName + "' is not in the allowed list.",
                    phase);
        }
        if (derived && "RAW_SQL".equals(upper)) {
            throw new ComposeSandboxViolationException(
                    ComposeSandboxErrorCodes.LAYER_B_DERIVED_FN_DENIED,
                    "Function 'RAW_SQL' is not allowed in derived plans.",
                    phase);
        }
        if (!ALLOWED_FUNCTIONS.contains(upper)) {
            throw new ComposeSandboxViolationException(
                    ComposeSandboxErrorCodes.LAYER_B_FUNCTION_DENIED,
                    "Function '" + funcName + "' is not in the allowed list.",
                    phase);
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
