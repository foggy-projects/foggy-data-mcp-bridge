package com.foggyframework.dataset.model.engine.compose.relation;

import java.util.Set;

/**
 * Closed string constants for relation permission state.
 *
 * <p>Tracks whether the relation's output schema has been through
 * permission validation. Prevents accidental consumption of an
 * unvalidated relation as if it were authorized.</p>
 *
 * @since 8.5.0.beta (S7a)
 */
public final class RelationPermissionState {

    private RelationPermissionState() { /* utility */ }

    /** Permission state is not yet determined. */
    public static final String UNKNOWN = "unknown";

    /** Relation was built from pre-authorized sources but not yet fully validated. */
    public static final String PRE_AUTHORIZED = "pre_authorized";

    /** Relation's output schema has been through full permission validation. */
    public static final String AUTHORIZED = "authorized";

    /** All valid permission state values. */
    public static final Set<String> ALL = Set.of(UNKNOWN, PRE_AUTHORIZED, AUTHORIZED);

    public static boolean isValid(String state) {
        return state != null && ALL.contains(state);
    }
}
