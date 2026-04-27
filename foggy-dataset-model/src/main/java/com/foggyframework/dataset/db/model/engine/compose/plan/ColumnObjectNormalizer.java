package com.foggyframework.dataset.db.model.engine.compose.plan;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * G5 Phase 1 (F4) — Column object normalizer.
 *
 * <p>Normalizes {@code dsl({columns: [...]})} entries from the F4 object form
 * (e.g. {@code {field: "amount", agg: "sum", as: "totalSales"}}) to the
 * canonical string form (e.g. {@code "SUM(amount) AS totalSales"}). Downstream
 * compilation / validation is unchanged.</p>
 *
 * <h3>Supported forms</h3>
 * <ul>
 *   <li><b>F1-F3 string</b> (passthrough): {@code "name"} / {@code "name AS alias"} /
 *       {@code "SUM(amount) AS total"} / {@code "YEAR(orderDate) AS year"}</li>
 *   <li><b>F4 object</b>: {@code {field, agg?, as?}} — required {@code field}, optional
 *       {@code agg} (whitelist below) and {@code as} (string alias)</li>
 *   <li><b>F5 object</b> ({@code {plan, field, ...}}): currently fail-loud with
 *       {@code COLUMN_PLAN_NOT_VISIBLE}; F5 is Phase 2 and blocked on G10</li>
 * </ul>
 *
 * <h3>Aggregation whitelist</h3>
 * <p>{@code sum}, {@code avg}, {@code count}, {@code max}, {@code min},
 * {@code count_distinct}. The last is lowered to {@code COUNT_DISTINCT(field)}
 * which the SQL engine ({@code AllowedFunctions} + {@code SqlFunctionExp})
 * automatically translates to {@code COUNT(DISTINCT field)}.</p>
 *
 * <h3>Error codes</h3>
 * <ul>
 *   <li>{@code COLUMN_FIELD_REQUIRED} — F4 object missing {@code field} or null/blank</li>
 *   <li>{@code COLUMN_AGG_NOT_SUPPORTED} — {@code agg} not in whitelist</li>
 *   <li>{@code COLUMN_AS_TYPE_INVALID} — {@code as} is not a string</li>
 *   <li>{@code COLUMN_FIELD_INVALID_KEY} — F4 object contains an unknown key</li>
 *   <li>{@code COLUMN_PLAN_NOT_VISIBLE} — F5 placeholder; F5 is Phase 2 (blocked on G10)</li>
 * </ul>
 *
 * @see <a href="../../../../../../../../../docs/8.3.0.beta/P0-SemanticDSL-列项对象语法-后置消歧设计.md">G5 spec v2-patch</a>
 */
public final class ColumnObjectNormalizer {

    /**
     * Aggregation function whitelist (case-insensitive). Lowercase canonical form is
     * used internally; output is uppercased for SQL emission.
     *
     * <p>Aligned with Compose {@code AggregateColumn} / {@code SemanticQueryRequest}
     * aggregation capability — NOT the formula scalar-function whitelist
     * (those are different concepts; see G5 spec v2-patch §2.3).</p>
     */
    public static final Set<String> ALLOWED_AGG = Set.of(
        "sum", "avg", "count", "max", "min", "count_distinct"
    );

    /**
     * Allowed keys in F4 object form. Object containing other keys (e.g. {@code plan}
     * for F5) triggers a fail-loud error in the current Phase 1.
     */
    public static final Set<String> ALLOWED_F4_KEYS = Set.of("field", "agg", "as");

    private ColumnObjectNormalizer() {
        // Utility class
    }

    /**
     * Normalize a single column entry. F1-F3 strings pass through unchanged;
     * F4 objects are converted to their string equivalent. Other types
     * (e.g. {@code PlanColumnRef} from chained API) pass through unchanged
     * for downstream handling.
     *
     * @param item  the column entry (String, Map, or other)
     * @param index 0-based index in the columns array (for error messages)
     * @return normalized form (String for F1-F4; passthrough for others)
     * @throws IllegalArgumentException with a {@code COLUMN_*} error-code prefix on F4 validation failure
     */
    public static Object normalize(Object item, int index) {
        if (item == null) {
            // Null entries are passed through; downstream behaviour unchanged
            // from pre-G5 (DslQueryFunction.toStringList skipped nulls).
            return null;
        }
        if (item instanceof String) {
            // F1-F3: passthrough
            return item;
        }
        if (item instanceof Map) {
            return normalizeMap((Map<?, ?>) item, index);
        }
        // Other types (PlanColumnRef, AggregateColumn, etc.) — passthrough.
        // These come from chained API or programmatic construction; F4 doesn't touch them.
        return item;
    }

    /**
     * Normalize a list of column entries. Returns a new {@link java.util.ArrayList}
     * (not a copy of the input list) — caller may further refine.
     */
    public static java.util.List<Object> normalizeColumns(java.util.List<?> rawColumns) {
        if (rawColumns == null) {
            return new ArrayList<>();
        }
        java.util.List<Object> result = new ArrayList<>(rawColumns.size());
        for (int i = 0; i < rawColumns.size(); i++) {
            Object normalized = normalize(rawColumns.get(i), i);
            result.add(normalized);
        }
        return result;
    }

    /**
     * Normalize a list of column entries to {@code List<String>}. Used by legacy
     * paths (e.g. {@code DslQueryFunction.buildRequest()}) that strictly require
     * strings downstream. Non-String, non-Map entries fall back to {@code toString()}.
     *
     * @return new {@code List<String>}; null entries skipped (legacy behavior)
     */
    public static java.util.List<String> normalizeColumnsToStrings(java.util.List<?> rawColumns) {
        if (rawColumns == null) {
            return new ArrayList<>();
        }
        java.util.List<String> result = new ArrayList<>(rawColumns.size());
        for (int i = 0; i < rawColumns.size(); i++) {
            Object normalized = normalize(rawColumns.get(i), i);
            if (normalized == null) {
                continue;
            }
            result.add(normalized instanceof String ? (String) normalized : normalized.toString());
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Internal: normalize one Map (F4 / F5)
    // ------------------------------------------------------------------

    private static String normalizeMap(Map<?, ?> raw, int index) {
        // Phase 2 placeholder — F5 plan-qualified form not yet supported (blocked on G10).
        if (raw.containsKey("plan")) {
            throw new IllegalArgumentException(
                "COLUMN_PLAN_NOT_VISIBLE: columns[" + index + "] uses plan-qualified syntax "
                + "{plan, field, ...} which is Phase 2 of G5 and currently blocked on G10 "
                + "engine refactor. As a workaround, rename in source plans using "
                + "\"name AS alias\" and reference the alias instead.");
        }

        // Validate keys
        for (Object key : raw.keySet()) {
            if (!(key instanceof String) || !ALLOWED_F4_KEYS.contains(key)) {
                throw new IllegalArgumentException(
                    "COLUMN_FIELD_INVALID_KEY: columns[" + index + "] contains unknown key "
                    + (key == null ? "null" : "'" + key + "'") + ". Allowed keys: "
                    + ALLOWED_F4_KEYS);
            }
        }

        // field — required
        Object fieldObj = raw.get("field");
        if (!(fieldObj instanceof String) || ((String) fieldObj).isBlank()) {
            throw new IllegalArgumentException(
                "COLUMN_FIELD_REQUIRED: columns[" + index + "] missing required 'field' "
                + "(must be a non-empty string, got " + (fieldObj == null ? "null" : fieldObj.getClass().getSimpleName()) + ")");
        }
        String field = ((String) fieldObj).trim();

        // as — optional
        String alias = null;
        if (raw.containsKey("as")) {
            Object asObj = raw.get("as");
            if (asObj != null && !(asObj instanceof String)) {
                throw new IllegalArgumentException(
                    "COLUMN_AS_TYPE_INVALID: columns[" + index + "] 'as' must be a string, "
                    + "got " + asObj.getClass().getSimpleName());
            }
            if (asObj != null) {
                String asStr = ((String) asObj).trim();
                if (!asStr.isEmpty()) {
                    alias = asStr;
                }
            }
        }

        // agg — optional
        String agg = null;
        if (raw.containsKey("agg")) {
            Object aggObj = raw.get("agg");
            if (!(aggObj instanceof String) || ((String) aggObj).isBlank()) {
                throw new IllegalArgumentException(
                    "COLUMN_AGG_NOT_SUPPORTED: columns[" + index + "] 'agg' must be a "
                    + "non-empty string in " + ALLOWED_AGG + ", got " + aggObj);
            }
            String aggLower = ((String) aggObj).trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_AGG.contains(aggLower)) {
                throw new IllegalArgumentException(
                    "COLUMN_AGG_NOT_SUPPORTED: columns[" + index + "] agg '" + aggObj
                    + "' is not in the whitelist " + ALLOWED_AGG
                    + ". (Note: 'count_distinct' is supported and lowers to COUNT(DISTINCT field).)");
            }
            agg = aggLower;
        }

        // Build the canonical string form
        StringBuilder sb = new StringBuilder();
        if (agg != null) {
            // Uppercase for SQL convention; count_distinct → COUNT_DISTINCT(...) is
            // recognised by AllowedFunctions and lowered to COUNT(DISTINCT ...) in
            // SqlFunctionExp.
            sb.append(agg.toUpperCase(Locale.ROOT)).append('(').append(field).append(')');
        } else {
            sb.append(field);
        }
        if (alias != null) {
            sb.append(" AS ").append(alias);
        }
        return sb.toString();
    }
}
