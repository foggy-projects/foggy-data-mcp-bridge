package com.foggyframework.dataset.model.engine.compose.normalization;

import com.foggyframework.dataset.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.model.plugins.pipeline.LoopDecision;

import java.util.List;
import java.util.Objects;

/**
 * Bounded convergence pipeline for immutable compose plan normalization.
 */
public final class PlanNormalizePipeline {

    private final List<PlanNormalizeRule> rules;

    public PlanNormalizePipeline(List<PlanNormalizeRule> rules) {
        this.rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public static PlanNormalizePipeline defaults() {
        return new PlanNormalizePipeline(List.of(new RemoveEmptyDerivedQueryPlanRule()));
    }

    public PlanNormalizeResult normalize(QueryPlan plan) {
        return normalize(plan, PlanNormalizeOptions.defaults());
    }

    public PlanNormalizeResult normalize(QueryPlan plan, PlanNormalizeOptions options) {
        Objects.requireNonNull(plan, "plan");
        PlanNormalizeContext ctx = new PlanNormalizeContext(plan, options);
        if (ctx.getMaxLoopCount() <= 0 || rules.isEmpty()) {
            return result(ctx, 0);
        }

        ctx.clearLoopStop();
        for (int i = 0; i < ctx.getMaxLoopCount(); i++) {
            ctx.setLoopIndex(i);
            ctx.clearLoopChanged();

            for (PlanNormalizeRule rule : rules) {
                LoopDecision decision = rule.apply(ctx);
                if (decision == null) {
                    decision = LoopDecision.unchanged("null decision");
                }
                ctx.addLoopTrace(rule.name(), decision);

                if (decision.isChanged()) {
                    ctx.markLoopChanged();
                }
                if (decision.isFail()) {
                    throw new IllegalStateException("Plan normalization failed at rule "
                            + rule.name() + ": " + decision.getReason());
                }
                if (decision.isStop()) {
                    ctx.requestLoopStop(decision.getReason());
                    return result(ctx, i + 1);
                }
            }

            if (!ctx.isLoopChanged()) {
                return result(ctx, i + 1);
            }
        }

        throw new IllegalStateException("Plan normalization exceeded maxLoopCount="
                + ctx.getMaxLoopCount());
    }

    private PlanNormalizeResult result(PlanNormalizeContext ctx, int loopCount) {
        return new PlanNormalizeResult(
                ctx.getOriginalPlan(),
                ctx.getPlan(),
                ctx.isPlanChangedFromOriginal(),
                loopCount,
                ctx.getLoopStopReason(),
                ctx.getLoopTrace());
    }
}
