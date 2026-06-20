package com.foggyframework.dataset.db.model.plugins.pipeline;

import java.util.Objects;

/**
 * Lightweight diagnostic record for bounded loop-hook execution.
 */
public final class LoopTraceEntry {

    private final int iteration;
    private final String stepName;
    private final LoopDecision.Action action;
    private final boolean changed;
    private final String reason;

    public LoopTraceEntry(int iteration,
                          String stepName,
                          LoopDecision.Action action,
                          boolean changed,
                          String reason) {
        this.iteration = iteration;
        this.stepName = Objects.requireNonNull(stepName, "stepName");
        this.action = Objects.requireNonNull(action, "action");
        this.changed = changed;
        this.reason = reason;
    }

    public int getIteration() {
        return iteration;
    }

    public String getStepName() {
        return stepName;
    }

    public LoopDecision.Action getAction() {
        return action;
    }

    public boolean isChanged() {
        return changed;
    }

    public String getReason() {
        return reason;
    }
}
