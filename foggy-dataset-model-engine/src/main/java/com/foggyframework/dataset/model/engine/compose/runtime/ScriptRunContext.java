package com.foggyframework.dataset.model.engine.compose.runtime;

import java.time.Instant;
import java.util.UUID;

/**
 * Mutable per-run lifecycle tracker.
 *
 * <p>Each {@code ScriptRunContext} tracks a single FSScript run's
 * current state and optional suspension.  State transitions are
 * validated via {@link #transition(ScriptRunState)}.</p>
 *
 * <p>Mirrors Python {@code ScriptRunContext} dataclass.</p>
 *
 * @since 8.5.0
 */
public final class ScriptRunContext {

    private final String runId;
    private final Instant createdAt;
    private volatile ScriptRunState state;
    private volatile SuspensionResult suspension;

    public ScriptRunContext() {
        this("sr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
    }

    public ScriptRunContext(String runId) {
        this.runId = runId;
        this.state = ScriptRunState.RUNNING;
        this.createdAt = Instant.now();
    }

    public String getRunId() { return runId; }
    public ScriptRunState getState() { return state; }
    public Instant getCreatedAt() { return createdAt; }
    public SuspensionResult getSuspension() { return suspension; }
    public void setSuspension(SuspensionResult suspension) { this.suspension = suspension; }

    /**
     * Transition to the new state if legal.
     *
     * @param newState the target state
     * @throws ScriptSuspendException.StateInvalid if the transition is illegal
     */
    public void transition(ScriptRunState newState) {
        if (!state.canTransitionTo(newState)) {
            throw new ScriptSuspendException.StateInvalid(
                    "cannot transition from " + state + " to " + newState);
        }
        this.state = newState;
    }

    /** True when the run is in a terminal state. */
    public boolean isTerminal() {
        return state.isTerminal();
    }

    /** Generate a prefixed suspend identifier. */
    public static String generateSuspendId() {
        return "sp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
