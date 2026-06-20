package com.foggyframework.dataset.db.model.engine.compose.normalization;

import com.foggyframework.dataset.db.model.plugins.pipeline.LoopDecision;

/**
 * A single convergence-oriented compose plan normalization rule.
 */
public interface PlanNormalizeRule {

    default String name() {
        return getClass().getSimpleName();
    }

    LoopDecision apply(PlanNormalizeContext ctx);
}
