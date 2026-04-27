package com.foggyframework.dataset.db.model.engine.compose.plan;

import com.foggyframework.dataset.db.model.engine.compose.plan.expr.PlanExpression;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * G5 Phase 1 (F4) + Phase 2 (F5) — Column object normalizer.
 *
 * <p>Normalizes {@code dsl({columns: [...]})} entries:
 * <ul>
 *   <li><b>F4</b> {@code {field, agg?, as?}} → canonical string form
 *       ({@code "SUM(amount) AS totalSales"}); downstream string-oriented
 *       compilation / validation is unchanged.</li>
 *   <li><b>F5</b> {@code {plan, field, agg?, as?}} → {@link PlanColumnRef} /
 *       {@link AggregateColumn} / {@link ProjectedColumn} compound — same
 *       object shape as the chained API ({@code sales.amount.sum().as("t")})
 *       so downstream {@code ComposePlanner.compilePlanColumnRef} (G10 PR3)
 *       handles them transparently when {@code g10Enabled()} is on.</li>
 * </ul>
 *
 * <h3>Supported forms</h3>
 * <ul>
 *   <li><b>F1-F3 string</b> (passthrough): {@code "name"} / {@code "name AS alias"} /
 *       {@code "SUM(amount) AS total"} / {@code "YEAR(orderDate) AS year"}</li>
 *   <li><b>F4 object</b>: {@code {field, agg?, as?}} — required {@code field}, optional
 *       {@code agg} (whitelist below) and {@code as} (string alias)</li>
 *   <li><b>F5 object</b>: {@code {plan, field, agg?, as?}} — adds required
 *       {@code plan} ({@link QueryPlan} reference). Returns a chained-API-style
 *       compound (NOT a string); legacy string-only consumers must reject it
 *       (see {@link #normalizeColumnsToStrings})</li>
 * </ul>
 *
 * <h3>Aggregation whitelist</h3>
 * <p>{@code sum}, {@code avg}, {@code count}, {@code max}, {@code min},
 * {@code count_distinct}. {@code count_distinct} lowers to
 * {@code COUNT_DISTINCT(field)} which the SQL engine
 * ({@code AllowedFunctions} + {@code SqlFunctionExp}) automatically
 * translates to {@code COUNT(DISTINCT field)}. F4 lowers to a string;
 * F5 produces an {@link AggregateColumn} carrying the uppercase function
 * token.</p>
 *
 * <h3>Error codes</h3>
 * <ul>
 *   <li>{@code COLUMN_FIELD_REQUIRED} — F4/F5 object missing {@code field} or null/blank</li>
 *   <li>{@code COLUMN_AGG_NOT_SUPPORTED} — {@code agg} not in whitelist</li>
 *   <li>{@code COLUMN_AS_TYPE_INVALID} — {@code as} is not a string</li>
 *   <li>{@code COLUMN_FIELD_INVALID_KEY} — F4/F5 object contains an unknown key</li>
 *   <li>{@code COLUMN_PLAN_TYPE_INVALID} — F5 {@code plan} value is not a {@link QueryPlan}</li>
 * </ul>
 *
 * <p>Plan-lineage visibility (spec §5.1) is checked at plan build time
 * ({@code BaseModelPlan.Builder.build} / {@code DerivedQueryPlan.Builder.build}),
 * NOT here, because normalize does not have outer-plan context. Visibility
 * violations surface as {@code COLUMN_PLAN_NOT_VISIBLE}; cross-plan F5 under
 * G10 OFF surfaces as {@code COLUMN_PLAN_NOT_BOUND}.</p>
 *
 * @see <a href="../../../../../../../../../docs/8.3.0.beta/P0-SemanticDSL-列项对象语法-后置消歧设计.md">G5 spec v2-patch-2</a>
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
     * for F5) triggers a fail-loud error.
     */
    public static final Set<String> ALLOWED_F4_KEYS = Set.of("field", "agg", "as");

    /**
     * Allowed keys in F5 object form. Adds {@code plan} on top of F4 keys.
     * Detected by the presence of the {@link #F5_PLAN_KEY} key in the input map.
     */
    public static final Set<String> ALLOWED_F5_KEYS = Set.of("plan", "field", "agg", "as");

    /** Sentinel key whose presence in a column-object dict triggers the
     *  F5 plan-qualified path (vs. the F4 path that uses
     *  {@link #ALLOWED_F4_KEYS}). Hardcoded {@code "plan"} matches the
     *  spec §2.4 surface and the Python parity constant. */
    private static final String F5_PLAN_KEY = "plan";

    private ColumnObjectNormalizer() {
        // Utility class
    }

    /**
     * Normalize a single column entry. F1-F3 strings pass through unchanged;
     * F4 objects are converted to their string equivalent; F5 objects are
     * converted to a chained-API-style compound ({@link PlanColumnRef} /
     * {@link AggregateColumn} / {@link ProjectedColumn}). Other types
     * (e.g. {@code PlanColumnRef} from chained API) pass through unchanged
     * for downstream handling.
     *
     * @param item  the column entry (String, Map, or other)
     * @param index 0-based index in the columns array (for error messages)
     * @return normalized form: String for F1-F4; {@link PlanExpression} for F5;
     *         passthrough for chained-API objects
     * @throws IllegalArgumentException with a {@code COLUMN_*} error-code prefix on F4/F5 validation failure
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
        // Other types (PlanColumnRef, AggregateColumn, ProjectedColumn,
        // WindowColumn, etc.) — passthrough. These come from chained API or
        // programmatic construction; F4 doesn't touch them.
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
     * paths (e.g. {@code DslQueryFunction.buildRequest()},
     * {@code ComposedDataSetResult.toStringList}) that strictly require strings
     * downstream — i.e. paths that build {@code SemanticQueryRequest.columns:
     * List<String>}, which cannot carry F5 plan-qualified references.
     *
     * <p>F1-F3 strings pass through; F4 maps are normalized to their canonical
     * string form; F5 maps and any chained-API plan-expression object
     * ({@link PlanColumnRef} / {@link AggregateColumn} / {@link ProjectedColumn})
     * are <b>rejected fail-loud</b> with {@code COLUMN_PLAN_TYPE_INVALID}
     * (G5 spec §10.3 item 5).</p>
     *
     * <p>Rejection is deliberate: silently calling {@code toString()} on a
     * {@link PlanColumnRef} produces a literal {@code "FieldRef(name)"} string
     * that compiles to a syntactically-legal but semantically-wrong SQL — the
     * resulting data corruption is not detectable via SQL string inspection.
     * Callers that want F5 must use the heterogeneous-list path
     * ({@link #normalizeColumns}) and downstream consumers must handle
     * {@link PlanExpression} objects natively (the post-G10 {@code BaseModelPlan} /
     * {@code DerivedQueryPlan} string-or-PlanExpression columns model).</p>
     *
     * @return new {@code List<String>}; null entries skipped (legacy behavior)
     * @throws IllegalArgumentException with {@code COLUMN_PLAN_TYPE_INVALID} prefix
     *         when an F5 entry or chained plan-expression object is encountered
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
            if (normalized instanceof String s) {
                result.add(s);
                continue;
            }
            // Anything that survived normalize() and is not a String must be a
            // PlanExpression (PlanColumnRef from chained API, or F5 compound).
            // Legacy string-only consumers cannot carry these — fail-loud.
            throw new IllegalArgumentException(
                "COLUMN_PLAN_TYPE_INVALID: columns[" + i + "] is a plan-qualified "
                + "column reference (" + normalized.getClass().getSimpleName()
                + ") which the legacy string-only request path cannot carry. "
                + "Either use the F4 string form '<AGG>(field) AS alias' / "
                + "'field AS alias', or route through a path that supports "
                + "List<Object> columns (e.g. dsl({...}) directly).");
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Internal: normalize one Map (F4 / F5)
    // ------------------------------------------------------------------

    private static Object normalizeMap(Map<?, ?> raw, int index) {
        // F5 detection via the F5_PLAN_KEY sentinel. The dispatch fans out
        // into different keysets and different return shapes (string for F4,
        // PlanExpression compound for F5).
        boolean isF5 = raw.containsKey(F5_PLAN_KEY);
        Set<String> allowedKeys = isF5 ? ALLOWED_F5_KEYS : ALLOWED_F4_KEYS;

        // Validate keys
        for (Object key : raw.keySet()) {
            if (!(key instanceof String) || !allowedKeys.contains(key)) {
                throw new IllegalArgumentException(
                    "COLUMN_FIELD_INVALID_KEY: columns[" + index + "] contains unknown key "
                    + (key == null ? "null" : "'" + key + "'") + ". Allowed keys: "
                    + allowedKeys);
            }
        }

        // field — required (both F4 and F5)
        Object fieldObj = raw.get("field");
        if (!(fieldObj instanceof String) || ((String) fieldObj).isBlank()) {
            throw new IllegalArgumentException(
                "COLUMN_FIELD_REQUIRED: columns[" + index + "] missing required 'field' "
                + "(must be a non-empty string, got " + (fieldObj == null ? "null" : fieldObj.getClass().getSimpleName()) + ")");
        }
        String field = ((String) fieldObj).trim();

        // as — optional (both F4 and F5)
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

        // agg — optional (both F4 and F5)
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

        if (isF5) {
            // F5: validate plan reference type, then build the
            // PlanColumnRef/AggregateColumn/ProjectedColumn compound. Plan
            // lineage visibility (spec §5.1) is checked at plan build time,
            // not here — normalize does not have outer-plan context.
            Object planObj = raw.get(F5_PLAN_KEY);
            if (!(planObj instanceof QueryPlan plan)) {
                throw new IllegalArgumentException(
                    "COLUMN_PLAN_TYPE_INVALID: columns[" + index + "] 'plan' must be a "
                    + "QueryPlan reference (e.g. a `dsl({...})` handle), got "
                    + (planObj == null ? "null" : planObj.getClass().getSimpleName()));
            }
            return buildPlanExpression(plan, field, agg, alias);
        }

        // F4: build the canonical string form
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

    /**
     * Build the F5 chained-API-style compound from already-validated parts.
     * Mirrors the chained API output:
     * <ul>
     *   <li>{@code plan, field} → {@link PlanColumnRef}</li>
     *   <li>{@code plan, field, agg} → {@link AggregateColumn} wrapping {@link PlanColumnRef}</li>
     *   <li>{@code plan, field, as} → {@link ProjectedColumn} wrapping {@link PlanColumnRef}</li>
     *   <li>{@code plan, field, agg, as} → {@link ProjectedColumn} wrapping {@link AggregateColumn}</li>
     * </ul>
     *
     * <p>This shape mirrors {@code sales.amount.sum().as("total")} so the
     * downstream {@code ComposePlanner.compilePlanColumnRef} (G10 PR3) emits
     * plan-aware SQL transparently.</p>
     *
     * @param plan  the plan reference (already validated as non-null QueryPlan)
     * @param field the field name (already trimmed and non-blank)
     * @param agg   lowercase aggregation token, or {@code null}
     * @param alias output alias, or {@code null}
     * @return a {@link PlanExpression} — exact runtime type depends on
     *         which optionals are present
     */
    private static PlanExpression buildPlanExpression(
            QueryPlan plan, String field, String agg, String alias) {
        PlanExpression node = new PlanColumnRef(plan, field);
        if (agg != null) {
            // PlanColumnRef → AggregateColumn (uppercase function token; SQL
            // engine handles COUNT_DISTINCT → COUNT(DISTINCT ...) lowering).
            node = new AggregateColumn((PlanColumnRef) node, agg.toUpperCase(Locale.ROOT));
        }
        if (alias != null) {
            // Wrap in ProjectedColumn — accepts any PlanExpression.
            node = new ProjectedColumn(node, alias, null);
        }
        return node;
    }
}
