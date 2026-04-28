package com.foggyframework.dataset.db.model.engine.compose.relation;

import java.util.Set;

/**
 * Closed string constants for column semantic kind.
 *
 * <p>Used by {@link com.foggyframework.dataset.db.model.engine.compose.schema.ColumnSpec}
 * S7a metadata to classify each output column's semantic role.</p>
 *
 * <p>String-backed (not enum) to avoid Java/Python drift in first POC.</p>
 *
 * @since 8.5.0.beta (S7a)
 */
public final class SemanticKind {

    private SemanticKind() { /* utility */ }

    /** A base dimension or attribute field from the source model. */
    public static final String BASE_FIELD = "base_field";

    /** An aggregated measure (SUM, COUNT, AVG, etc.). */
    public static final String AGGREGATE_MEASURE = "aggregate_measure";

    /** A column derived by timeWindow expansion (e.g. __prior, __diff, __ratio, __rolling_*, __ytd, __mtd). */
    public static final String TIME_WINDOW_DERIVED = "time_window_derived";

    /** A scalar calculated field (no aggregation, no window). */
    public static final String SCALAR_CALC = "scalar_calc";

    /** A window-function calculated field (OVER clause). */
    public static final String WINDOW_CALC = "window_calc";

    /** All valid semantic kind values. */
    public static final Set<String> ALL = Set.of(
            BASE_FIELD, AGGREGATE_MEASURE, TIME_WINDOW_DERIVED,
            SCALAR_CALC, WINDOW_CALC);

    public static boolean isValid(String kind) {
        return kind != null && ALL.contains(kind);
    }
}
