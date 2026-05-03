package com.foggyframework.dataset.db.model.engine.pivot.phase;

import com.foggyframework.dataset.db.model.engine.pivot.algo.ParentShareCalculator;

/**
 * Phase 2.8: ParentShare 父级占比计算。
 *
 * <p>仅在 pivot.parentShareMetrics 非空时执行。</p>
 */
public class ParentShareProcessor implements PivotPhase2Processor {

    @Override
    public void process(PivotPhase2Context ctx) {
        if (!ctx.getPivot().getParentShareMetrics().isEmpty()) {
            ctx.getLogger().debug("[Pivot] Phase 2.8: ParentShare calculation, {} metrics",
                    ctx.getPivot().getParentShareMetrics().size());
            ParentShareCalculator.apply(ctx.getResultSet(), ctx.getPivot(),
                    ctx.getRowFields(), ctx.getColFields());
        }
    }
}
