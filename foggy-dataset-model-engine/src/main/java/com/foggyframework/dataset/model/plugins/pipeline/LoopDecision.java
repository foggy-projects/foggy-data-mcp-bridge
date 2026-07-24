package com.foggyframework.dataset.model.plugins.pipeline;

import java.util.Objects;

/**
 * Decision returned by an optional bounded pipeline loop hook.
 */
public final class LoopDecision {

    public enum Action {
        CONTINUE,
        STOP,
        FAIL
    }

    private static final LoopDecision UNCHANGED = new LoopDecision(Action.CONTINUE, false, "unchanged");

    private final Action action;
    private final boolean changed;
    private final String reason;

    private LoopDecision(Action action, boolean changed, String reason) {
        this.action = Objects.requireNonNull(action, "action");
        this.changed = changed;
        this.reason = reason;
    }

    public static LoopDecision changed(String reason) {
        return new LoopDecision(Action.CONTINUE, true, reason);
    }

    public static LoopDecision unchanged() {
        return UNCHANGED;
    }

    public static LoopDecision unchanged(String reason) {
        return new LoopDecision(Action.CONTINUE, false, reason);
    }

    public static LoopDecision stop(String reason) {
        return new LoopDecision(Action.STOP, false, reason);
    }

    public static LoopDecision fail(String reason) {
        return new LoopDecision(Action.FAIL, false, reason);
    }

    public Action getAction() {
        return action;
    }

    public boolean isChanged() {
        return changed;
    }

    public String getReason() {
        return reason;
    }

    public boolean isStop() {
        return action == Action.STOP;
    }

    public boolean isFail() {
        return action == Action.FAIL;
    }
}
