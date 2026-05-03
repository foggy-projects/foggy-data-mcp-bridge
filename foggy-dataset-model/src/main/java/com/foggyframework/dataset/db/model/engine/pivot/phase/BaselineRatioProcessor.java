package com.foggyframework.dataset.db.model.engine.pivot.phase;

import com.foggyframework.dataset.db.model.engine.pivot.algo.BaselineRatioCalculator;

/**
 * Phase 2.9: BaselineRatio 基准引用计算。
 *
 * <p>仅在 pivot.baselineRatioMetrics 非空时执行。</p>
 */
public class BaselineRatioProcessor implements PivotPhase2Processor {

    @Override
    public void process(PivotPhase2Context ctx) {
        if (!ctx.getPivot().getBaselineRatioMetrics().isEmpty()) {
            ctx.getLogger().debug("[Pivot] Phase 2.9: BaselineRatio calculation, {} metrics",
                    ctx.getPivot().getBaselineRatioMetrics().size());
            BaselineRatioCalculator.apply(ctx.getResultSet(), ctx.getPivot(),
                    ctx.getRowFields(), ctx.getColFields());
        }
    }
}
