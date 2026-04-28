package com.foggyframework.dataset.db.model.engine.compose.relation;

import java.util.Set;

/**
 * Closed string constants for column reference policy.
 *
 * <p>A column's reference policy is a <em>set</em> of capabilities
 * describing how the column may be consumed by an outer plan layer.</p>
 *
 * <p>String-backed (not enum) to avoid Java/Python drift in first POC.</p>
 *
 * @since 8.5.0.beta (S7a)
 */
public final class ReferencePolicy {

    private ReferencePolicy() { /* utility */ }

    /** Column value can be read / displayed. */
    public static final String READABLE = "readable";

    /** Column can appear in outer GROUP BY. */
    public static final String GROUPABLE = "groupable";

    /** Column can be wrapped in an outer aggregate function (SUM, AVG, etc.). */
    public static final String AGGREGATABLE = "aggregatable";

    /** Column can be referenced inside an outer OVER(...) window clause. */
    public static final String WINDOWABLE = "windowable";

    /** Column can appear in outer ORDER BY. */
    public static final String ORDERABLE = "orderable";

    /** All valid reference policy values. */
    public static final Set<String> ALL = Set.of(
            READABLE, GROUPABLE, AGGREGATABLE, WINDOWABLE, ORDERABLE);

    /** Convenience: dimension fields — readable, groupable, orderable. */
    public static final Set<String> DIMENSION_DEFAULT = Set.of(READABLE, GROUPABLE, ORDERABLE);

    /** Convenience: measure fields — readable, orderable. */
    public static final Set<String> MEASURE_DEFAULT = Set.of(READABLE, ORDERABLE);

    /** Convenience: timeWindow derived fields — readable, orderable. */
    public static final Set<String> TIME_WINDOW_DERIVED_DEFAULT = Set.of(READABLE, ORDERABLE);

    public static boolean isValid(String policy) {
        return policy != null && ALL.contains(policy);
    }
}
