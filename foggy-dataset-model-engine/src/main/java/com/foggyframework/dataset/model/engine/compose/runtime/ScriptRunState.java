package com.foggyframework.dataset.model.engine.compose.runtime;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle states for a single FSScript run.
 *
 * <p>Terminal states: {@code REJECTED}, {@code TIMED_OUT},
 * {@code ABORTED}, {@code COMPLETED}.  Once a run enters any terminal
 * state no further transition is allowed.</p>
 *
 * <p>Mirrors Python {@code ScriptRunState}.</p>
 *
 * @since 8.5.0
 */
public enum ScriptRunState {

    RUNNING,
    SUSPENDED,
    REJECTED,
    TIMED_OUT,
    ABORTED,
    COMPLETED;

    /** Legal transitions (fail-closed — anything not listed is illegal). */
    private static final Map<ScriptRunState, Set<ScriptRunState>> VALID_TRANSITIONS = Map.of(
            RUNNING, EnumSet.of(SUSPENDED, COMPLETED, ABORTED),
            SUSPENDED, EnumSet.of(RUNNING, REJECTED, TIMED_OUT, ABORTED),
            REJECTED, EnumSet.noneOf(ScriptRunState.class),
            TIMED_OUT, EnumSet.noneOf(ScriptRunState.class),
            ABORTED, EnumSet.noneOf(ScriptRunState.class),
            COMPLETED, EnumSet.noneOf(ScriptRunState.class)
    );

    /** Terminal states — no outgoing edges. */
    public static final Set<ScriptRunState> TERMINAL_STATES = EnumSet.of(
            REJECTED, TIMED_OUT, ABORTED, COMPLETED
    );

    /**
     * Check whether transitioning from this state to {@code target} is legal.
     *
     * @param target the desired next state
     * @return true if the transition is valid
     */
    public boolean canTransitionTo(ScriptRunState target) {
        Set<ScriptRunState> allowed = VALID_TRANSITIONS.getOrDefault(this, Set.of());
        return allowed.contains(target);
    }

    /** True when no further transition is allowed. */
    public boolean isTerminal() {
        return TERMINAL_STATES.contains(this);
    }
}
