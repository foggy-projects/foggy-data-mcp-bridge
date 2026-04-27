package com.foggyframework.dataset.db.model.engine.compose.schema;

import java.util.Set;

/**
 * Frozen error codes for Compose Query schema-derivation failures.
 *
 * <p>These are <b>structural / correctness</b> errors (missing field
 * references, union column-count mismatch, join-on resolution failures)
 * that occur during plan-build / schema-derive phases. They are
 * deliberately NOT grouped under {@code compose-sandbox-violation} —
 * sandbox codes are for <i>security</i> enforcement (Layer A/B/C
 * whitelists); schema codes are for <i>correctness</i> (did the user
 * write a structurally-valid plan?).</p>
 *
 * <p><b>Cross-language parity contract.</b> Every string constant here
 * must match the Python {@code foggy.dataset_model.engine.compose.schema.error_codes}
 * module byte-for-byte. A mirror test on the Python side asserts the same
 * seven codes exist; drift is a cross-repo regression.</p>
 *
 * <p>Extensions allowed (new codes may be appended). Renames and removals
 * are SemVer breakage and require an SPI-version bump.</p>
 *
 * @since 8.2.0.beta
 */
public final class ComposeSchemaErrorCodes {

    private ComposeSchemaErrorCodes() { /* utility */ }

    /** Top-level error namespace. */
    public static final String NAMESPACE = "compose-schema-error";

    // ------------------------------------------------------------------
    // Seven frozen codes (M4 contract)
    // ------------------------------------------------------------------

    /** Derived query references a column that is NOT in source.output_schema. */
    public static final String DERIVED_QUERY_UNKNOWN_FIELD =
            NAMESPACE + "/derived-query/unknown-field";

    /** Column spec whose expression references the empty alias slot
     *  ({@code ... AS}) or similar malformed shape. */
    public static final String COLUMN_SPEC_MALFORMED =
            NAMESPACE + "/column-spec/malformed";

    /** Output schema after derivation contains duplicate output names. */
    public static final String DUPLICATE_OUTPUT_COLUMN =
            NAMESPACE + "/duplicate-output-column";

    /** {@code UnionPlan} two sides have different column counts. */
    public static final String UNION_COLUMN_COUNT_MISMATCH =
            NAMESPACE + "/union/column-count-mismatch";

    /** {@code JoinPlan.on[*].left} does not resolve in left's output schema. */
    public static final String JOIN_ON_LEFT_UNKNOWN_FIELD =
            NAMESPACE + "/join/on-left-unknown-field";

    /** {@code JoinPlan.on[*].right} does not resolve in right's output schema. */
    public static final String JOIN_ON_RIGHT_UNKNOWN_FIELD =
            NAMESPACE + "/join/on-right-unknown-field";

    /** Join left.output + right.output share an output column name without
     *  explicit alias disambiguation.
     *
     *  <p><b>G10:</b> Only thrown when {@code foggy.compose.g10.enabled=false}
     *  (legacy behaviour). When G10 is enabled, the column is marked
     *  {@code isAmbiguous=true} and the conflict is detected at downstream
     *  reference resolution as {@link #JOIN_AMBIGUOUS_COLUMN}.</p> */
    public static final String JOIN_OUTPUT_COLUMN_CONFLICT =
            NAMESPACE + "/join/output-column-conflict";

    /**
     * <b>G10 PR2</b> · A lookup against {@link OutputSchema#get(String)} or
     * {@code requireUnique(name)} resolved a column name marked
     * {@code isAmbiguous=true} (multiple plans contribute the same name).
     *
     * <p>The error message lists every candidate column's plan provenance so
     * the caller can disambiguate via F5 plan-qualified column ref
     * ({@code {plan: <handle>, field: <name>}}).</p>
     */
    public static final String OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP =
            NAMESPACE + "/output-schema/ambiguous-lookup";

    /**
     * <b>G10 PR3</b> · Downstream reference (in derived/projected expression,
     * group-by, or order-by) targets a column that the upstream join marked
     * {@code isAmbiguous=true}, and the reference itself is not
     * plan-qualified (F5).
     *
     * <p>Reserved here so PR3 / PR4 producers can throw a stable code; not
     * yet emitted by PR2.</p>
     */
    public static final String JOIN_AMBIGUOUS_COLUMN =
            NAMESPACE + "/join/ambiguous-column";

    public static final Set<String> ALL_CODES = Set.of(
            DERIVED_QUERY_UNKNOWN_FIELD,
            COLUMN_SPEC_MALFORMED,
            DUPLICATE_OUTPUT_COLUMN,
            UNION_COLUMN_COUNT_MISMATCH,
            JOIN_ON_LEFT_UNKNOWN_FIELD,
            JOIN_ON_RIGHT_UNKNOWN_FIELD,
            JOIN_OUTPUT_COLUMN_CONFLICT,
            OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP,
            JOIN_AMBIGUOUS_COLUMN
    );

    // ------------------------------------------------------------------
    // Phase tags (kept compatible with the sandbox-error phase set so error
    // sinks can consume both error families uniformly)
    // ------------------------------------------------------------------

    public static final String PHASE_PLAN_BUILD = "plan-build";
    public static final String PHASE_SCHEMA_DERIVE = "schema-derive";

    public static final Set<String> VALID_PHASES = Set.of(
            PHASE_PLAN_BUILD,
            PHASE_SCHEMA_DERIVE
    );
}
