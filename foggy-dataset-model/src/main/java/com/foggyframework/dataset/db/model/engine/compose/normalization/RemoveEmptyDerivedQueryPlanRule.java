package com.foggyframework.dataset.db.model.engine.compose.normalization;

import com.foggyframework.dataset.db.model.engine.compose.plan.DerivedQueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.UnionPlan;
import com.foggyframework.dataset.db.model.plugins.pipeline.LoopDecision;

/**
 * Removes semantically empty {@link DerivedQueryPlan} wrappers.
 */
public final class RemoveEmptyDerivedQueryPlanRule implements PlanNormalizeRule {

    @Override
    public LoopDecision apply(PlanNormalizeContext ctx) {
        QueryPlan normalized = normalize(ctx.getPlan());
        if (normalized == ctx.getPlan() || normalized.equals(ctx.getPlan())) {
            return LoopDecision.unchanged("no empty derived wrapper");
        }
        ctx.setPlan(normalized);
        return LoopDecision.changed("removed empty derived wrapper");
    }

    private QueryPlan normalize(QueryPlan plan) {
        if (plan instanceof DerivedQueryPlan derived) {
            if (isEmptyWrapper(derived)) {
                return normalize(derived.source());
            }
            return derived;
        }
        if (plan instanceof JoinPlan join) {
            QueryPlan left = normalize(join.left());
            QueryPlan right = normalize(join.right());
            if (left == join.left() && right == join.right()) {
                return join;
            }
            return JoinPlan.builder()
                    .left(left)
                    .right(right)
                    .type(join.type())
                    .on(join.on())
                    .build();
        }
        if (plan instanceof UnionPlan union) {
            QueryPlan left = normalize(union.left());
            QueryPlan right = normalize(union.right());
            if (left == union.left() && right == union.right()) {
                return union;
            }
            return UnionPlan.builder()
                    .left(left)
                    .right(right)
                    .all(union.all())
                    .build();
        }
        return plan;
    }

    private boolean isEmptyWrapper(DerivedQueryPlan plan) {
        return plan.columns().isEmpty()
                && plan.slice().isEmpty()
                && plan.groupBy().isEmpty()
                && plan.orderBy().isEmpty()
                && plan.limit() == null
                && plan.start() == null
                && !plan.distinct();
    }
}
