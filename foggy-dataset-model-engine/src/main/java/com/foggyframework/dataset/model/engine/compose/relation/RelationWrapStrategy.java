package com.foggyframework.dataset.model.engine.compose.relation;

import java.util.Set;

/**
 * Closed string constants for relation wrapping strategy.
 *
 * <p>Determines how a {@link CompiledRelation}'s SQL is rendered
 * when consumed by an outer plan.</p>
 *
 * @since 8.5.0.beta (S7a)
 */
public final class RelationWrapStrategy {

    private RelationWrapStrategy() { /* utility */ }

    /** Render as inline subquery: {@code FROM (...) AS rel_N}. */
    public static final String INLINE_SUBQUERY = "inline_subquery";

    /** Hoist inner CTE items to the top-level WITH clause. */
    public static final String HOISTED_CTE = "hoisted_cte";

    /** Render the relation itself as a native top-level CTE item. */
    public static final String NATIVE_CTE = "native_cte";

    /** Cannot wrap this relation for the target dialect; must fail-closed. */
    public static final String FAIL_CLOSED = "fail_closed";

    /** All valid strategy values. */
    public static final Set<String> ALL = Set.of(
            INLINE_SUBQUERY, HOISTED_CTE, NATIVE_CTE, FAIL_CLOSED);

    public static boolean isValid(String strategy) {
        return strategy != null && ALL.contains(strategy);
    }
}
