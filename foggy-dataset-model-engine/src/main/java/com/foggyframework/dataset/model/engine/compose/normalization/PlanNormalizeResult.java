package com.foggyframework.dataset.model.engine.compose.normalization;

import com.foggyframework.dataset.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.model.plugins.pipeline.LoopTraceEntry;

import java.util.List;
import java.util.Objects;

/**
 * Result of compose plan normalization.
 */
public final class PlanNormalizeResult {

    private final QueryPlan originalPlan;
    private final QueryPlan normalizedPlan;
    private final boolean changed;
    private final int loopCount;
    private final String stopReason;
    private final List<LoopTraceEntry> loopTrace;

    PlanNormalizeResult(QueryPlan originalPlan,
                        QueryPlan normalizedPlan,
                        boolean changed,
                        int loopCount,
                        String stopReason,
                        List<LoopTraceEntry> loopTrace) {
        this.originalPlan = Objects.requireNonNull(originalPlan, "originalPlan");
        this.normalizedPlan = Objects.requireNonNull(normalizedPlan, "normalizedPlan");
        this.changed = changed;
        this.loopCount = loopCount;
        this.stopReason = stopReason;
        this.loopTrace = loopTrace == null ? List.of() : List.copyOf(loopTrace);
    }

    public QueryPlan originalPlan() {
        return originalPlan;
    }

    public QueryPlan normalizedPlan() {
        return normalizedPlan;
    }

    public boolean changed() {
        return changed;
    }

    public int loopCount() {
        return loopCount;
    }

    public String stopReason() {
        return stopReason;
    }

    public List<LoopTraceEntry> loopTrace() {
        return loopTrace;
    }
}
