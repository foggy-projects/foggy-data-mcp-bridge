package com.foggyframework.dataset.model.engine.compose.compilation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * S7f · Restricted parser for window select expressions.
 *
 * <p>Parses structured SQL-like window expressions into
 * {@link WindowSelectSpec} instances. Only the S7f-approved
 * function subset is accepted; arbitrary raw SQL is rejected.</p>
 *
 * <p>Accepted shapes:
 * <pre>
 *   RANK() OVER (ORDER BY col DESC) AS alias
 *   ROW_NUMBER() OVER (ORDER BY col) AS alias
 *   AVG(col) OVER (PARTITION BY dim ORDER BY date ROWS BETWEEN 2 PRECEDING AND CURRENT ROW) AS alias
 *   LAG(col) OVER (ORDER BY date) AS alias
 *   COUNT(*) OVER (PARTITION BY dim) AS alias
 * </pre>
 *
 * @since 8.5.0.beta (S7f)
 */
public final class WindowSelectParser {

    private WindowSelectParser() { /* utility */ }

    private static final Set<String> ALL_FUNCTIONS;
    static {
        Set<String> fns = new java.util.LinkedHashSet<>();
        fns.addAll(WindowSelectSpec.RANKING_FUNCTIONS);
        fns.addAll(WindowSelectSpec.OFFSET_FUNCTIONS);
        fns.addAll(WindowSelectSpec.AGGREGATE_WINDOW_FUNCTIONS);
        ALL_FUNCTIONS = Set.copyOf(fns);
    }

    // Pattern: FUNC([input]) OVER (...) [AS alias]
    // Groups: 1=func, 2=input (may be empty or *), 3=over_body, 4=alias (optional)
    private static final Pattern WINDOW_EXPR_PATTERN = Pattern.compile(
            "^\\s*(ROW_NUMBER|RANK|DENSE_RANK|LAG|LEAD|SUM|AVG|MIN|MAX|COUNT)"
                    + "\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$]*|\\*)?\\s*\\)"
                    + "\\s+OVER\\s*\\(\\s*(.*)\\s*\\)"
                    + "(?:\\s+AS\\s+([A-Za-z_][A-Za-z0-9_$]*))?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern FRAME_BOUND_PATTERN = Pattern.compile(
            "(?:UNBOUNDED\\s+PRECEDING|UNBOUNDED\\s+FOLLOWING|CURRENT\\s+ROW|\\d+\\s+PRECEDING|\\d+\\s+FOLLOWING)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FRAME_PATTERN = Pattern.compile(
            "^(ROWS|RANGE)\\s+(?:(BETWEEN)\\s+(.+)\\s+AND\\s+(.+)|(.+))$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Parse a window expression string.
     *
     * @param expr the raw select expression containing OVER(...)
     * @return a validated {@link WindowSelectSpec}
     * @throws ComposeCompileException if the expression cannot be parsed
     *         or uses an unsupported function
     */
    public static WindowSelectSpec parse(String expr) {
        if (expr == null || expr.isBlank()) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.RELATION_OUTER_WINDOW_NOT_SUPPORTED,
                    ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                    "Empty window expression.");
        }

        Matcher m = WINDOW_EXPR_PATTERN.matcher(expr);
        if (!m.matches()) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.RELATION_OUTER_WINDOW_NOT_SUPPORTED,
                    ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                    "Unsupported window expression format: '" + expr
                            + "'. Expected: FUNC([col]) OVER (...) [AS alias].");
        }

        String function = m.group(1).toUpperCase(Locale.ROOT);
        String inputRaw = m.group(2);       // may be null or empty for ranking
        String overBody = m.group(3).trim();
        String alias = m.group(4);

        // Validate function name
        if (!ALL_FUNCTIONS.contains(function)) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.RELATION_OUTER_WINDOW_NOT_SUPPORTED,
                    ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                    "Unsupported window function: '" + function + "'.");
        }

        // Parse input column
        String inputColumn = null;
        if (inputRaw != null && !inputRaw.isBlank()) {
            inputColumn = inputRaw.trim();
        }

        // Ranking functions must not have an input column
        if (WindowSelectSpec.RANKING_FUNCTIONS.contains(function)
                && inputColumn != null) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.RELATION_OUTER_WINDOW_NOT_SUPPORTED,
                    ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                    "Ranking function " + function
                            + " must not have an input column: '" + expr + "'.");
        }

        // Offset/aggregate functions must have an input column
        if ((WindowSelectSpec.OFFSET_FUNCTIONS.contains(function)
                || WindowSelectSpec.AGGREGATE_WINDOW_FUNCTIONS.contains(function))
                && inputColumn == null) {
            // Exception: COUNT without input is treated as COUNT(*)
            if ("COUNT".equals(function)) {
                inputColumn = "*";
            } else {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_OUTER_WINDOW_NOT_SUPPORTED,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        function + " window function requires an input column: '"
                                + expr + "'.");
            }
        }

        // Only COUNT(*) is allowed with star
        if ("*".equals(inputColumn) && !"COUNT".equals(function)) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.RELATION_OUTER_WINDOW_NOT_SUPPORTED,
                    ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                    "Only COUNT(*) is allowed with star input: '" + expr + "'.");
        }

        // Parse OVER body: PARTITION BY, ORDER BY, frame
        List<String> partitionBy = new ArrayList<>();
        List<String> orderBy = new ArrayList<>();
        String frame = null;

        if (!overBody.isEmpty()) {
            String upperOver = overBody.toUpperCase(Locale.ROOT);

            // Extract PARTITION BY
            int partIdx = upperOver.indexOf("PARTITION BY");
            int ordIdx = upperOver.indexOf("ORDER BY");
            int frameIdx = findFrameStart(upperOver);

            if (partIdx >= 0) {
                int partEnd = ordIdx >= 0 ? ordIdx : (frameIdx >= 0 ? frameIdx : overBody.length());
                String partClause = overBody.substring(partIdx + "PARTITION BY".length(), partEnd).trim();
                partitionBy = parseColumnList(partClause);
            }

            if (ordIdx >= 0) {
                int ordEnd = frameIdx >= 0 ? frameIdx : overBody.length();
                String ordClause = overBody.substring(ordIdx + "ORDER BY".length(), ordEnd).trim();
                orderBy = parseOrderByList(ordClause);
            }

            if (frameIdx >= 0) {
                frame = overBody.substring(frameIdx).trim();
                validateFrame(frame, expr);
            }
        }

        // Generate default alias if not provided
        if (alias == null || alias.isBlank()) {
            alias = defaultWindowAlias(function, inputColumn);
        }

        return new WindowSelectSpec(function, inputColumn,
                partitionBy, orderBy, frame, alias);
    }

    /**
     * Detect whether the given expression contains a window function OVER(...).
     */
    static boolean isWindowExpression(String expr) {
        if (expr == null) return false;
        return expr.toUpperCase(Locale.ROOT).contains(" OVER")
                || expr.toUpperCase(Locale.ROOT).contains(" OVER(");
    }

    private static int findFrameStart(String upperOver) {
        // Frame starts with ROWS or RANGE
        int rowsIdx = upperOver.indexOf("ROWS ");
        int rangeIdx = upperOver.indexOf("RANGE ");
        if (rowsIdx >= 0 && rangeIdx >= 0) {
            return Math.min(rowsIdx, rangeIdx);
        }
        return rowsIdx >= 0 ? rowsIdx : rangeIdx;
    }

    private static void validateFrame(String frame, String expr) {
        Matcher frameMatcher = FRAME_PATTERN.matcher(frame);
        if (!frameMatcher.matches()) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.RELATION_OUTER_WINDOW_NOT_SUPPORTED,
                    ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                    "Unsupported window frame clause in expression: '" + expr + "'.");
        }

        String between = frameMatcher.group(2);
        if (between != null) {
            validateFrameBound(frameMatcher.group(3), expr);
            validateFrameBound(frameMatcher.group(4), expr);
            return;
        }

        validateFrameBound(frameMatcher.group(5), expr);
    }

    private static void validateFrameBound(String bound, String expr) {
        String normalized = bound == null ? "" : bound.trim();
        if (!FRAME_BOUND_PATTERN.matcher(normalized).matches()) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.RELATION_OUTER_WINDOW_NOT_SUPPORTED,
                    ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                    "Unsupported window frame bound '" + normalized
                            + "' in expression: '" + expr + "'.");
        }
    }

    private static List<String> parseColumnList(String clause) {
        List<String> cols = new ArrayList<>();
        if (clause == null || clause.isBlank()) return cols;
        for (String part : clause.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                cols.add(extractBaseColumnName(trimmed));
            }
        }
        return cols;
    }

    private static List<String> parseOrderByList(String clause) {
        List<String> cols = new ArrayList<>();
        if (clause == null || clause.isBlank()) return cols;
        for (String part : clause.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                cols.add(trimmed);
            }
        }
        return cols;
    }

    /**
     * Extract the base column name from an ORDER BY clause token,
     * stripping ASC/DESC suffix.
     */
    static String extractOrderByBase(String clause) {
        String trimmed = clause.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.endsWith(" ASC")) {
            return trimmed.substring(0, trimmed.length() - 4).trim();
        }
        if (upper.endsWith(" DESC")) {
            return trimmed.substring(0, trimmed.length() - 5).trim();
        }
        return trimmed;
    }

    private static String extractBaseColumnName(String token) {
        String trimmed = token.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.endsWith(" ASC")) {
            return trimmed.substring(0, trimmed.length() - 4).trim();
        }
        if (upper.endsWith(" DESC")) {
            return trimmed.substring(0, trimmed.length() - 5).trim();
        }
        return trimmed;
    }

    private static String defaultWindowAlias(String function, String inputColumn) {
        String fn = function.toLowerCase(Locale.ROOT);
        if (inputColumn == null || "*".equals(inputColumn)) {
            return fn;
        }
        return fn + "_" + inputColumn;
    }
}
