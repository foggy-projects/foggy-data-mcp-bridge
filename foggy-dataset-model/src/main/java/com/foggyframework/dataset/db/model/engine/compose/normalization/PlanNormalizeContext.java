package com.foggyframework.dataset.db.model.engine.compose.normalization;

import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.plugins.pipeline.LoopDecision;
import com.foggyframework.dataset.db.model.plugins.pipeline.LoopTraceEntry;
import com.foggyframework.dataset.db.model.plugins.pipeline.LoopablePipelineContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loop-aware context for compose plan normalization.
 */
public final class PlanNormalizeContext implements LoopablePipelineContext {

    private final QueryPlan originalPlan;
    private QueryPlan plan;
    private final int maxLoopCount;
    private int loopIndex;
    private boolean loopStopRequested;
    private String loopStopReason;
    private boolean loopChanged;
    private final List<LoopTraceEntry> loopTrace = new ArrayList<>();

    PlanNormalizeContext(QueryPlan plan, PlanNormalizeOptions options) {
        this.originalPlan = Objects.requireNonNull(plan, "plan");
        this.plan = plan;
        PlanNormalizeOptions effectiveOptions =
                options == null ? PlanNormalizeOptions.defaults() : options;
        this.maxLoopCount = effectiveOptions.maxLoopCount();
    }

    public QueryPlan getOriginalPlan() {
        return originalPlan;
    }

    public QueryPlan getPlan() {
        return plan;
    }

    public void setPlan(QueryPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    boolean isPlanChangedFromOriginal() {
        return plan != originalPlan && !plan.equals(originalPlan);
    }

    @Override
    public int getLoopIndex() {
        return loopIndex;
    }

    @Override
    public void setLoopIndex(int loopIndex) {
        this.loopIndex = loopIndex;
    }

    @Override
    public int getMaxLoopCount() {
        return maxLoopCount;
    }

    @Override
    public boolean isLoopStopRequested() {
        return loopStopRequested;
    }

    @Override
    public String getLoopStopReason() {
        return loopStopReason;
    }

    @Override
    public void requestLoopStop(String reason) {
        this.loopStopRequested = true;
        this.loopStopReason = reason;
    }

    @Override
    public void clearLoopStop() {
        this.loopStopRequested = false;
        this.loopStopReason = null;
    }

    @Override
    public boolean isLoopChanged() {
        return loopChanged;
    }

    @Override
    public void markLoopChanged() {
        this.loopChanged = true;
    }

    @Override
    public void clearLoopChanged() {
        this.loopChanged = false;
    }

    @Override
    public void addLoopTrace(String stepName, LoopDecision decision) {
        if (decision == null) {
            return;
        }
        loopTrace.add(new LoopTraceEntry(
                loopIndex,
                stepName,
                decision.getAction(),
                decision.isChanged(),
                decision.getReason()));
    }

    @Override
    public List<LoopTraceEntry> getLoopTrace() {
        return List.copyOf(loopTrace);
    }
}
